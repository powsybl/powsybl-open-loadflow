package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.security.*;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.*;
import java.util.function.Supplier;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis {

    DcSensitivityAnalysis sensiDC = null;

    public DcSecurityAnalysis(final Network network, final LimitViolationDetector detector, final LimitViolationFilter filter,
                              final MatrixFactory matrixFactory, final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        super(network, detector, filter, matrixFactory, connectivityProvider);
    }

    @Override
    SecurityAnalysisReport runSync(final SecurityAnalysisParameters securityAnalysisParameters, final ContingenciesProvider contingenciesProvider)
    {
        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(securityAnalysisParameters.getLoadFlowParameters());
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, the remaining mismatch is put on the slack bus and no exception is thrown.
        lfParametersExt.setThrowsExceptionInCaseOfSlackDistributionFailure(false);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        sensiDC = new DcSensitivityAnalysis(matrixFactory, connectivityProvider);

        OpenSensitivityAnalysisProvider providerSensi = new OpenSensitivityAnalysisProvider();

        List<SensitivityVariableSet> variableSets = new ArrayList<>();
        SensitivityAnalysisParameters sensitivityAnalysisParameters = new SensitivityAnalysisParameters();
        sensitivityAnalysisParameters.getLoadFlowParameters().setDc(true);

        ContingencyContext contingencyContext = new ContingencyContext(null, ContingencyContextType.ALL);
        String variableId = network.getLoads().iterator().next().getId();

        List<SensitivityFactor2> factors = new ArrayList<>();
        for (Branch b : network.getBranches()) {
            factors.add(new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult2 res = providerSensi.run(network, contingencies, variableSets, sensitivityAnalysisParameters, factors);

        List<LimitViolation> preContingencyLimitViolations = new ArrayList<>();
        for (SensitivityValue2 sensValue : res.getValues()) {
            SensitivityFactor2 factor = (SensitivityFactor2) sensValue.getFactorContext();
            String branchId = factor.getFunctionId();
            Branch branch = network.getBranch(branchId);
            double permanentLimit = branch.getActivePowerLimits1().getPermanentLimit();
            if (sensValue.getFunctionReference() >= permanentLimit) {
                preContingencyLimitViolations.add(new LimitViolation(branch.getId(), LimitViolationType.OTHER, null,
                        Integer.MAX_VALUE, permanentLimit, (float) 1., sensValue.getFunctionReference(), Branch.Side.ONE));
            }
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
                double permanentLimit = branch.getActivePowerLimits1().getPermanentLimit();
                if (v.getFunctionReference() >= permanentLimit) {
                    violations.add(new LimitViolation(branch.getId(), LimitViolationType.OTHER, null,
                            Integer.MAX_VALUE, permanentLimit, (float) 1., v.getFunctionReference(), Branch.Side.ONE));
                }
            }

            postContingencyResults.add(new PostContingencyResult(contingency, true, violations));
        }

        return new SecurityAnalysisReport(new SecurityAnalysisResult(preContingencyResult, postContingencyResults));
    }
}
