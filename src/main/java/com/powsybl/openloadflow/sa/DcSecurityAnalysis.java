/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.security.detectors.LoadingLimitType;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis {

    private class DcSecurityAnalysisContext {

        List<SensitivityFactor> sensitivityFactors;
        Map<String, BranchResult> preContingencyAllBranchResults;
        Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolationsMap;
        SecurityAnalysisParameters parameters;
        List<Contingency> contingencies;
        DefaultLimitViolationDetector detector;
        double dcPowerFactor;

        public DcSecurityAnalysisContext(SecurityAnalysisParameters saParameters,
                                         List<Contingency> contingencyList,
                                         DefaultLimitViolationDetector violationDetector,
                                         double dcPowerFactor) {
            this.parameters = saParameters;
            this.contingencies = contingencyList;
            this.sensitivityFactors = new ArrayList<>();
            this.preContingencyAllBranchResults = new HashMap<>();
            this.preContingencyLimitViolationsMap = new HashMap<>();
            this.detector = violationDetector;
            this.dcPowerFactor = dcPowerFactor;
        }

        List<SensitivityFactor> getFactors() {
            return sensitivityFactors;
        }

        Map<String, BranchResult> getPreContingencyAllBranchResults() {
            return preContingencyAllBranchResults;
        }

        Map<Pair<String, Branch.Side>, LimitViolation> getPreContingencyLimitViolationsMap() {
            return preContingencyLimitViolationsMap;
        }

        List<Contingency> getContingencies() {
            return contingencies;
        }

        SecurityAnalysisParameters getParameters() {
            return parameters;
        }

        DefaultLimitViolationDetector getDetector() {
            return detector;
        }

        double getDcPowerFactor() {
            return dcPowerFactor;
        }
    }

    protected DcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, Reporter reporter) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reporter);
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions) {

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        OpenSensitivityAnalysisProvider sensitivityAnalysisProvider = new OpenSensitivityAnalysisProvider(matrixFactory);

        List<SensitivityVariableSet> variableSets = Collections.emptyList();
        SensitivityAnalysisParameters sensitivityAnalysisParameters = new SensitivityAnalysisParameters();
        sensitivityAnalysisParameters.setLoadFlowParameters(securityAnalysisParameters.getLoadFlowParameters());

        ContingencyContext contingencyContext = new ContingencyContext(null, ContingencyContextType.ALL);
        String variableId = network.getLoads().iterator().next().getId();

        DefaultLimitViolationDetector detector = new DefaultLimitViolationDetector(1.0f, EnumSet.allOf(LoadingLimitType.class));

        // CosPhi for DC power to current conversion
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        DcSecurityAnalysisContext context = new DcSecurityAnalysisContext(securityAnalysisParameters, contingencies, detector, parametersExt.getDcPowerFactor());
        for (Branch<?> b : network.getBranches()) {
            context.getFactors().add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult result = new SensitivityAnalysis.Runner(sensitivityAnalysisProvider)
                .run(network, workingVariantId, context.getFactors(), context.getContingencies(), variableSets, sensitivityAnalysisParameters, computationManager, Reporter.NO_OP);

        PreContingencyResult preContingencyResult = createPreContingencyResults(context, result);

        List<PostContingencyResult> postContingencyResults = createPostContingencyResults(context, result);

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults, Collections.emptyList()));
    }

    private PreContingencyResult createPreContingencyResults(DcSecurityAnalysisContext context, SensitivityAnalysisResult result) {

        List<BranchResult> branchResults = new ArrayList<>();
        for (SensitivityValue sensValue : result.getValues(null)) {
            SensitivityFactor factor = context.getFactors().get(sensValue.getFactorIndex());
            String branchId = factor.getFunctionId();
            Branch<?> branch = network.getBranch(branchId);
            double i1 = currentActivePower(Math.abs(sensValue.getFunctionReference()), branch.getTerminal1().getVoltageLevel().getNominalV(), context.getDcPowerFactor());
            double i2 = currentActivePower(Math.abs(sensValue.getFunctionReference()), branch.getTerminal2().getVoltageLevel().getNominalV(), context.getDcPowerFactor());
            BranchResult branchResult = new BranchResult(branchId, sensValue.getFunctionReference(), Double.NaN, i1, -sensValue.getFunctionReference(), Double.NaN, i2, Double.NaN);
            context.getPreContingencyAllBranchResults().put(branchId, branchResult);
            context.getDetector().checkActivePower(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()), violation -> context.getPreContingencyLimitViolationsMap().put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            context.getDetector().checkCurrent(branch, Branch.Side.ONE, i1, violation -> context.getPreContingencyLimitViolationsMap().put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            context.getDetector().checkCurrent(branch, Branch.Side.TWO, i2, violation -> context.getPreContingencyLimitViolationsMap().put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            if (isBranchMonitored(branchId, null)) {
                branchResults.add(branchResult);
            }
        }

        LimitViolationsResult limitViolations = new LimitViolationsResult(new ArrayList<>(context.getPreContingencyLimitViolationsMap().values()));
        return new PreContingencyResult(LoadFlowResult.ComponentResult.Status.CONVERGED, limitViolations, branchResults, Collections.emptyList(), Collections.emptyList());
    }

    private List<PostContingencyResult> createPostContingencyResults(DcSecurityAnalysisContext context, SensitivityAnalysisResult res) {

        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : context.getContingencies()) {
            Map<String, BranchResult> postContingencyBranchResults = new HashMap<>();
            List<SensitivityValue> values = res.getValues(contingency.getId());
            Map<Pair<String, Branch.Side>, LimitViolation> violations = new HashMap<>();
            double branchInContingencyP1 = Double.NaN;
            if (contingency.getElements().size() == 1 && contingency.getElements().get(0).getType() == ContingencyElementType.BRANCH) {
                branchInContingencyP1 = context.getPreContingencyAllBranchResults().get(contingency.getElements().get(0).getId()).getP1();
            }
            for (SensitivityValue v : values) {
                SensitivityFactor factor = context.getFactors().get(v.getFactorIndex());
                String branchId = factor.getFunctionId();
                Branch<?> branch = network.getBranch(branchId);
                double i1 = currentActivePower(Math.abs(v.getFunctionReference()), branch.getTerminal1().getVoltageLevel().getNominalV(), context.getDcPowerFactor());
                double i2 = currentActivePower(Math.abs(v.getFunctionReference()), branch.getTerminal2().getVoltageLevel().getNominalV(), context.getDcPowerFactor());
                if (isBranchMonitored(branchId, contingency)) {
                    BranchResult preContingencyBranchResult = context.getPreContingencyAllBranchResults().get(branchId);
                    double flowTransfer = Double.isNaN(branchInContingencyP1) ? Double.NaN : (v.getFunctionReference() - preContingencyBranchResult.getP1()) / branchInContingencyP1;
                    postContingencyBranchResults.put(branchId, new BranchResult(branchId, v.getFunctionReference(), Double.NaN, i1,
                            -v.getFunctionReference(), Double.NaN, i2, flowTransfer));
                }
                context.getDetector().checkActivePower(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()), violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
                context.getDetector().checkCurrent(branch, Branch.Side.ONE, i1, violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
                context.getDetector().checkCurrent(branch, Branch.Side.TWO, i2, violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            }
            context.getPreContingencyLimitViolationsMap().forEach((subjectSideId, preContingencyViolation) -> {
                LimitViolation postContingencyViolation = violations.get(subjectSideId);
                if (LimitViolationManager.violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation, context.getParameters().getIncreasedViolationsParameters())) {
                    violations.remove(subjectSideId);
                }
            });

            postContingencyResults.add(new PostContingencyResult(contingency, PostContingencyComputationStatus.CONVERGED, new ArrayList<>(violations.values()),
                    new ArrayList<>(postContingencyBranchResults.values()), Collections.emptyList(), Collections.emptyList()));
        }
        return postContingencyResults;
    }

    private boolean isBranchMonitored(String branchId, Contingency contingency) {
        boolean allMonitored = monitorIndex.getAllStateMonitor().getBranchIds().contains(branchId);
        boolean specificMonitored = false;
        StateMonitor specificMonitor = null;
        if (contingency != null) {
            specificMonitor = monitorIndex.getSpecificStateMonitors().get(contingency.getId());
        }
        if (specificMonitor != null) {
            specificMonitored = specificMonitor.getBranchIds().contains(branchId);
        }
        return allMonitored || specificMonitored;
    }

    private static double currentActivePower(double activePower, double voltage, double cosPhi) {
        return 1000 * activePower / (Math.sqrt(3) * cosPhi * voltage);
    }
}
