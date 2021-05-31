package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.security.*;
import com.powsybl.security.detectors.LoadingLimitType;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.*;
import java.util.function.Supplier;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis {

    DcSensitivityAnalysis sensiDC = null;

    public DcSecurityAnalysis(final Network network) {
        super(network);
    }

    public DcSecurityAnalysis(final Network network, final LimitViolationDetector detector, final LimitViolationFilter filter,
                              final MatrixFactory matrixFactory, final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        super(network, detector, filter, matrixFactory, connectivityProvider);
    }

    @Override
    SecurityAnalysisReport runSync(final SecurityAnalysisParameters securityAnalysisParameters, final ContingenciesProvider contingenciesProvider)
    {
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(securityAnalysisParameters.getLoadFlowParameters());
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, the remaining mismatch is put on the slack bus and no exception is thrown.
        lfParametersExt.setThrowsExceptionInCaseOfSlackDistributionFailure(false);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        sensiDC = new DcSensitivityAnalysis(matrixFactory, connectivityProvider);

        OpenSensitivityAnalysisProvider sensitivityAnalysisProvider = new OpenSensitivityAnalysisProvider();

        List<SensitivityVariableSet> variableSets = new ArrayList<>();
        SensitivityAnalysisParameters sensitivityAnalysisParameters = new SensitivityAnalysisParameters();
        sensitivityAnalysisParameters.getLoadFlowParameters().setDc(true);

        ContingencyContext contingencyContext = new ContingencyContext(null, ContingencyContextType.ALL);
        String variableId = network.getLoads().iterator().next().getId();

        List<SensitivityFactor2> factors = new ArrayList<>();
        for (Branch b : network.getBranches()) {
            factors.add(new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult2 res = sensitivityAnalysisProvider.run(network, contingencies, variableSets, sensitivityAnalysisParameters, factors);

        DefaultLimitViolationDetector detector = new DefaultLimitViolationDetector(1.0f, EnumSet.noneOf(LoadingLimitType.class));
        List<LimitViolation> preContingencyLimitViolations = new ArrayList<>();
        for (SensitivityValue2 sensValue : res.getValues(null)) {
            SensitivityFactor2 factor = (SensitivityFactor2) sensValue.getFactorContext();
            String branchId = factor.getFunctionId();
            Branch branch = network.getBranch(branchId);
            detector.checkPermanentLimit(branch, Branch.Side.ONE, Math.abs(sensValue.getFunctionReference()), preContingencyLimitViolations::add, LimitType.ACTIVE_POWER);
        }

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(true, preContingencyLimitViolations);

        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            List<SensitivityValue2> values = res.getValues(contingency.getId());
            List<LimitViolation> violations = new ArrayList<>();

            for (SensitivityValue2 v : values) {
                SensitivityFactor2 factor = (SensitivityFactor2) v.getFactorContext();
                String branchId = factor.getFunctionId();
                Branch branch = network.getBranch(branchId);
                detector.checkPermanentLimit(branch, Branch.Side.ONE, Math.abs(v.getFunctionReference()), violations::add, LimitType.ACTIVE_POWER);
            }

            postContingencyResults.add(new PostContingencyResult(contingency, true, violations));
        }

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults));
    }
}
