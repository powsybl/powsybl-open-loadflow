/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.security.*;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class AcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected AcSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                 MatrixFactory matrixFactory, GraphDecrementalConnectivityFactory<LfBus, LfBranch> connectivityFactory, List<StateMonitor> stateMonitors) {
        super(network, detector, filter, matrixFactory, connectivityFactory, stateMonitors);
    }

    private static SecurityAnalysisResult createNoResult() {
        return new SecurityAnalysisResult(new PreContingencyResult(new LimitViolationsResult(false, Collections.emptyList()),
                                                                   Collections.emptyList(),
                                                                   Collections.emptyList(),
                                                                   Collections.emptyList()),
                                          Collections.emptyList());
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager) {
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
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSecurityAnalysis(network, contingencies, allSwitchesToOpen,
                lfParameters.isShuntCompensatorVoltageControlOn(), lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                lfParameters.isHvdcAcEmulation());

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, Reporter.NO_OP, true, false);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, acParameters.getNetworkParameters());

        // run simulation on largest network
        SecurityAnalysisResult result;
        if (lfNetworks.isEmpty()) {
            result = createNoResult();
        } else {
            LfNetwork largestNetwork = lfNetworks.get(0);
            if (largestNetwork.isValid()) {
                result = runSimulations(largestNetwork, propagatedContingencies, acParameters, securityAnalysisParameters);
            } else {
                result = createNoResult();
            }
        }

        stopwatch.stop();
        LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

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
                                                  SecurityAnalysisParameters securityAnalysisParameters) {
        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        List<BranchResult> preContingencyBranchResults = new ArrayList<>();
        List<BusResult> preContingencyBusResults = new ArrayList<>();
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

                // save base state for later restoration after each contingency
                NetworkState networkState = NetworkState.save(network);

                // start a simulation for each of the contingency
                Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
                while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
                    PropagatedContingency propagatedContingency = contingencyIt.next();
                    propagatedContingency.toLfContingency(network, true)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                lfContingency.apply(loadFlowParameters.getBalanceType());

                                distributedMismatch(network, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                                PostContingencyResult postContingencyResult = runPostContingencySimulation(network, context, propagatedContingency.getContingency(), lfContingency,
                                        preContingencyLimitViolations, results, securityAnalysisParameters.getIncreasedViolationsParameters());
                                postContingencyResults.add(postContingencyResult);

                                if (contingencyIt.hasNext()) {
                                    // restore base state
                                    networkState.restore();
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
                                                               Map<String, BranchResult> preContingencyBranchResults, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters) {
        LOGGER.info("Start post contingency '{}' simulation on network {}", lfContingency.getId(), network);
        LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift load of {} buses",
                lfContingency.getId(), network, lfContingency.getDisabledBuses(), lfContingency.getDisabledBranches(), lfContingency.getLostGenerators(),
                lfContingency.getShuntsShift(), lfContingency.getBusesLoadShift());

        Stopwatch stopwatch = Stopwatch.createStarted();

        List<BranchResult> branchResults = new ArrayList<>();
        List<BusResult> busResults = new ArrayList<>();
        List<ThreeWindingsTransformerResult> threeWindingsTransformerResults = new ArrayList<>();

        // restart LF on post contingency equation system
        context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
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
            if (violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation, violationsParameters)) {
                postContingencyLimitViolations.remove(subjectSideId);
            }
        });

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done on network {} in {} ms", lfContingency.getId(),
                network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(contingency, new LimitViolationsResult(postContingencyComputationOk,
                new ArrayList<>(postContingencyLimitViolations.values())), branchResults, busResults, threeWindingsTransformerResults);
    }
}
