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
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.security.*;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.security.detectors.LoadingLimitType;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected DcSecurityAnalysis(final Network network, final LimitViolationDetector detector, final LimitViolationFilter filter,
                                 final MatrixFactory matrixFactory, final GraphDecrementalConnectivityFactory<LfBus, LfBranch> connectivityFactory, List<StateMonitor> stateMonitors) {
        super(network, detector, filter, matrixFactory, connectivityFactory, stateMonitors);
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager) {

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        OpenSensitivityAnalysisProvider sensitivityAnalysisProvider = new OpenSensitivityAnalysisProvider(matrixFactory);

        List<SensitivityVariableSet> variableSets = Collections.emptyList();
        SensitivityAnalysisParameters sensitivityAnalysisParameters = new SensitivityAnalysisParameters();
        sensitivityAnalysisParameters.setLoadFlowParameters(securityAnalysisParameters.getLoadFlowParameters());

        ContingencyContext contingencyContext = new ContingencyContext(null, ContingencyContextType.ALL);
        String variableId = network.getLoads().iterator().next().getId();

        List<SensitivityFactor> factors = new ArrayList<>();
        for (Branch<?> b : network.getBranches()) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult res = new SensitivityAnalysis.Runner(sensitivityAnalysisProvider)
                .run(network, workingVariantId, factors, contingencies, variableSets, sensitivityAnalysisParameters, computationManager, Reporter.NO_OP);

        DefaultLimitViolationDetector detector = new DefaultLimitViolationDetector(1.0f, EnumSet.allOf(LoadingLimitType.class));

        StateMonitor monitor = monitorIndex.getAllStateMonitor();
        Map<String, BranchResult> preContingencyBranchResults = new HashMap<>();

        Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolationsMap = new HashMap<>();
        for (SensitivityValue sensValue : res.getValues(null)) {
            SensitivityFactor factor = factors.get(sensValue.getFactorIndex());
            String branchId = factor.getFunctionId();
            Branch<?> branch = network.getBranch(branchId);
            preContingencyBranchResults.put(branchId, new BranchResult(branchId, sensValue.getFunctionReference(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
            detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()),
                violation -> preContingencyLimitViolationsMap.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            checkDcCurrent(branch, Math.abs(sensValue.getFunctionReference()));
        }

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(true,
                new ArrayList<>(preContingencyLimitViolationsMap.values()));

        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            Map<String, BranchResult> postContingencyBranchResults = new HashMap<>();
            List<SensitivityValue> values = res.getValues(contingency.getId());
            Map<Pair<String, Branch.Side>, LimitViolation> violations = new HashMap<>();
            double branchInContingencyP1 = Double.NaN;
            if (contingency.getElements().size() == 1 && contingency.getElements().get(0).getType() == ContingencyElementType.BRANCH) {
                branchInContingencyP1 = preContingencyBranchResults.get(contingency.getElements().get(0).getId()).getP1();
            }

            for (SensitivityValue v : values) {
                SensitivityFactor factor = factors.get(v.getFactorIndex());
                String branchId = factor.getFunctionId();
                Branch<?> branch = network.getBranch(branchId);

                if (monitor.getBranchIds().contains(branchId)) {
                    BranchResult preContingencyBranchResult = preContingencyBranchResults.get(branchId);
                    double flowTransfer = Double.isNaN(branchInContingencyP1) ? Double.NaN : (v.getFunctionReference() - preContingencyBranchResult.getP1()) / branchInContingencyP1;
                    postContingencyBranchResults.put(branchId, new BranchResult(branchId, v.getFunctionReference(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, flowTransfer));
                }
                detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()),
                    violation -> violations.put(Pair.of(violation.getSubjectId(), violation.getSide()), violation));
            }
            preContingencyLimitViolationsMap.forEach((subjectSideId, preContingencyViolation) -> {
                LimitViolation postContingencyViolation = violations.get(subjectSideId);
                if (violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation, securityAnalysisParameters.getIncreasedViolationsParameters())) {
                    violations.remove(subjectSideId);
                }
            });
            postContingencyResults.add(new PostContingencyResult(contingency, true, new ArrayList<>(violations.values()), postContingencyBranchResults, Collections.emptyMap(), Collections.emptyMap()));
        }

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults, new ArrayList<>(preContingencyBranchResults.values()), Collections.emptyList(), Collections.emptyList()));
    }

    private void checkDcCurrent(Branch<?> branch, double activePower) {
        // Check side 1
        // TODO: get cosphi from parameters
        double branchCurrent = currentFromAdnActivePower(activePower, branch.getTerminal1().getVoltageLevel().getNominalV(), 0.4);

        // Check side 2
    }

    public static double currentFromAdnActivePower(double activePower, double dcVoltage, double cosPhi) {
        return 1000 * activePower / (Math.sqrt(3) * cosPhi * dcVoltage);
    }
}
