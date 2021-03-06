package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.BranchState;
import com.powsybl.openloadflow.util.BusState;
import com.powsybl.openloadflow.util.LfContingency;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.security.*;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResults;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected AcSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider, List<StateMonitor> stateMonitors) {
        super(network, detector, filter, matrixFactory, connectivityProvider, stateMonitors);
    }

    @Override
    SecurityAnalysisReport runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        Stopwatch stopwatch = Stopwatch.createStarted();

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

        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters, lfParametersExt, true);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, acParameters);

        // run simulation on largest network
        if (lfNetworks.isEmpty()) {
            throw new PowsyblException("Empty network list");
        }
        LfNetwork largestNetwork = lfNetworks.get(0);
        if (!largestNetwork.isValid()) {
            throw new PowsyblException("Largest network is invalid");
        }
        SecurityAnalysisResult result = runSimulations(largestNetwork, propagatedContingencies, acParameters, lfParameters, lfParametersExt);

        stopwatch.stop();
        LOGGER.info("Security analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new SecurityAnalysisReport(result);
    }

    List<LfNetwork> createNetworks(Set<Switch> allSwitchesToOpen, AcLoadFlowParameters acParameters) {
        List<LfNetwork> lfNetworks;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID().toString();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            network.getSwitchStream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                   .forEach(sw -> sw.setRetained(false));
            allSwitchesToOpen.forEach(sw -> sw.setRetained(true));
            lfNetworks = AcloadFlowEngine.createNetworks(network, acParameters, Reporter.NO_OP);
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
        }
        return lfNetworks;
    }

    private SecurityAnalysisResult runSimulations(LfNetwork network, List<PropagatedContingency> propagatedContingencies, AcLoadFlowParameters acParameters,
                                                  LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        // create a contingency list that impact the network
        List<LfContingency> contingencies = createContingencies(propagatedContingencies, network);
        List<BranchResult> preContingencyBranchResults = new ArrayList<>();
        List<BusResults> preContingencyBusResults = new ArrayList<>();
        List<ThreeWindingsTransformerResult> preContingencyThreeWindingsTransformerResults = new ArrayList<>();

        // run pre-contingency simulation
        try (AcloadFlowEngine engine = new AcloadFlowEngine(network, acParameters)) {
            AcLoadFlowResult preContingencyLoadFlowResult = engine.run(Reporter.NO_OP);
            addMonitorInfo(network, monitorIndex.getNoneStateMonitor(), preContingencyBranchResults, preContingencyBusResults,
                    preContingencyThreeWindingsTransformerResults);
            addMonitorInfo(network, monitorIndex.getAllStateMonitor(), preContingencyBranchResults, preContingencyBusResults,
                    preContingencyThreeWindingsTransformerResults);
            boolean preContingencyComputationOk = preContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolations = new LinkedHashMap<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            if (preContingencyComputationOk) {
                detectViolations(network.getBranches().stream(), network.getBuses().stream(), preContingencyLimitViolations);

                LOGGER.info("Save pre-contingency state");

                // save base state for later restoration after each contingency
                Map<LfBus, BusState> busStates = BusState.createBusStates(network.getBuses());
                Map<LfBranch, BranchState> branchStates = BranchState.createBranchStates(network.getBranches());
                for (LfBus bus : network.getBuses()) {
                    bus.setVoltageControlSwitchOffCount(0);
                }

                // start a simulation for each of the contingency
                Iterator<LfContingency> contingencyIt = contingencies.iterator();
                while (contingencyIt.hasNext()) {
                    LfContingency lfContingency = contingencyIt.next();

                    for (LfBus bus : lfContingency.getBuses()) {
                        bus.setDisabled(true);
                    }
                    for (LfBranch branch : lfContingency.getBranches()) {
                        branch.setDisabled(true);
                    }

                    distributedMismatch(network, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                    PostContingencyResult postContingencyResult = runPostContingencySimulation(network, engine, lfContingency, preContingencyLimitViolations);
                    postContingencyResults.add(postContingencyResult);

                    if (contingencyIt.hasNext()) {
                        LOGGER.info("Restore pre-contingency state");

                        // restore base state
                        BusState.restoreBusStates(busStates);
                        BranchState.restoreBranchStates(branchStates);
                    }
                }
            }

            LimitViolationsResult preContingencyResult = new LimitViolationsResult(preContingencyComputationOk, new ArrayList<>(preContingencyLimitViolations.values()));
            return new SecurityAnalysisResult(preContingencyResult, postContingencyResults, preContingencyBranchResults,
                    preContingencyBusResults, preContingencyThreeWindingsTransformerResults);
        }
    }

    private PostContingencyResult runPostContingencySimulation(LfNetwork network, AcloadFlowEngine engine, LfContingency lfContingency,
                                                               Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolations) {
        LOGGER.info("Start post contingency '{}' simulation", lfContingency.getContingency().getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation> deactivatedEquations = new ArrayList<>();
        List<EquationTerm> deactivatedEquationTerms = new ArrayList<>();
        List<BranchResult> branchResults = new ArrayList<>();
        List<BusResults> busResults = new ArrayList<>();
        List<ThreeWindingsTransformerResult> threeWindingsTransformerResults = new ArrayList<>();
        LfContingency.deactivateEquations(lfContingency, engine.getEquationSystem(), deactivatedEquations, deactivatedEquationTerms);

        // restart LF on post contingency equation system
        engine.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        AcLoadFlowResult postContingencyLoadFlowResult = engine.run(Reporter.NO_OP);
        boolean postContingencyComputationOk = postContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        Map<Pair<String, Branch.Side>, LimitViolation> postContingencyLimitViolations = new LinkedHashMap<>();
        if (postContingencyComputationOk) {
            detectViolations(
                    network.getBranches().stream().filter(b -> !b.isDisabled()),
                    network.getBuses().stream().filter(b -> !b.isDisabled()),
                    postContingencyLimitViolations);
            addMonitorInfo(network, monitorIndex.getAllStateMonitor(), branchResults, busResults, threeWindingsTransformerResults);
            StateMonitor stateMonitor = monitorIndex.getSpecificStateMonitors().get(lfContingency.getContingency().getId());
            if (stateMonitor != null) {
                addMonitorInfo(network, stateMonitor, branchResults, busResults, threeWindingsTransformerResults);
            }
        }

        preContingencyLimitViolations.forEach((subjectSideId, preContingencyViolation) -> {
            LimitViolation postContingencyViolation = postContingencyLimitViolations.get(subjectSideId);
            if (violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation)) {
                postContingencyLimitViolations.remove(subjectSideId);
            }
        });

        LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done in {} ms", lfContingency.getContingency().getId(),
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(lfContingency.getContingency(), postContingencyComputationOk, new ArrayList<>(postContingencyLimitViolations.values()),
                branchResults.stream().collect(Collectors.toMap(BranchResult::getBranchId, Function.identity())),
                busResults.stream().collect(Collectors.toMap(BusResults::getVoltageLevelId, Function.identity())),
                threeWindingsTransformerResults.stream().collect(Collectors.toMap(ThreeWindingsTransformerResult::getThreeWindingsTransformerId,
                        Function.identity())));
    }
}
