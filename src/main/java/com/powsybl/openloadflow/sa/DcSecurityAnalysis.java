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
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.sensitivity.*;

import java.util.*;
import java.util.function.Supplier;

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

        List<SensitivityFactor> factors = new ArrayList<>();
        for (Branch<?> b : network.getBranches()) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult res = sensitivityAnalysisProvider.run(network, contingencies, variableSets, sensitivityAnalysisParameters, factors);

        DefaultLimitViolationDetector detector = new DefaultLimitViolationDetector(1.0f, EnumSet.allOf(LoadingLimitType.class));

        List<LimitViolation> preContingencyLimitViolations = new ArrayList<>();
        for (SensitivityValue sensValue : res.getValues(null)) {
            SensitivityFactor factor = sensValue.getFactor();
            String branchId = factor.getFunctionId();
            Branch<?> branch = network.getBranch(branchId);
            detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()), preContingencyLimitViolations::add);
        }

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(true, preContingencyLimitViolations);

        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            List<SensitivityValue> values = res.getValues(contingency.getId());
            List<LimitViolation> violations = new ArrayList<>();

            for (SensitivityValue v : values) {
                SensitivityFactor factor = v.getFactor();
                String branchId = factor.getFunctionId();
                Branch<?> branch = network.getBranch(branchId);
                detector.checkActivePower(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()), violations::add);
            }

            postContingencyResults.add(new PostContingencyResult(contingency, true, violations));
        }

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults));
    }
}
