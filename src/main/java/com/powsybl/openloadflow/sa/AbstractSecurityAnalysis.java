/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.security.*;
import com.powsybl.security.action.*;
import com.powsybl.security.condition.AllViolationCondition;
import com.powsybl.security.condition.AnyViolationCondition;
import com.powsybl.security.condition.AtLeastOneViolationCondition;
import com.powsybl.security.condition.TrueCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.OperatorStrategyResult;
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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSecurityAnalysis<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity,
                                               P extends AbstractLoadFlowParameters,
                                               C extends LoadFlowContext<V, E, P>> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSecurityAnalysis.class);

    protected final Network network;

    protected final MatrixFactory matrixFactory;

    protected final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected final StateMonitorIndex monitorIndex;

    protected final Reporter reporter;

    private static final String NOT_FOUND = "' not found in the network";

    protected AbstractSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                       List<StateMonitor> stateMonitors, Reporter reporter) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.monitorIndex = new StateMonitorIndex(stateMonitors);
        this.reporter = Objects.requireNonNull(reporter);
    }

    public CompletableFuture<SecurityAnalysisReport> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters,
                                                         ContingenciesProvider contingenciesProvider, ComputationManager computationManager,
                                                         List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFutureTask.runAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingVariantId);
            return runSync(workingVariantId, securityAnalysisParameters, contingenciesProvider, computationManager, operatorStrategies, actions);
        }, computationManager.getExecutor());
    }

    abstract SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                            ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions);

    public static PostContingencyComputationStatus postContingencyStatusFromNRStatus(NewtonRaphsonStatus status) {
        return switch (status) {
            case CONVERGED -> PostContingencyComputationStatus.CONVERGED;
            case MAX_ITERATION_REACHED -> PostContingencyComputationStatus.MAX_ITERATION_REACHED;
            case SOLVER_FAILED -> PostContingencyComputationStatus.SOLVER_FAILED;
            case NO_CALCULATION -> PostContingencyComputationStatus.NO_IMPACT;
            case UNREALISTIC_STATE -> PostContingencyComputationStatus.FAILED;
        };
    }

    public static LoadFlowResult.ComponentResult.Status loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus status) {
        return switch (status) {
            case CONVERGED -> LoadFlowResult.ComponentResult.Status.CONVERGED;
            case MAX_ITERATION_REACHED -> LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
            case SOLVER_FAILED -> LoadFlowResult.ComponentResult.Status.SOLVER_FAILED;
            case NO_CALCULATION, UNREALISTIC_STATE -> LoadFlowResult.ComponentResult.Status.FAILED;
        };
    }

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

                case LineConnectionAction.NAME: {
                    LineConnectionAction lineConnectionAction = (LineConnectionAction) action;
                    if (network.getBranch(lineConnectionAction.getLineId()) == null && network.getTieLine(lineConnectionAction.getLineId()) == null) {
                        throw new PowsyblException("Branch '" + lineConnectionAction.getLineId() + NOT_FOUND);
                    }
                    break;
                }

                case PhaseTapChangerTapPositionAction.NAME: {
                    PhaseTapChangerTapPositionAction phaseTapChangerTapPositionAction = (PhaseTapChangerTapPositionAction) action;
                    if (network.getTwoWindingsTransformer(phaseTapChangerTapPositionAction.getTransformerId()) == null
                            && network.getThreeWindingsTransformer(phaseTapChangerTapPositionAction.getTransformerId()) == null) {
                        throw new PowsyblException("Transformer '" + phaseTapChangerTapPositionAction.getTransformerId() + NOT_FOUND);
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

    protected static List<RatioTapChangerHolder> findAllRtcToOperate(Network network, List<Action> actions) {
        List<RatioTapChangerHolder> rtcToOperate = new ArrayList<>();
        for (Action action : actions) {
            if (Objects.equals(action.getType(), "RATIO_TAP_CHANGER_TAP_POSITION")) {
                RatioTapChangerTapPositionAction rtcAction = (RatioTapChangerTapPositionAction) action;
                rtcAction.getSide().ifPresentOrElse(side -> {
                    // This is a T3WT
                    ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer(rtcAction.getTransformerId());
                    if (t3wt != null) {
                        rtcToOperate.add(t3wt.getLeg(side));
                    }
                }, () -> {
                    // This is a T2WT
                    TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer(rtcAction.getTransformerId());
                    if (t2wt != null) {
                        rtcToOperate.add(t2wt);
                    }
                });
            }
        }
        return rtcToOperate;
    }

    protected static List<PhaseTapChangerHolder> findAllPstToOperate(Network network, List<Action> actions) {
        List<PhaseTapChangerHolder> pstToOperate = new ArrayList<>();
        for (Action action : actions) {
            if (Objects.equals(action.getType(), "PHASE_TAP_CHANGER_TAP_POSITION")) {
                PhaseTapChangerTapPositionAction ptcAction = (PhaseTapChangerTapPositionAction) action;
                ptcAction.getSide().ifPresentOrElse(side -> {
                    // This is a T3WT
                    ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer(ptcAction.getTransformerId());
                    if (t3wt != null) {
                        pstToOperate.add(t3wt.getLeg(side));
                    }
                }, () -> {
                    // This is a T2WT
                    TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer(ptcAction.getTransformerId());
                    if (t2wt != null) {
                        pstToOperate.add(t2wt);
                    }
                });
            }
        }
        return pstToOperate;
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

    protected abstract PostContingencyComputationStatus runActionLoadFlow(C context);
}
