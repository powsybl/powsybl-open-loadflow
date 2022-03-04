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
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.security.*;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.security.detectors.LoadingLimitType;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.sensitivity.*;

import java.util.*;
import java.util.stream.Collectors;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected DcSecurityAnalysis(final Network network, final LimitViolationDetector detector, final LimitViolationFilter filter,
                                 final MatrixFactory matrixFactory, final GraphDecrementalConnectivityFactory<LfBus> connectivityFactory, List<StateMonitor> stateMonitors) {
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
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult res = new SensitivityAnalysis.Runner(sensitivityAnalysisProvider)
                .run(network, workingVariantId, factors, contingencies, variableSets, sensitivityAnalysisParameters, computationManager, Reporter.NO_OP);

        DefaultLimitViolationDetector detector = new DefaultLimitViolationDetector(1.0f, EnumSet.allOf(LoadingLimitType.class));

        StateMonitor monitor = monitorIndex.getAllStateMonitor();
        Map<String, BranchResult> preContingencyBranchResults = new HashMap<>();

        List<LimitViolation> preContingencyLimitViolations = new ArrayList<>();
        for (SensitivityValue sensValue : res.getValues(null)) {
            SensitivityFactor factor = factors.get(sensValue.getFactorIndex());
            String branchId = factor.getFunctionId();
            Branch<?> branch = network.getBranch(branchId);
            preContingencyBranchResults.put(branchId, new BranchResult(branchId, sensValue.getFunctionReference(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
            detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()), preContingencyLimitViolations::add);
        }

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(true, preContingencyLimitViolations);

        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            Map<String, BranchResult> postContingencyBranchResults = new HashMap<>();
            List<SensitivityValue> values = res.getValues(contingency.getId());
            List<LimitViolation> violations = new ArrayList<>();
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

                detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()), violations::add);
            }

            postContingencyResults.add(new PostContingencyResult(contingency, true, violations, postContingencyBranchResults, Collections.emptyMap(), Collections.emptyMap()));
        }

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults, preContingencyBranchResults.values().stream().collect(Collectors.toList()), Collections.emptyList(), Collections.emptyList()));
    }
}
