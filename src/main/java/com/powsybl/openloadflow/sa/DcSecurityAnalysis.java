package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.security.*;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


public class DcSecurityAnalysis extends OpenSecurityAnalysis
{
    DcSensitivityAnalysis sensiDC = null;
    public DcSecurityAnalysis(final Network network, final LimitViolationDetector detector, final LimitViolationFilter filter, final MatrixFactory matrixFactory, final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider)
    {
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
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSecurityAnalysis(network, contingencies, allSwitchesToOpen);

        sensiDC = new DcSensitivityAnalysis(matrixFactory, connectivityProvider);

        SlackBusSelector slackBusSelector = OpenLoadFlowProvider.getSlackBusSelector(network, lfParameters, lfParametersExt);
        DcLoadFlowParameters dcParameters = new DcLoadFlowParameters(slackBusSelector,
                matrixFactory,
                true,
                lfParameters.isDcUseTransformerRatio(),
                lfParameters.isDistributedSlack(),
                lfParameters.getBalanceType(),
                false,
                lfParametersExt.getPlausibleActivePowerLimit(),
                lfParametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(),
                true,
                lfParameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN,
                lfParameters.getCountriesToBalance());

        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, dcParameters);
        OpenSensitivityAnalysisProvider providerSensi = new OpenSensitivityAnalysisProvider();

        List<SensitivityVariableSet> variableSets = new ArrayList<>();
        SensitivityAnalysisParameters sensitivityAnalysisParameters = new SensitivityAnalysisParameters();
        sensitivityAnalysisParameters.getLoadFlowParameters().setDc(true);

        ContingencyContext contingencyContext = new ContingencyContext(null, ContingencyContextType.ALL);
        String variableId = network.getLoads().iterator().next().getId();

        List<SensitivityFactor2> factors = new ArrayList<>();
        for(Branch b: network.getBranches()) {
            factors.add(new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, b.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, contingencyContext));
        }
        SensitivityAnalysisResult2 res = providerSensi.run(network, contingencies, variableSets, sensitivityAnalysisParameters, factors);

        List<LimitViolation> limits = new ArrayList<>();

        //Get matching contingency
        //

        LfNetwork mainNetwork = lfNetworks.get(0);
        for(Contingency c: contingencies) {
            List<SensitivityValue2> values = res.getValues(c.getId());

            for(SensitivityValue2 v : values) {
                SensitivityFactor2 factor = (SensitivityFactor2) v.getFactorContext();
                String branchId = factor.getFunctionId();

                Map<Pair<String, Branch.Side>, LimitViolation> violations = new HashMap<>();
                LfBranch lfb = mainNetwork.getBranchById(branchId);
                Branch b = network.getBranch(branchId);
                if(v.getFunctionReference() > b.getActivePowerLimits1().getPermanentLimit()) {
                    lfb.getLimits1().stream()
                       .filter(temporaryLimit1 -> v.getFunctionReference() > temporaryLimit1.getValue())
                       .findFirst() // only the most serious violation is added (the limits are sorted in descending gravity)
                       .map(temporaryLimit1 -> createLimitViolation1(lfb, temporaryLimit1))
                       .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));
                }
            }
        }
    }

    List<LfNetwork> createNetworks(Set<Switch> allSwitchesToOpen, DcLoadFlowParameters dcParameters) {
        List<LfNetwork> lfNetworks;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID().toString();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            network.getSwitchStream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                   .forEach(sw -> sw.setRetained(false));
            allSwitchesToOpen.forEach(sw -> sw.setRetained(true));
            LfNetworkParameters lfNetworkParameters = new LfNetworkParameters(dcParameters.getSlackBusSelector(), false, false, false, false,
                    dcParameters.getPlausibleActivePowerLimit(), false, dcParameters.isComputeMainConnectedComponentOnly(), dcParameters.getCountriesToBalance(), false);
            lfNetworks = LfNetwork.load(network, lfNetworkParameters, Reporter.NO_OP);
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
        }
        return lfNetworks;
    }
}
