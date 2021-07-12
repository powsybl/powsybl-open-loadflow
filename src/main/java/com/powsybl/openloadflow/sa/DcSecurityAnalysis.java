package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.security.*;
import com.powsybl.security.detectors.LoadingLimitType;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis {

    DcSensitivityAnalysis dcSensitivityAnalysis = null;

    protected DcSecurityAnalysis(final Network network, final LimitViolationDetector detector, final LimitViolationFilter filter,
                              final MatrixFactory matrixFactory, final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider, List<StateMonitor> stateMonitors) {
        super(network, detector, filter, matrixFactory, connectivityProvider, stateMonitors);
    }

    @Override
    SecurityAnalysisReport runSync(final SecurityAnalysisParameters securityAnalysisParameters, final ContingenciesProvider contingenciesProvider) {

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        dcSensitivityAnalysis = new DcSensitivityAnalysis(matrixFactory, connectivityProvider);

        OpenSensitivityAnalysisProvider sensitivityAnalysisProvider = new OpenSensitivityAnalysisProvider();

        List<SensitivityVariableSet> variableSets = new ArrayList<>();
        SensitivityAnalysisParameters sensitivityAnalysisParameters = new SensitivityAnalysisParameters();
        sensitivityAnalysisParameters.getLoadFlowParameters().setDc(true);

        ContingencyContext contingencyContext = new ContingencyContext(null, ContingencyContextType.ALL);
        String variableId = network.getLoads().iterator().next().getId();

        List<SensitivityFactor2> factors = new ArrayList<>();
        for (Branch<?> b : network.getBranches()) {
            factors.add(new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult2 res = sensitivityAnalysisProvider.run(network, contingencies, variableSets, sensitivityAnalysisParameters, factors);

        DefaultLimitViolationDetector detector = new DefaultLimitViolationDetector(1.0f, EnumSet.allOf(LoadingLimitType.class));

        StateMonitor monitor = monitorIndex.getAllStateMonitor();
        Map<String, BranchResult> preContingencyBranchResults = new HashMap<>();

        List<LimitViolation> preContingencyLimitViolations = new ArrayList<>();
        for (SensitivityValue2 sensValue : res.getValues(null)) {
            SensitivityFactor2 factor = (SensitivityFactor2) sensValue.getFactorContext();
            String branchId = factor.getFunctionId();
            Branch<?> branch = network.getBranch(branchId);
            preContingencyBranchResults.put(branchId, new BranchResult(branchId, sensValue.getFunctionReference(), Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN));
            detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()), preContingencyLimitViolations::add);
        }

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(true, preContingencyLimitViolations);

        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            Map<String, BranchResult> postContingencyBranchResults = new HashMap<>();
            List<SensitivityValue2> values = res.getValues(contingency.getId());
            List<LimitViolation> violations = new ArrayList<>();
            BranchResult preContRefBR = preContingencyBranchResults.get(contingency.getId());

            for (SensitivityValue2 v : values) {
                SensitivityFactor2 factor = (SensitivityFactor2) v.getFactorContext();
                String branchId = factor.getFunctionId();
                Branch<?> branch = network.getBranch(branchId);

                if (monitor.getBranchIds().contains(branchId)) {
                    BranchResult preContBR = preContingencyBranchResults.get(branchId);
                    double flowTransfer = computeFlowTransfer(v.getFunctionReference(), preContBR.getP1(), preContRefBR.getP1());
                    postContingencyBranchResults.put(branchId, new BranchResult(branchId, v.getFunctionReference(), Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, flowTransfer));
                }

                detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()), violations::add);
            }

            postContingencyResults.add(new PostContingencyResult(contingency, true, violations, postContingencyBranchResults, Collections.emptyMap(), Collections.emptyMap()));
        }

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults, preContingencyBranchResults.values().stream().collect(Collectors.toList()), Collections.emptyList(), Collections.emptyList()));
    }
}
