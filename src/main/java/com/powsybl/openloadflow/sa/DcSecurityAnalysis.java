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
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext> {

    protected DcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, Reporter reporter) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reporter);
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        var saReporter = Reports.createDcSecurityAnalysis(reporter, network.getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        OpenSecurityAnalysisParameters securityAnalysisParametersExt = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);

        // check actions validity
        checkActions(network, actions);

        // try for find all switches to be operated as actions.
        LfTopoConfig topoConfig = new LfTopoConfig();
        findAllSwitchesToOperate(network, actions, topoConfig);

        // try to find all ptc to retain because involved in ptc actions
        findAllPtcToOperate(actions, topoConfig);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);
        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setContingencyPropagation(securityAnalysisParametersExt.isContingencyPropagation())
                .setShuntCompensatorVoltageControlOn(false)
                .setSlackDistributionOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setHvdcAcEmulation(false);

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);

        var dcParameters = OpenLoadFlowParameters.createDcParameters(network, lfParameters,
                lfParametersExt, matrixFactory, connectivityFactory, false);
        boolean breakers = topoConfig.isBreaker();
        dcParameters.getNetworkParameters()
                .setBreakers(breakers)
                .setCacheEnabled(false); // force not caching as not supported in secu analysis

        // create networks including all necessary switches
        try (LfNetworkList lfNetworks = Networks.load(network, dcParameters.getNetworkParameters(), topoConfig, saReporter)) {
            // run simulation on largest network
            SecurityAnalysisResult result = lfNetworks.getLargest().filter(LfNetwork::isValid)
                    .map(largestNetwork -> runSimulations(largestNetwork, propagatedContingencies, dcParameters, securityAnalysisParameters, operatorStrategies, actions))
                    .orElse(createNoResult());

            stopwatch.stop();
            LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                    stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return new SecurityAnalysisReport(result);
        }
    }

    private SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies, DcLoadFlowParameters dcParameters,
                                                  SecurityAnalysisParameters securityAnalysisParameters, List<OperatorStrategy> operatorStrategies,
                                                  List<Action> actions) {
        Map<String, Action> actionsById = indexActionsById(actions);
        Set<Action> neededActions = new HashSet<>(actionsById.size());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = indexOperatorStrategiesByContingencyId(propagatedContingencies, operatorStrategies, actionsById, neededActions);
        Map<String, LfAction> lfActionById = createLfActions(lfNetwork, neededActions, network, dcParameters.getNetworkParameters()); // only convert needed actions

        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            Reporter networkReporter = lfNetwork.getReporter();
            Reporter preContSimReporter = Reports.createPreContingencySimulation(networkReporter);
            lfNetwork.setReporter(preContSimReporter);

            // run pre-contingency simulation
            DcLoadFlowResult preContingencyLoadFlowResult = new DcLoadFlowEngine(context)
                    .run();

            boolean preContingencyComputationOk = preContingencyLoadFlowResult.isSucceeded();
            var preContingencyLimitViolationManager = new LimitViolationManager();
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            if (preContingencyComputationOk) {
                // update network result
                preContingencyNetworkResult.update();

                // detect violations
                preContingencyLimitViolationManager.detectViolations(lfNetwork);

                // save base state for later restoration after each contingency
                NetworkState networkState = NetworkState.save(lfNetwork);

                // start a simulation for each of the contingency
                Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
                while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
                    PropagatedContingency propagatedContingency = contingencyIt.next();
                    propagatedContingency.toLfContingency(lfNetwork, true)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                Reporter postContSimReporter = Reports.createPostContingencySimulation(networkReporter, lfContingency.getId());
                                lfNetwork.setReporter(postContSimReporter);

                                lfContingency.apply(loadFlowParameters.getBalanceType());

                                distributedMismatch(lfNetwork, DcLoadFlowEngine.getActivePowerMismatch(lfNetwork.getBuses().stream().filter(bus -> !bus.isDisabled()).collect(Collectors.toSet())),
                                        loadFlowParameters, openLoadFlowParameters);

                                var postContingencyResult = runPostContingencySimulation(lfNetwork, context, propagatedContingency.getContingency(),
                                        lfContingency, preContingencyLimitViolationManager,
                                        securityAnalysisParameters.getIncreasedViolationsParameters(),
                                        preContingencyNetworkResult, createResultExtension);
                                postContingencyResults.add(postContingencyResult);

                                List<OperatorStrategy> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId.get(lfContingency.getId());
                                if (operatorStrategiesForThisContingency != null) {
                                    // we have at least an operator strategy for this contingency.
                                    if (operatorStrategiesForThisContingency.size() == 1) {
                                        runActionSimulation(lfNetwork, context,
                                                operatorStrategiesForThisContingency.get(0), preContingencyLimitViolationManager,
                                                securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
                                                createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
                                                dcParameters.getNetworkParameters())
                                                .ifPresent(operatorStrategyResults::add);
                                    } else {
                                        // save post contingency state for later restoration after action
                                        NetworkState postContingencyNetworkState = NetworkState.save(lfNetwork);
                                        for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
                                            runActionSimulation(lfNetwork, context,
                                                    operatorStrategy, preContingencyLimitViolationManager,
                                                    securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
                                                    createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
                                                    dcParameters.getNetworkParameters())
                                                    .ifPresent(result -> {
                                                        operatorStrategyResults.add(result);
                                                        postContingencyNetworkState.restore();
                                                    });
                                        }
                                    }
                                }

                                if (contingencyIt.hasNext()) {
                                    // restore base state
                                    networkState.restore();
                                }
                            });
                }
            }

            return new SecurityAnalysisResult(
                    new PreContingencyResult(
                            LoadFlowResult.ComponentResult.Status.CONVERGED,
                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
                    postContingencyResults, operatorStrategyResults);
        }
    }

    private Optional<OperatorStrategyResult> runActionSimulation(LfNetwork network, DcLoadFlowContext context, OperatorStrategy operatorStrategy,
                                                                 LimitViolationManager preContingencyLimitViolationManager,
                                                                 SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                 Map<String, LfAction> lfActionById, boolean createResultExtension, LfContingency contingency,
                                                                 LimitViolationsResult postContingencyLimitViolations, LfNetworkParameters networkParameters) {
        OperatorStrategyResult operatorStrategyResult = null;

        List<String> actionIds = checkCondition(operatorStrategy, postContingencyLimitViolations);
        if (!actionIds.isEmpty()) {
            operatorStrategyResult = runActionSimulation(network, context, operatorStrategy, actionIds, preContingencyLimitViolationManager,
                    violationsParameters, lfActionById, createResultExtension, contingency, networkParameters);
        }

        return Optional.ofNullable(operatorStrategyResult);
    }

    @Override
    protected PostContingencyComputationStatus runActionLoadFlow(DcLoadFlowContext context) {
        DcLoadFlowResult dcLoadFlowResult = new DcLoadFlowEngine(context).run();

        boolean postActionsComputationOk = dcLoadFlowResult.isSucceeded();
        return postActionsComputationOk ? PostContingencyComputationStatus.CONVERGED : PostContingencyComputationStatus.FAILED;
    }
}
