/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
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
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.EquationUtil;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
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
import java.util.function.Supplier;

public class AcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected AcSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                 MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider, List<StateMonitor> stateMonitors) {
        super(network, detector, filter, matrixFactory, connectivityProvider, stateMonitors);
    }

    @Override
    SecurityAnalysisReport runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, the remaining mismatch is put on the slack bus and no exception is thrown.
        lfParametersExt.setThrowsExceptionInCaseOfSlackDistributionFailure(false);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSecurityAnalysis(network, contingencies, allSwitchesToOpen, lfParameters.isSimulShunt());

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, Reporter.NO_OP, true, false);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, acParameters.getNetworkParameters());

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

    List<LfNetwork> createNetworks(Set<Switch> allSwitchesToOpen, LfNetworkParameters networkParameters) {
        List<LfNetwork> lfNetworks;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID().toString();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            network.getSwitchStream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                    .forEach(sw -> sw.setRetained(false));
            allSwitchesToOpen.forEach(sw -> sw.setRetained(true));
            lfNetworks = Networks.load(network, networkParameters, Reporter.NO_OP);
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
        }
        return lfNetworks;
    }

    private SecurityAnalysisResult runSimulations(LfNetwork network, List<PropagatedContingency> propagatedContingencies, AcLoadFlowParameters acParameters,
                                                  LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        List<BranchResult> preContingencyBranchResults = new ArrayList<>();
        List<BusResults> preContingencyBusResults = new ArrayList<>();
        List<ThreeWindingsTransformerResult> preContingencyThreeWindingsTransformerResults = new ArrayList<>();

        // run pre-contingency simulation
        try (AcLoadFlowContext context = new AcLoadFlowContext(network, acParameters)) {
            AcLoadFlowResult preContingencyLoadFlowResult = new AcloadFlowEngine(context)
                    .run(Reporter.NO_OP);
            Map<String, BranchResult> results = new LinkedHashMap<>();
            addMonitorInfo(network, monitorIndex.getNoneStateMonitor(), preContingencyBranchResults, preContingencyBusResults,
                    preContingencyThreeWindingsTransformerResults, results, null);
            addMonitorInfo(network, monitorIndex.getAllStateMonitor(), preContingencyBranchResults, preContingencyBusResults,
                    preContingencyThreeWindingsTransformerResults, results, null);
            boolean preContingencyComputationOk = preContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolations = new LinkedHashMap<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            if (preContingencyComputationOk) {
                detectViolations(network.getBranches().stream(), network.getBuses().stream(), preContingencyLimitViolations);

                LOGGER.info("Save pre-contingency state");

                // save base state for later restoration after each contingency
                List<BusState> busStates = ElementState.save(network.getBuses(), BusState::save);
                List<BranchState> branchStates = ElementState.save(network.getBranches(), BranchState::save);

                // start a simulation for each of the contingency
                Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
                GraphDecrementalConnectivity<LfBus> connectivity = network.createDecrementalConnectivity(connectivityProvider);
                while (contingencyIt.hasNext()) {
                    PropagatedContingency propagatedContingency = contingencyIt.next();
                    LfContingency.create(propagatedContingency, network, connectivity, true)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                for (LfBranch branch : lfContingency.getBranches()) {
                                    branch.setDisabled(true);
                                }
                                for (LfBus bus : lfContingency.getBuses()) {
                                    bus.setDisabled(true);
                                }
                                for (Pair<LfShunt, Double> shuntAndB : lfContingency.getShunts()) {
                                    LfShunt shunt = shuntAndB.getKey();
                                    shunt.setB(shunt.getB() - shuntAndB.getValue());
                                }

                                distributedMismatch(network, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                                PostContingencyResult postContingencyResult = runPostContingencySimulation(network, context, propagatedContingency.getContingency(), lfContingency, preContingencyLimitViolations, results);
                                postContingencyResults.add(postContingencyResult);

                                if (contingencyIt.hasNext()) {
                                    LOGGER.info("Restore pre-contingency state");

                                    // restore base state
                                    ElementState.restore(busStates);
                                    ElementState.restore(branchStates);
                                }
                            });
                }
            }

            LimitViolationsResult preContingencyResult = new LimitViolationsResult(preContingencyComputationOk, new ArrayList<>(preContingencyLimitViolations.values()));
            return new SecurityAnalysisResult(preContingencyResult, postContingencyResults, preContingencyBranchResults,
                    preContingencyBusResults, preContingencyThreeWindingsTransformerResults);
        }
    }

    private PostContingencyResult runPostContingencySimulation(LfNetwork network, AcLoadFlowContext context, Contingency contingency, LfContingency lfContingency,
                                                               Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolations,
                                                               Map<String, BranchResult> preContingencyBranchResults) {
        LOGGER.info("Start post contingency '{}' simulation", lfContingency.getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation<AcVariableType, AcEquationType>> deactivatedEquations = new ArrayList<>();
        List<EquationTerm<AcVariableType, AcEquationType>> deactivatedEquationTerms = new ArrayList<>();
        List<BranchResult> branchResults = new ArrayList<>();
        List<BusResults> busResults = new ArrayList<>();
        List<ThreeWindingsTransformerResult> threeWindingsTransformerResults = new ArrayList<>();
        EquationUtil.deactivateEquations(lfContingency.getBranches(), lfContingency.getBuses(), context.getEquationSystem(), deactivatedEquations, deactivatedEquationTerms);

        // restart LF on post contingency equation system
        context.getParameters().getNewtonRaphsonParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        AcLoadFlowResult postContingencyLoadFlowResult = new AcloadFlowEngine(context)
                .run(Reporter.NO_OP);
        boolean postContingencyComputationOk = postContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        Map<Pair<String, Branch.Side>, LimitViolation> postContingencyLimitViolations = new LinkedHashMap<>();
        if (postContingencyComputationOk) {
            detectViolations(
                    network.getBranches().stream().filter(b -> !b.isDisabled()),
                    network.getBuses().stream().filter(b -> !b.isDisabled()),
                    postContingencyLimitViolations);

            addMonitorInfo(network, monitorIndex.getAllStateMonitor(), branchResults, busResults, threeWindingsTransformerResults, preContingencyBranchResults, lfContingency.getId());

            StateMonitor stateMonitor = monitorIndex.getSpecificStateMonitors().get(lfContingency.getId());
            if (stateMonitor != null) {
                addMonitorInfo(network, stateMonitor, branchResults, busResults, threeWindingsTransformerResults, preContingencyBranchResults, lfContingency.getId());
            }
        }

        preContingencyLimitViolations.forEach((subjectSideId, preContingencyViolation) -> {
            LimitViolation postContingencyViolation = postContingencyLimitViolations.get(subjectSideId);
            if (violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation)) {
                postContingencyLimitViolations.remove(subjectSideId);
            }
        });

        EquationUtil.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done in {} ms", lfContingency.getId(),
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(contingency, new LimitViolationsResult(postContingencyComputationOk,
                new ArrayList<>(postContingencyLimitViolations.values())), branchResults, busResults, threeWindingsTransformerResults);
    }
}
