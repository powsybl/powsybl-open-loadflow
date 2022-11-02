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
        DcSecurityAnalysisContext ctx = new DcSecurityAnalysisContext(securityAnalysisParameters, contingencies, detector, parametersExt.getDcPowerFactor());
        for (Branch<?> b : network.getBranches()) {
            ctx.getFactors().add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult res = new SensitivityAnalysis.Runner(sensitivityAnalysisProvider)
                .run(network, workingVariantId, ctx.getFactors(), ctx.getContingencies(), variableSets, sensitivityAnalysisParameters, computationManager, Reporter.NO_OP);

        PreContingencyResult preContingencyResult = buildPreContingencyResults(ctx, res);

        List<PostContingencyResult> postContingencyResults = buildPostContingencyResults(ctx, res);

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults, Collections.emptyList()));
    }

    public static double currentActivePower(double activePower, double voltage, double cosPhi) {
        return 1000 * activePower / (Math.sqrt(3) * cosPhi * voltage);
    }

    public PreContingencyResult buildPreContingencyResults(DcSecurityAnalysisContext ctx, SensitivityAnalysisResult res) {
        for (SensitivityValue sensValue : res.getValues(null)) {
            SensitivityFactor factor = ctx.getFactors().get(sensValue.getFactorIndex());
            String branchId = factor.getFunctionId();
            Branch<?> branch = network.getBranch(branchId);
            double i1 = currentActivePower(Math.abs(sensValue.getFunctionReference()), branch.getTerminal1().getVoltageLevel().getNominalV(), ctx.getDcPowerFactor());
            double i2 = currentActivePower(Math.abs(sensValue.getFunctionReference()), branch.getTerminal2().getVoltageLevel().getNominalV(), ctx.getDcPowerFactor());
            ctx.getPreContingencyBranchResults().put(branchId, new BranchResult(branchId, sensValue.getFunctionReference(), Double.NaN, i1, -sensValue.getFunctionReference(), Double.NaN, i2, Double.NaN));
            ctx.getDetector().checkActivePower(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()), violation -> ctx.getPreContingencyLimitViolationsMap().put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            ctx.getDetector().checkCurrent(branch, Branch.Side.ONE, i1, violation -> ctx.getPreContingencyLimitViolationsMap().put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            ctx.getDetector().checkCurrent(branch, Branch.Side.TWO, i2, violation -> ctx.getPreContingencyLimitViolationsMap().put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
        }

        LimitViolationsResult limitViolations = new LimitViolationsResult(new ArrayList<>(ctx.getPreContingencyLimitViolationsMap().values()));

        return new PreContingencyResult(LoadFlowResult.ComponentResult.Status.CONVERGED, limitViolations, new ArrayList<>(ctx.getPreContingencyBranchResults().values()), Collections.emptyList(), Collections.emptyList());
    }

    public List<PostContingencyResult> buildPostContingencyResults(DcSecurityAnalysisContext ctx, SensitivityAnalysisResult res) {
        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : ctx.getContingencies()) {
            Map<String, BranchResult> postContingencyBranchResults = new HashMap<>();
            List<SensitivityValue> values = res.getValues(contingency.getId());
            Map<Pair<String, Branch.Side>, LimitViolation> violations = new HashMap<>();
            double branchInContingencyP1 = Double.NaN;
            if (contingency.getElements().size() == 1 && contingency.getElements().get(0).getType() == ContingencyElementType.BRANCH) {
                branchInContingencyP1 = ctx.getPreContingencyBranchResults().get(contingency.getElements().get(0).getId()).getP1();
            }

            for (SensitivityValue v : values) {
                SensitivityFactor factor = ctx.getFactors().get(v.getFactorIndex());
                String branchId = factor.getFunctionId();
                Branch<?> branch = network.getBranch(branchId);
                double i1 = currentActivePower(Math.abs(v.getFunctionReference()), branch.getTerminal1().getVoltageLevel().getNominalV(), ctx.getDcPowerFactor());
                double i2 = currentActivePower(Math.abs(v.getFunctionReference()), branch.getTerminal2().getVoltageLevel().getNominalV(), ctx.getDcPowerFactor());

                if (isBranchMonitored(branchId, contingency)) {
                    BranchResult preContingencyBranchResult = ctx.getPreContingencyBranchResults().get(branchId);
                    double flowTransfer = Double.isNaN(branchInContingencyP1) ? Double.NaN : (v.getFunctionReference() - preContingencyBranchResult.getP1()) / branchInContingencyP1;
                    postContingencyBranchResults.put(branchId, new BranchResult(branchId, v.getFunctionReference(), Double.NaN, i1,
                            -v.getFunctionReference(), Double.NaN, i2, flowTransfer));
                }
                ctx.getDetector().checkActivePower(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()), violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
                ctx.getDetector().checkCurrent(branch, Branch.Side.ONE, i1, violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
                ctx.getDetector().checkCurrent(branch, Branch.Side.TWO, i2, violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            }
            ctx.getPreContingencyLimitViolationsMap().forEach((subjectSideId, preContingencyViolation) -> {
                LimitViolation postContingencyViolation = violations.get(subjectSideId);
                if (LimitViolationManager.violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation, ctx.getParameters().getIncreasedViolationsParameters())) {
                    violations.remove(subjectSideId);
                }
            });
            postContingencyResults.add(new PostContingencyResult(contingency, PostContingencyComputationStatus.CONVERGED, new ArrayList<>(violations.values()),
                    new ArrayList<>(postContingencyBranchResults.values()), Collections.emptyList(), Collections.emptyList()));
        }
        return postContingencyResults;
    }

    private boolean isBranchMonitored(String branchId, Contingency c) {
        boolean allMonitored = monitorIndex.getAllStateMonitor().getBranchIds().contains(branchId);
        boolean specificMonitored = false;
        StateMonitor specificMonitor = monitorIndex.getSpecificStateMonitors().get(c.getId());
        if (specificMonitor != null) {
            specificMonitored = specificMonitor.getBranchIds().contains(branchId);
        }
        return allMonitored || specificMonitored;
    }

    public class DcSecurityAnalysisContext {

        List<SensitivityFactor> sensitivityFactors;

        Map<String, BranchResult> preContingencyBranchResults;

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
            this.preContingencyBranchResults = new HashMap<>();
            this.preContingencyLimitViolationsMap = new HashMap<>();
            this.detector = violationDetector;
            this.dcPowerFactor = dcPowerFactor;
        }

        List<SensitivityFactor> getFactors() {
            return sensitivityFactors;
        }

        Map<String, BranchResult> getPreContingencyBranchResults() {
            return preContingencyBranchResults;
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
}
