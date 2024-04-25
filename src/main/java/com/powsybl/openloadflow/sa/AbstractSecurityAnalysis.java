/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.action.*;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.lf.LoadFlowEngine;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.*;
import com.powsybl.security.condition.AllViolationCondition;
import com.powsybl.security.condition.AnyViolationCondition;
import com.powsybl.security.condition.AtLeastOneViolationCondition;
import com.powsybl.security.condition.TrueCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.*;
import com.powsybl.security.strategy.ConditionalActions;
import com.powsybl.security.strategy.OperatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractSecurityAnalysis<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity,
                                               P extends AbstractLoadFlowParameters<P>,
                                               C extends LoadFlowContext<V, E, P>,
                                               R extends com.powsybl.openloadflow.lf.LoadFlowResult> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSecurityAnalysis.class);

    protected final Network network;

    protected final MatrixFactory matrixFactory;

    protected final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected final StateMonitorIndex monitorIndex;

    protected final ReportNode reportNode;

    private static final String NOT_FOUND = "' not found in the network";

    protected AbstractSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                       List<StateMonitor> stateMonitors, ReportNode reportNode) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.monitorIndex = new StateMonitorIndex(stateMonitors);
        this.reportNode = Objects.requireNonNull(reportNode);
    }

    protected static SecurityAnalysisResult createNoResult() {
        return new SecurityAnalysisResult(new LimitViolationsResult(Collections.emptyList()), LoadFlowResult.ComponentResult.Status.FAILED, Collections.emptyList());
    }

    public CompletableFuture<SecurityAnalysisReport> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters,
                                                         ContingenciesProvider contingenciesProvider, ComputationManager computationManager,
                                                         List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFutureTask.runAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingVariantId);
            return runSync(securityAnalysisParameters, contingenciesProvider, operatorStrategies, actions);
        }, computationManager.getExecutor());
    }

    protected abstract ReportNode createSaRootReportNode();

    protected abstract boolean isShuntCompensatorVoltageControlOn(LoadFlowParameters lfParameters);

    protected abstract P createParameters(LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers);

    SecurityAnalysisReport runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        var saReportNode = createSaRootReportNode();

        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        OpenSecurityAnalysisParameters securityAnalysisParametersExt = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);

        // check actions validity
        checkActions(network, actions);

        // try for find all switches to be operated as actions.
        LfTopoConfig topoConfig = new LfTopoConfig();
        findAllSwitchesToOperate(network, actions, topoConfig);

        // try to find all ptc and rtc to retain because involved in ptc and rtc actions
        findAllPtcToOperate(actions, topoConfig);
        findAllRtcToOperate(actions, topoConfig);
        // try to find all shunts which section can change through actions.
        findAllShuntsToOperate(actions, topoConfig);

        // try to find branches (lines and two windings transformers).
        // tie lines and three windings transformers missing.
        findAllBranchesToClose(network, actions, topoConfig);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);
        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setContingencyPropagation(securityAnalysisParametersExt.isContingencyPropagation())
                .setShuntCompensatorVoltageControlOn(isShuntCompensatorVoltageControlOn(lfParameters))
                .setSlackDistributionOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setHvdcAcEmulation(lfParameters.isHvdcAcEmulation());

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);

        var parameters = createParameters(lfParameters, lfParametersExt, topoConfig.isBreaker());

        // create networks including all necessary switches
        try (LfNetworkList lfNetworks = Networks.load(network, parameters.getNetworkParameters(), topoConfig, saReportNode)) {
            // run simulation on largest network
            SecurityAnalysisResult result = lfNetworks.getLargest().filter(n -> n.getValidity() == LfNetwork.Validity.VALID)
                    .map(largestNetwork -> runSimulations(largestNetwork, propagatedContingencies, parameters, securityAnalysisParameters, operatorStrategies, actions))
                    .orElse(createNoResult());

            stopwatch.stop();
            LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                    stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return new SecurityAnalysisReport(result);
        }
    }

    protected abstract PostContingencyComputationStatus postContingencyStatusFromLoadFlowResult(R result);

    protected static void checkActions(Network network, List<Action> actions) {
        for (Action action : actions) {
            switch (action.getType()) {
                case SwitchAction.NAME: {
                    SwitchAction switchAction = (SwitchAction) action;
                    if (network.getSwitch(switchAction.getSwitchId()) == null) {
                        throw new PowsyblException("Switch '" + switchAction.getSwitchId() + NOT_FOUND);
                    }
                    break;
                }

                case TerminalsConnectionAction.NAME: {
                    TerminalsConnectionAction terminalsConnectionAction = (TerminalsConnectionAction) action;
                    if (network.getBranch(terminalsConnectionAction.getElementId()) == null) {
                        throw new PowsyblException("Branch '" + terminalsConnectionAction.getElementId() + NOT_FOUND);
                    }
                    break;
                }

                case PhaseTapChangerTapPositionAction.NAME,
                     RatioTapChangerTapPositionAction.NAME: {
                    String transformerId = action.getType().equals(PhaseTapChangerTapPositionAction.NAME) ?
                            ((PhaseTapChangerTapPositionAction) action).getTransformerId() : ((RatioTapChangerTapPositionAction) action).getTransformerId();
                    if (network.getTwoWindingsTransformer(transformerId) == null
                            && network.getThreeWindingsTransformer(transformerId) == null) {
                        throw new PowsyblException("Transformer '" + transformerId + NOT_FOUND);
                    }
                    break;
                }

                case LoadAction.NAME: {
                    LoadAction loadAction = (LoadAction) action;
                    if (network.getLoad(loadAction.getLoadId()) == null) {
                        throw new PowsyblException("Load '" + loadAction.getLoadId() + NOT_FOUND);
                    }
                    break;
                }

                case GeneratorAction.NAME: {
                    GeneratorAction generatorAction = (GeneratorAction) action;
                    if (network.getGenerator(generatorAction.getGeneratorId()) == null) {
                        throw new PowsyblException("Generator '" + generatorAction.getGeneratorId() + NOT_FOUND);
                    }
                    break;
                }

                case HvdcAction.NAME: {
                    HvdcAction hvdcAction = (HvdcAction) action;
                    if (network.getHvdcLine(hvdcAction.getHvdcId()) == null) {
                        throw new PowsyblException("Hvdc line '" + hvdcAction.getHvdcId() + NOT_FOUND);
                    }
                    break;
                }

                case ShuntCompensatorPositionAction.NAME: {
                    ShuntCompensatorPositionAction shuntCompensatorPositionAction = (ShuntCompensatorPositionAction) action;
                    if (network.getShuntCompensator(shuntCompensatorPositionAction.getShuntCompensatorId()) == null) {
                        throw new PowsyblException("Shunt compensator '" + shuntCompensatorPositionAction.getShuntCompensatorId() + "' not found");
                    }
                    break;
                }

                default:
                    throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
            }
        }
    }

    protected static Map<String, LfAction> createLfActions(LfNetwork lfNetwork, Set<Action> actions, Network network, LfNetworkParameters parameters) {
        return actions.stream()
                .map(action -> LfAction.create(action, lfNetwork, network, parameters.isBreakers()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(LfAction::getId, Function.identity()));
    }

    protected static Map<String, Action> indexActionsById(List<Action> actions) {
        return actions.stream()
                .collect(Collectors.toMap(
                        Action::getId,
                        Function.identity(),
                    (action1, action2) -> {
                        throw new PowsyblException("An action '" + action1.getId() + "' already exist");
                    }
                ));
    }

    protected static Map<String, List<OperatorStrategy>> indexOperatorStrategiesByContingencyId(List<PropagatedContingency> propagatedContingencies,
                                                                                              List<OperatorStrategy> operatorStrategies,
                                                                                              Map<String, Action> actionsById,
                                                                                              Set<Action> neededActions) {
        Set<String> contingencyIds = propagatedContingencies.stream().map(propagatedContingency -> propagatedContingency.getContingency().getId()).collect(Collectors.toSet());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = new HashMap<>();
        for (OperatorStrategy operatorStrategy : operatorStrategies) {
            if (contingencyIds.contains(operatorStrategy.getContingencyContext().getContingencyId())) {
                // check actions IDs exists
                for (ConditionalActions conditionalActions : operatorStrategy.getConditionalActions()) {
                    for (String actionId : conditionalActions.getActionIds()) {
                        Action action = actionsById.get(actionId);
                        if (action == null) {
                            throw new PowsyblException("Operator strategy '" + operatorStrategy.getId() + "' is associated to action '"
                                    + actionId + "' but this action is not present in the list");
                        }
                        neededActions.add(action);
                    }
                }
                operatorStrategiesByContingencyId.computeIfAbsent(operatorStrategy.getContingencyContext().getContingencyId(), key -> new ArrayList<>())
                        .add(operatorStrategy);
            } else {
                throw new PowsyblException("Operator strategy '" + operatorStrategy.getId() + "' is associated to contingency '"
                        + operatorStrategy.getContingencyContext().getContingencyId() + "' but this contingency is not present in the list");
            }
        }
        return operatorStrategiesByContingencyId;
    }

    private static boolean checkCondition(ConditionalActions conditionalActions, Set<String> limitViolationEquipmentIds) {
        switch (conditionalActions.getCondition().getType()) {
            case TrueCondition.NAME:
                return true;
            case AnyViolationCondition.NAME:
                return !limitViolationEquipmentIds.isEmpty();
            case AtLeastOneViolationCondition.NAME: {
                AtLeastOneViolationCondition atLeastCondition = (AtLeastOneViolationCondition) conditionalActions.getCondition();
                Set<String> commonEquipmentIds = atLeastCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return !commonEquipmentIds.isEmpty();
            }
            case AllViolationCondition.NAME: {
                AllViolationCondition allCondition = (AllViolationCondition) conditionalActions.getCondition();
                Set<String> commonEquipmentIds = allCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return commonEquipmentIds.equals(new HashSet<>(allCondition.getViolationIds()));
            }
            default:
                throw new UnsupportedOperationException("Unsupported condition type: " + conditionalActions.getCondition().getType());
        }
    }

    protected List<String> checkCondition(OperatorStrategy operatorStrategy, LimitViolationsResult limitViolationsResult) {
        Set<String> limitViolationEquipmentIds = limitViolationsResult.getLimitViolations().stream()
                .map(LimitViolation::getSubjectId)
                .collect(Collectors.toSet());
        List<String> actionsIds = new ArrayList<>();
        for (ConditionalActions conditionalActions : operatorStrategy.getConditionalActions()) {
            if (checkCondition(conditionalActions, limitViolationEquipmentIds)) {
                actionsIds.addAll(conditionalActions.getActionIds());
            }
        }
        return actionsIds;
    }

    protected static void findAllSwitchesToOperate(Network network, List<Action> actions, LfTopoConfig topoConfig) {
        actions.stream().filter(action -> action.getType().equals(SwitchAction.NAME))
                .forEach(action -> {
                    String switchId = ((SwitchAction) action).getSwitchId();
                    Switch sw = network.getSwitch(switchId);
                    boolean toOpen = ((SwitchAction) action).isOpen();
                    if (sw.isOpen() && !toOpen) { // the switch is open and the action will close it.
                        topoConfig.getSwitchesToClose().add(sw);
                    } else if (!sw.isOpen() && toOpen) { // the switch is closed and the action will open it.
                        topoConfig.getSwitchesToOpen().add(sw);
                    }
                });
    }

    protected static void findAllPtcToOperate(List<Action> actions, LfTopoConfig topoConfig) {
        for (Action action : actions) {
            if (PhaseTapChangerTapPositionAction.NAME.equals(action.getType())) {
                PhaseTapChangerTapPositionAction ptcAction = (PhaseTapChangerTapPositionAction) action;
                ptcAction.getSide().ifPresentOrElse(
                        side -> topoConfig.addBranchIdWithPtcToRetain(LfLegBranch.getId(side, ptcAction.getTransformerId())), // T3WT
                        () -> topoConfig.addBranchIdWithPtcToRetain(ptcAction.getTransformerId()) // T2WT
                );
            }
        }
    }

    protected static void findAllRtcToOperate(List<Action> actions, LfTopoConfig topoConfig) {
        for (Action action : actions) {
            if (RatioTapChangerTapPositionAction.NAME.equals(action.getType())) {
                RatioTapChangerTapPositionAction rtcAction = (RatioTapChangerTapPositionAction) action;
                rtcAction.getSide().ifPresentOrElse(
                        side -> topoConfig.addBranchIdWithRtcToRetain(LfLegBranch.getId(side, rtcAction.getTransformerId())), // T3WT
                        () -> topoConfig.addBranchIdWithRtcToRetain(rtcAction.getTransformerId()) // T2WT
                );
            }
        }
    }

    protected static void findAllShuntsToOperate(List<Action> actions, LfTopoConfig topoConfig) {
        actions.stream().filter(action -> action.getType().equals(ShuntCompensatorPositionAction.NAME))
                .forEach(action -> topoConfig.addShuntIdToOperate(((ShuntCompensatorPositionAction) action).getShuntCompensatorId()));
    }

    protected static void findAllBranchesToClose(Network network, List<Action> actions, LfTopoConfig topoConfig) {
        // only branches open at both side or open at one side are visible in the LfNetwork.
        for (Action action : actions) {
            if (TerminalsConnectionAction.NAME.equals(action.getType())) {
                TerminalsConnectionAction terminalsConnectionAction = (TerminalsConnectionAction) action;
                if (terminalsConnectionAction.getSide().isEmpty() && !terminalsConnectionAction.isOpen()) {
                    Branch branch = network.getBranch(terminalsConnectionAction.getElementId());
                    if (branch != null && !(branch instanceof TieLine) &&
                            !branch.getTerminal1().isConnected() && !branch.getTerminal2().isConnected()) {
                        // both terminals must be disconnected. If only one is connected, the branch is present
                        // in the Lf network.
                        topoConfig.getBranchIdsToClose().add(terminalsConnectionAction.getElementId());
                    }
                }
            }
        }
    }

    protected static void distributedMismatch(LfNetwork network, double mismatch, LoadFlowParameters loadFlowParameters,
                                           OpenLoadFlowParameters openLoadFlowParameters) {
        if (loadFlowParameters.isDistributedSlack() && Math.abs(mismatch) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant(), openLoadFlowParameters.isUseActiveLimits());
            activePowerDistribution.run(network, mismatch);
        }
    }

    protected abstract C createLoadFlowContext(LfNetwork lfNetwork, P parameters);

    protected abstract LoadFlowEngine<V, E, P, R> createLoadFlowEngine(C context);

    protected void afterPreContingencySimulation(P acParameters) {
    }

    protected SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies, P acParameters,
                                                    SecurityAnalysisParameters securityAnalysisParameters, List<OperatorStrategy> operatorStrategies,
                                                    List<Action> actions) {
        Map<String, Action> actionsById = indexActionsById(actions);
        Set<Action> neededActions = new HashSet<>(actionsById.size());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = indexOperatorStrategiesByContingencyId(propagatedContingencies, operatorStrategies, actionsById, neededActions);
        Map<String, LfAction> lfActionById = createLfActions(lfNetwork, neededActions, network, acParameters.getNetworkParameters()); // only convert needed actions

        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (C context = createLoadFlowContext(lfNetwork, acParameters)) {
            ReportNode networkReportNode = lfNetwork.getReportNode();
            ReportNode preContSimReportNode = Reports.createPreContingencySimulation(networkReportNode);
            lfNetwork.setReportNode(preContSimReportNode);

            // run pre-contingency simulation
            R preContingencyLoadFlowResult = createLoadFlowEngine(context)
                    .run();

            boolean preContingencyComputationOk = preContingencyLoadFlowResult.isSuccess();
            var preContingencyLimitViolationManager = new LimitViolationManager();
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            if (preContingencyComputationOk) {
                afterPreContingencySimulation(acParameters);

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
                    propagatedContingency.toLfContingency(lfNetwork)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfContingency.getId());
                                lfNetwork.setReportNode(postContSimReportNode);

                                lfContingency.apply(loadFlowParameters.getBalanceType());

                                distributedMismatch(lfNetwork, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

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
                                                acParameters.getNetworkParameters())
                                                .ifPresent(operatorStrategyResults::add);
                                    } else {
                                        // save post contingency state for later restoration after action
                                        NetworkState postContingencyNetworkState = NetworkState.save(lfNetwork);
                                        for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
                                            runActionSimulation(lfNetwork, context,
                                                    operatorStrategy, preContingencyLimitViolationManager,
                                                    securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
                                                    createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
                                                    acParameters.getNetworkParameters())
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
                            preContingencyLoadFlowResult.toComponentResultStatus(),
                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
                    postContingencyResults, operatorStrategyResults);
        }
    }

    private Optional<OperatorStrategyResult> runActionSimulation(LfNetwork network, C context, OperatorStrategy operatorStrategy,
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

    protected PostContingencyResult runPostContingencySimulation(LfNetwork network, C context, Contingency contingency, LfContingency lfContingency,
                                                                 LimitViolationManager preContingencyLimitViolationManager,
                                                                 SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                 PreContingencyNetworkResult preContingencyNetworkResult, boolean createResultExtension) {
        LOGGER.info("Start post contingency '{}' simulation on network {}", lfContingency.getId(), network);
        LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift {} loads",
                lfContingency.getId(), network, lfContingency.getDisabledNetwork().getBuses(), lfContingency.getDisabledNetwork().getBranchesStatus(),
                lfContingency.getLostGenerators(), lfContingency.getShuntsShift(), lfContingency.getLostLoads());

        Stopwatch stopwatch = Stopwatch.createStarted();

        // restart LF on post contingency equation system
        PostContingencyComputationStatus status = runActionLoadFlow(context); // FIXME: change name.
        var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, violationsParameters);
        var postContingencyNetworkResult = new PostContingencyNetworkResult(network, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency);

        if (status.equals(PostContingencyComputationStatus.CONVERGED)) {
            // update network result
            postContingencyNetworkResult.update();

            // detect violations
            postContingencyLimitViolationManager.detectViolations(network);
        }

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done on network {} in {} ms", lfContingency.getId(),
                network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

        var connectivityResult = new ConnectivityResult(lfContingency.getCreatedSynchronousComponentsCount(), 0,
                lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                lfContingency.getDisconnectedElementIds());

        return new PostContingencyResult(contingency, status,
                new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                postContingencyNetworkResult.getBranchResults(),
                postContingencyNetworkResult.getBusResults(),
                postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                connectivityResult);
    }

    protected OperatorStrategyResult runActionSimulation(LfNetwork network, C context, OperatorStrategy operatorStrategy,
                                                         List<String> actionsIds,
                                                         LimitViolationManager preContingencyLimitViolationManager,
                                                         SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                         Map<String, LfAction> lfActionById, boolean createResultExtension, LfContingency contingency,
                                                         LfNetworkParameters networkParameters) {
        LOGGER.info("Start operator strategy {} after contingency '{}' simulation on network {}", operatorStrategy.getId(),
                operatorStrategy.getContingencyContext().getContingencyId(), network);

        // get LF action for this operator strategy, as all actions have been previously checked against IIDM
        // network, an empty LF action means it is for another component (so another LF network) so we can
        // skip it
        List<LfAction> operatorStrategyLfActions = actionsIds.stream()
                .map(lfActionById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LfAction.apply(operatorStrategyLfActions, network, contingency, networkParameters);

        Stopwatch stopwatch = Stopwatch.createStarted();

        // restart LF on post contingency and post actions equation system
        PostContingencyComputationStatus status = runActionLoadFlow(context);
        var postActionsViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, violationsParameters);
        var postActionsNetworkResult = new PreContingencyNetworkResult(network, monitorIndex, createResultExtension);

        if (status.equals(PostContingencyComputationStatus.CONVERGED)) {
            // update network result
            postActionsNetworkResult.update();

            // detect violations
            postActionsViolationManager.detectViolations(network);
        }

        stopwatch.stop();

        LOGGER.info("Operator strategy {} after contingency '{}' simulation done on network {} in {} ms", operatorStrategy.getId(),
                operatorStrategy.getContingencyContext().getContingencyId(), network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new OperatorStrategyResult(operatorStrategy, status,
                                          new LimitViolationsResult(postActionsViolationManager.getLimitViolations()),
                                          new NetworkResult(postActionsNetworkResult.getBranchResults(),
                                                            postActionsNetworkResult.getBusResults(),
                                                            postActionsNetworkResult.getThreeWindingsTransformerResults()));
    }

    protected void beforeActionLoadFlowRun(C context) {
    }

    protected PostContingencyComputationStatus runActionLoadFlow(C context) {
        beforeActionLoadFlowRun(context);
        R result = createLoadFlowEngine(context).run();
        return postContingencyStatusFromLoadFlowResult(result);
    }
}
