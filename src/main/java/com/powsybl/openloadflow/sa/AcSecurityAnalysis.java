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
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.action.SwitchAction;
import com.powsybl.security.condition.AllViolationCondition;
import com.powsybl.security.condition.AnyViolationCondition;
import com.powsybl.security.condition.AtLeastOneViolationCondition;
import com.powsybl.security.condition.TrueCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected AcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, Reporter reporter) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reporter);
    }

    private static SecurityAnalysisResult createNoResult() {
        return new SecurityAnalysisResult(new LimitViolationsResult(false, Collections.emptyList()), Collections.emptyList());
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        var saReporter = Reports.createAcSecurityAnalysis(reporter, network.getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, the remaining mismatch is put on the slack bus and no exception is thrown.
        lfParametersExt.setThrowsExceptionInCaseOfSlackDistributionFailure(false);
        OpenSecurityAnalysisParameters securityAnalysisParametersExt = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, allSwitchesToOpen,
                lfParameters.isShuntCompensatorVoltageControlOn(), lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                lfParameters.isHvdcAcEmulation(), securityAnalysisParametersExt.isContingencyPropagation());

        // try for find all switches to be operated as actions.
        Set<Switch> allSwitchesToClose = new HashSet<>();
        findAllSwitchesToOperate(network, actions, allSwitchesToClose, allSwitchesToOpen);
        boolean breakers = !(allSwitchesToOpen.isEmpty() && allSwitchesToClose.isEmpty());
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, saReporter, breakers, false);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), allSwitchesToOpen, allSwitchesToClose, saReporter);

        // run simulation on largest network
        SecurityAnalysisResult result;
        if (lfNetworks.isEmpty()) {
            result = createNoResult();
        } else {
            LfNetwork largestNetwork = lfNetworks.get(0);
            if (largestNetwork.isValid()) {
                Map<String, LfAction> lfActionsById = createLfActions(largestNetwork, actions);
                Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = indexOperatorStrategiesByContingencyId(propagatedContingencies, operatorStrategies);
                result = runSimulations(largestNetwork, propagatedContingencies, acParameters, securityAnalysisParameters, operatorStrategiesByContingencyId, lfActionsById, allSwitchesToClose);
            } else {
                result = createNoResult();
            }
        }

        stopwatch.stop();
        LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new SecurityAnalysisReport(result);
    }

    public static void distributedMismatch(LfNetwork network, double mismatch, LoadFlowParameters loadFlowParameters,
                                           OpenLoadFlowParameters openLoadFlowParameters) {
        if (loadFlowParameters.isDistributedSlack() && Math.abs(mismatch) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant());
            activePowerDistribution.run(network, mismatch);
        }
    }

    private static void findAllSwitchesToOperate(Network network, List<Action> actions, Set<Switch> allSwitchesToClose, Set<Switch> allSwitchesToOpen) {
        actions.stream().filter(action -> action.getType().equals(SwitchAction.NAME))
                .forEach(action -> {
                    String switchId = ((SwitchAction) action).getSwitchId();
                    Switch sw = network.getSwitch(switchId);
                    boolean toOpen = ((SwitchAction) action).isOpen();
                    if (sw.isOpen() && !toOpen) { // the switch is open and the action will close it.
                        allSwitchesToClose.add(sw);
                    } else if (!sw.isOpen() && toOpen) { // the switch is closed and the action will open it.
                        allSwitchesToOpen.add(sw);
                    }
                });
    }

    private static Map<String, LfAction> createLfActions(LfNetwork network, List<Action> actions) {
        return actions.stream().collect(Collectors.toMap(Action::getId, action -> new LfAction(action, network)));
    }

    private static Map<String, List<OperatorStrategy>> indexOperatorStrategiesByContingencyId(List<PropagatedContingency> propagatedContingencies,
                                                                                              List<OperatorStrategy> operatorStrategies) {
        Set<String> contingencyIds = propagatedContingencies.stream().map(propagatedContingency -> propagatedContingency.getContingency().getId()).collect(Collectors.toSet());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = new HashMap<>();
        for (OperatorStrategy operatorStrategy : operatorStrategies) {
            if (contingencyIds.contains(operatorStrategy.getContingencyId())) {
                operatorStrategiesByContingencyId.computeIfAbsent(operatorStrategy.getContingencyId(), key -> new ArrayList<>()).add(operatorStrategy);
            } else {
                throw new PowsyblException("An operator strategy associated to contingency '" + operatorStrategy.getContingencyId() +
                        "' but this contingency is not present in the list of contingencies");
            }
        }
        return operatorStrategiesByContingencyId;
    }

    private static void restoreInitialTopology(LfNetwork network, Set<Switch> allSwitchesToClose) {
        if (allSwitchesToClose.isEmpty()) {
            return;
        }
        var connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();
        allSwitchesToClose.stream().map(Identifiable::getId).forEach(id -> {
            LfBranch branch = network.getBranchById(id);
            connectivity.removeEdge(branch);
        });
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        removedBuses.forEach(bus -> bus.setDisabled(true));
        Set<LfBranch> removedBranches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : removedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(removedBranches::add);
        }
        removedBranches.forEach(branch -> branch.setDisabled(true));
    }

    private SecurityAnalysisResult runSimulations(LfNetwork network, List<PropagatedContingency> propagatedContingencies, AcLoadFlowParameters acParameters,
                                                  SecurityAnalysisParameters securityAnalysisParameters, Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId,
                                                  Map<String, LfAction> lfActionById, Set<Switch> allSwitchesToClose) {
        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (AcLoadFlowContext context = new AcLoadFlowContext(network, acParameters)) {
            Reporter networkReporter = network.getReporter();
            Reporter preContSimReporter = Reports.createPreContingencySimulation(networkReporter);
            network.setReporter(preContSimReporter);

            // run pre-contingency simulation
            restoreInitialTopology(network, allSwitchesToClose);
            AcLoadFlowResult preContingencyLoadFlowResult = new AcloadFlowEngine(context)
                    .run();

            boolean preContingencyComputationOk = preContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            var preContingencyLimitViolationManager = new LimitViolationManager();
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            var preContingencyNetworkResult = new PreContingencyNetworkResult(network, monitorIndex, createResultExtension);
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            if (preContingencyComputationOk) {
                // update network result
                preContingencyNetworkResult.update();

                // detect violations
                preContingencyLimitViolationManager.detectViolations(network);

                // save base state for later restoration after each contingency
                NetworkState networkState = NetworkState.save(network);

                // start a simulation for each of the contingency
                Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
                while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
                    PropagatedContingency propagatedContingency = contingencyIt.next();
                    propagatedContingency.toLfContingency(network)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                Reporter postContSimReporter = Reports.createPostContingencySimulation(networkReporter, lfContingency.getId());
                                network.setReporter(postContSimReporter);

                                lfContingency.apply(loadFlowParameters.getBalanceType());

                                distributedMismatch(network, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                                var postContingencyResult = runPostContingencySimulation(network, context, propagatedContingency.getContingency(),
                                                                                         lfContingency, preContingencyLimitViolationManager,
                                                                                         securityAnalysisParameters.getIncreasedViolationsParameters(),
                                                                                         preContingencyNetworkResult, createResultExtension);
                                postContingencyResults.add(postContingencyResult);

                                List<OperatorStrategy> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId.get(lfContingency.getId());
                                if (operatorStrategiesForThisContingency != null) {
                                    // we have at least an operator strategy for this contingency.
                                    if (operatorStrategiesForThisContingency.size() == 1) {
                                        runActionSimulation(network, context,
                                                operatorStrategiesForThisContingency.get(0), preContingencyLimitViolationManager,
                                                securityAnalysisParameters.getIncreasedViolationsParameters(), postContingencyResult.getLimitViolationsResult(), lfActionById,
                                                createResultExtension, lfContingency)
                                                .ifPresent(operatorStrategyResults::add);
                                    } else {
                                        // save post contingency state for later restoration after action
                                        NetworkState postContingencyNetworkState = NetworkState.save(network);
                                        for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
                                            runActionSimulation(network, context,
                                                    operatorStrategy, preContingencyLimitViolationManager,
                                                    securityAnalysisParameters.getIncreasedViolationsParameters(), postContingencyResult.getLimitViolationsResult(), lfActionById,
                                                    createResultExtension, lfContingency)
                                                    .ifPresent(operatorStrategyResults::add);
                                            postContingencyNetworkState.restore();
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

            return new SecurityAnalysisResult(new LimitViolationsResult(preContingencyComputationOk, preContingencyLimitViolationManager.getLimitViolations()),
                                              postContingencyResults,
                                              preContingencyNetworkResult.getBranchResults(),
                                              preContingencyNetworkResult.getBusResults(),
                                              preContingencyNetworkResult.getThreeWindingsTransformerResults(),
                                              operatorStrategyResults);
        }
    }

    private PostContingencyResult runPostContingencySimulation(LfNetwork network, AcLoadFlowContext context, Contingency contingency, LfContingency lfContingency,
                                                               LimitViolationManager preContingencyLimitViolationManager,
                                                               SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                               PreContingencyNetworkResult preContingencyNetworkResult, boolean createResultExtension) {
        LOGGER.info("Start post contingency '{}' simulation on network {}", lfContingency.getId(), network);
        LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift load of {} buses",
                lfContingency.getId(), network, lfContingency.getDisabledBuses(), lfContingency.getDisabledBranches(), lfContingency.getLostGenerators(),
                lfContingency.getShuntsShift(), lfContingency.getBusesLoadShift());

        Stopwatch stopwatch = Stopwatch.createStarted();

        // restart LF on post contingency equation system
        context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        AcLoadFlowResult postContingencyLoadFlowResult = new AcloadFlowEngine(context)
                .run();

        boolean postContingencyComputationOk = postContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, violationsParameters);
        var postContingencyNetworkResult = new PostContingencyNetworkResult(network, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency);

        if (postContingencyComputationOk) {
            // update network result
            postContingencyNetworkResult.update();

            // detect violations
            postContingencyLimitViolationManager.detectViolations(network);
        }

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done on network {} in {} ms", lfContingency.getId(),
                network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(contingency,
                                         new LimitViolationsResult(postContingencyComputationOk, postContingencyLimitViolationManager.getLimitViolations()),
                                         postContingencyNetworkResult.getBranchResults(),
                                         postContingencyNetworkResult.getBusResults(),
                                         postContingencyNetworkResult.getThreeWindingsTransformerResults());
    }

    private Optional<OperatorStrategyResult> runActionSimulation(LfNetwork network, AcLoadFlowContext context, OperatorStrategy operatorStrategy,
                                                                 LimitViolationManager preContingencyLimitViolationManager,
                                                                 SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                 LimitViolationsResult postContingencyLimitViolations, Map<String, LfAction> lfActionById,
                                                                 boolean createResultExtension, LfContingency contingency) {
        OperatorStrategyResult operatorStrategyResult = null;

        if (checkCondition(operatorStrategy, postContingencyLimitViolations)) {
            LOGGER.info("Start operator strategy {} after contingency '{}' simulation on network {}", operatorStrategy.getId(),
                    operatorStrategy.getContingencyId(), network);

            List<LfAction> operatorStrategyLfActions = operatorStrategy.getActionIds().stream()
                    .map(id -> {
                        LfAction lfAction = lfActionById.get(id);
                        if (lfAction == null) {
                            throw new PowsyblException("Action '" + id + "' of operator strategy '" + operatorStrategy.getId() + "' not found");
                        }
                        return lfAction;
                    })
                    .collect(Collectors.toList());

            LfAction.apply(operatorStrategyLfActions, network, contingency);

            Stopwatch stopwatch = Stopwatch.createStarted();

            // restart LF on post contingency and post actions equation system
            context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer(true));
            AcLoadFlowResult postActionsLoadFlowResult = new AcloadFlowEngine(context)
                    .run();

            boolean postActionsComputationOk = postActionsLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            var postActionsViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, violationsParameters);
            var postActionsNetworkResult = new PreContingencyNetworkResult(network, monitorIndex, createResultExtension);

            if (postActionsComputationOk) {
                // update network result
                postActionsNetworkResult.update();

                // detect violations
                postActionsViolationManager.detectViolations(network);
            }

            stopwatch.stop();

            LOGGER.info("Operator strategy {} after contingency '{}' simulation done on network {} in {} ms", operatorStrategy.getId(),
                    operatorStrategy.getContingencyId(), network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

            operatorStrategyResult = new OperatorStrategyResult(operatorStrategy,
                    new LimitViolationsResult(postActionsComputationOk,
                            postActionsViolationManager.getLimitViolations()),
                    new NetworkResult(postActionsNetworkResult.getBranchResults(),
                            postActionsNetworkResult.getBusResults(),
                            postActionsNetworkResult.getThreeWindingsTransformerResults()));
        }

        return Optional.ofNullable(operatorStrategyResult);
    }

    private boolean checkCondition(OperatorStrategy operatorStrategy, LimitViolationsResult limitViolationsResult) {
        Set<String> limitViolationEquipmentIds = limitViolationsResult.getLimitViolations().stream()
                .map(LimitViolation::getSubjectId)
                .collect(Collectors.toSet());
        switch (operatorStrategy.getCondition().getType()) {
            case TrueCondition.NAME:
                return true;
            case AnyViolationCondition.NAME:
                return !limitViolationEquipmentIds.isEmpty();
            case AtLeastOneViolationCondition.NAME: {
                AtLeastOneViolationCondition atLeastCondition = (AtLeastOneViolationCondition) operatorStrategy.getCondition();
                Set<String> commonEquipmentIds = atLeastCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return !commonEquipmentIds.isEmpty();
            }
            case AllViolationCondition.NAME: {
                AllViolationCondition allCondition = (AllViolationCondition) operatorStrategy.getCondition();
                Set<String> commonEquipmentIds = allCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return commonEquipmentIds.equals(new HashSet<>(allCondition.getViolationIds()));
            }
            default:
                throw new UnsupportedOperationException("Unsupported condition type: " + operatorStrategy.getCondition().getType());
        }
    }
}
