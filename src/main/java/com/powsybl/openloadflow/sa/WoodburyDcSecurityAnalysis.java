/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.action.Action;
import com.powsybl.action.PhaseTapChangerTapPositionAction;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide2DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.dc.fastdc.*;
import com.powsybl.openloadflow.dc.fastdc.ConnectivityBreakAnalysis.ConnectivityAnalysisResult;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.dc.fastdc.ComputedContingencyElement;
import com.powsybl.openloadflow.dc.fastdc.ConnectivityBreakAnalysis;
import com.powsybl.openloadflow.dc.fastdc.WoodburyEngine;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.limitreduction.LimitReduction;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.updateNetwork;
import static com.powsybl.openloadflow.network.impl.PropagatedContingency.cleanContingencies;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyDcSecurityAnalysis extends DcSecurityAnalysis {

    protected WoodburyDcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                         List<StateMonitor> stateMonitors, ReportNode reportNode) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reportNode);
    }

    @Override
    protected ReportNode createSaRootReportNode() {
        return Reports.createWoodburyDcSecurityAnalysis(reportNode, network.getId());
    }

    @Override
    protected DcLoadFlowParameters createParameters(LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers) {
        DcLoadFlowParameters dcParameters = super.createParameters(lfParameters, lfParametersExt, breakers);
        // connectivity break analysis does not handle zero impedance lines
        dcParameters.getNetworkParameters().setMinImpedance(true);
        // needed an equation to force angle to zero when a PST is lost
        dcParameters.getEquationSystemCreationParameters().setForcePhaseControlOffAndAddAngle1Var(true);
        return dcParameters;
    }

    /**
     * Calculate post contingency states for a contingency.
     * In case of connectivity break, a pre-computation has been done in {@link #calculatePostContingencyStatesForAContingencyBreakingConnectivity}
     * to reset active power flow of hvdc lines on which one bus is lost.
     * If connectivity, a generator, a load or a phase tap changer is lost due to the contingency, the pre contingency flowStates are overridden.
     * @return the post contingency states for the contingency
     */
    private double[] calculatePostContingencyStates(DcLoadFlowContext loadFlowContext, DenseMatrix contingenciesStates, double[] flowStates,
                                                    PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                    Set<LfBus> disabledBuses, Set<String> elementsToReconnect, Set<LfBranch> partialDisabledBranches, ReportNode reportNode) {
        return calculatePostContingencyAndOperatorStrategyStates(loadFlowContext, contingenciesStates, flowStates, contingency, contingencyElementByBranch, disabledBuses, elementsToReconnect,
                partialDisabledBranches, Collections.emptyList(), Collections.emptyMap(), DenseMatrix.EMPTY, reportNode);
    }

    /**
     * Calculate post contingency and post operator strategy states, for a contingency and operator strategy actions.
     * In case of connectivity break, a pre-computation has been done in {@link #calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity}
     * to reset active power flow of hvdc lines on which one bus is lost.
     * If connectivity, a generator, a load or a phase tap changer is lost/modified due to the contingency/operator strategy, the pre contingency flowStates are overridden.
     */
    private double[] calculatePostContingencyAndOperatorStrategyStates(DcLoadFlowContext loadFlowContext, DenseMatrix contingenciesStates, double[] flowStates,
                                                                       PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                       Set<LfBus> disabledBuses, Set<String> elementsToReconnect, Set<LfBranch> partialDisabledBranches,
                                                                       List<LfAction> operatorStrategyLfActions, Map<String, ComputedTapPositionChangeElement> tapPositionChangeElementByBranch,
                                                                       DenseMatrix actionsStates, ReportNode reportNode) {
        List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(contingencyElementByBranch::get)
                .toList();
        List<ComputedTapPositionChangeElement> actionElements = operatorStrategyLfActions.stream()
                .map(lfAction -> lfAction.getTapPositionChange().getBranch().getId())
                .map(tapPositionChangeElementByBranch::get)
                .toList();

        var lfNetwork = loadFlowContext.getNetwork();
        Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
        disabledBranches.addAll(partialDisabledBranches);
        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);

        WoodburyEngine engine = new WoodburyEngine(loadFlowContext.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates, actionElements, actionsStates);
        double[] newFlowStates = flowStates;
        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLose().isEmpty()) {

            // get the lost phase tap changers for this contingency
            Set<LfBranch> lostPhaseControllers = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .map(ComputedContingencyElement::getLfBranch)
                    .filter(LfBranch::hasPhaseControllerCapability)
                    .collect(Collectors.toSet());

            // if a phase tap changer is lost or if the connectivity have changed, we must recompute load flows
            // same if there is an action, as they are only on pst for now
            if (!disabledBuses.isEmpty() || !lostPhaseControllers.isEmpty() || !operatorStrategyLfActions.isEmpty()) {
                newFlowStates = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(loadFlowContext, disabledNetwork, reportNode, operatorStrategyLfActions);
            }
            engine.toPostContingencyAndOperatorStrategyStates(newFlowStates);
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            // FIXME change runDcLoadFlowWithModifiedTargetVector to support disables loads and generators
            newFlowStates = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(loadFlowContext, disabledNetwork, reportNode, operatorStrategyLfActions);
            engine.toPostContingencyAndOperatorStrategyStates(newFlowStates);
        }

        return newFlowStates;
    }

    /**
     * Calculate post contingency states for a contingency breaking connectivity.
     */
    private double[] calculatePostContingencyStatesForAContingencyBreakingConnectivity(ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                                                       Map<String, ComputedContingencyElement> contingencyElementByBranch, double[] flowStates, DenseMatrix contingenciesStates,
                                                                                       ReportNode reportNode) {
        return calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, contingencyElementByBranch, flowStates,
                contingenciesStates, Collections.emptyList(), Collections.emptyMap(), DenseMatrix.EMPTY, reportNode);
    }

    /**
     * Calculate post contingency and post operator strategy states, for a contingency breaking connectivity.
     */
    private double[] calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity(ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                                                                          Map<String, ComputedContingencyElement> contingencyElementByBranch, double[] flowStates, DenseMatrix contingenciesStates,
                                                                                                          List<LfAction> operatorStrategyLfActions, Map<String, ComputedTapPositionChangeElement> tapPositionChangeElementByBranch, DenseMatrix actionsStates, ReportNode reportNode) {

        PropagatedContingency contingency = connectivityAnalysisResult.getPropagatedContingency();
        Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();

        // as we are processing a contingency with connectivity break, we have to reset active power flow of a hvdc line
        // if one bus of the line is lost.
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
            }
        }

        return calculatePostContingencyAndOperatorStrategyStates(loadFlowContext, contingenciesStates, flowStates,
                contingency, contingencyElementByBranch, disabledBuses, connectivityAnalysisResult.getElementsToReconnect(),
                connectivityAnalysisResult.getPartialDisabledBranches(), operatorStrategyLfActions, tapPositionChangeElementByBranch, actionsStates, reportNode);
    }

    private void filterActions(List<Action> actions) {
        actions.stream()
                .filter(action -> !(action instanceof PhaseTapChangerTapPositionAction))
                .findAny()
                .ifPresent(e -> {
                    throw new IllegalStateException("For now, only PhaseTapChangerTapPositionAction is allowed in WoodburyDcSecurityAnalysis");
                });
    }

    /**
     * Returns the post contingency result associated to given contingency and post contingency states.
     */
    private PostContingencyResult computePostContingencyResultFromPostContingencyStates(DcLoadFlowContext loadFlowContext, ConnectivityAnalysisResult connectivityAnalysisResult,
                                                                                        LimitViolationManager preContingencyLimitViolationManager, PreContingencyNetworkResult preContingencyNetworkResult,
                                                                                        boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                                        double[] postContingencyStates, List<LimitReduction> limitReductions) {

        // update network state with post contingency states
        LfNetwork lfNetwork = loadFlowContext.getNetwork();

        // update post contingency network result
        // FIXME to implement without getting flow in the network
        PropagatedContingency propagatedContingency = connectivityAnalysisResult.getPropagatedContingency();
        var postContingencyNetworkResult = new PostContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension, preContingencyNetworkResult, propagatedContingency.getContingency());
//        postContingencyNetworkResult.update();

        // detect violations
        boolean[] disabled = new boolean[lfNetwork.getBranches().size()];
        double[] p1 = new double[lfNetwork.getBranches().size()];
        double[] i1 = new double[lfNetwork.getBranches().size()];
        double[] p2 = new double[lfNetwork.getBranches().size()];
        double[] i2 = new double[lfNetwork.getBranches().size()];
        double dcPowerFactor = loadFlowContext.getParameters().getEquationSystemCreationParameters().getDcPowerFactor();

        StateVector sv = new StateVector(postContingencyStates);
        computeFlows(lfNetwork, propagatedContingency, sv, disabled, p1, i1, p2, i2, dcPowerFactor);

        var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions, violationsParameters);
        postContingencyLimitViolationManager.detectBranchesViolations(lfNetwork,
                branch -> disabled[branch.getNum()],
                branch -> i1[branch.getNum()],
                branch -> p1[branch.getNum()],
                branch -> {
                    throw new PowsyblException("s1 useless");
                },
                branch -> i2[branch.getNum()],
                branch -> p2[branch.getNum()],
                branch -> {
                    throw new PowsyblException("s2 useless");
                });

        var connectivityResult = createConnectivityResult(connectivityAnalysisResult, lfNetwork);

        return new PostContingencyResult(propagatedContingency.getContingency(),
                PostContingencyComputationStatus.CONVERGED,
                new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                postContingencyNetworkResult.getBranchResults(),
                postContingencyNetworkResult.getBusResults(),
                postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                connectivityResult);
    }

    private static ConnectivityResult createConnectivityResult(ConnectivityAnalysisResult connectivityAnalysisResult, LfNetwork lfNetwork) {
        PropagatedContingency propagatedContingency = connectivityAnalysisResult.getPropagatedContingency();

        // load and generation lost directly by the contingency
        double disconnectedLoadActivePower = propagatedContingency.getLoadIdsToLose().entrySet().stream().mapToDouble(e ->  {
            LfLoad load = lfNetwork.getLoadById(e.getKey());
            return load != null ? e.getValue().getActive(): 0;
        }).sum();
        double disconnectedGenerationActivePower = propagatedContingency.getGeneratorIdsToLose().stream().mapToDouble(generatorId ->  {
            LfGenerator gen = lfNetwork.getGeneratorById(generatorId);
            return gen != null ? gen.getTargetP(): 0;
        }).sum();
        Set<String> disconnectedElements = new HashSet<>();
        disconnectedElements.addAll(propagatedContingency.getGeneratorIdsToLose());
        disconnectedElements.addAll(propagatedContingency.getLoadIdsToLose().keySet());
        disconnectedElements.addAll(propagatedContingency.getBranchIdsToOpen().keySet());

        // load and generation lost indirectly by propagation
        for (LfBus bus : connectivityAnalysisResult.getDisabledBuses()) {
            for (LfLoad load : bus.getLoads()) {
                disconnectedLoadActivePower += load.getTargetP();
                disconnectedElements.addAll(load.getOriginalIds());
            }
            for (LfGenerator generator : bus.getGenerators()) {
                disconnectedGenerationActivePower += generator.getTargetP();
                disconnectedElements.add(generator.getOriginalId());
            }
        }
        for (LfBranch branch : connectivityAnalysisResult.getPartialDisabledBranches()) {
            disconnectedElements.addAll(branch.getOriginalIds());
        }

        // FIXME we need to detail of created components => to add in ConnectivityAnalysisResult
        return new ConnectivityResult(
                0, 0,
                disconnectedLoadActivePower,
                disconnectedGenerationActivePower,
                disconnectedElements);
    }

    private static void computeFlows(LfNetwork lfNetwork, PropagatedContingency propagatedContingency, StateVector sv, boolean[] disabled, double[] p1, double[] i1, double[] p2, double[] i2, double dcPowerFactor) {
        Arrays.fill(disabled, false);
        for (String disabledBranchId : propagatedContingency.getBranchIdsToOpen().keySet()) {
            LfBranch disabledBranch = lfNetwork.getBranchById(disabledBranchId);
            disabled[disabledBranch.getNum()] = true;
        }
        for (LfBranch branch : lfNetwork.getBranches()) {
            p1[branch.getNum()] = branch.getP1() instanceof  ClosedBranchSide1DcFlowEquationTerm ? ((ClosedBranchSide1DcFlowEquationTerm) branch.getP1()).eval(sv) : Double.NaN;
            i1[branch.getNum()] = Math.abs(p1[branch.getNum()]) / dcPowerFactor;
            p2[branch.getNum()] = branch.getP2() instanceof  ClosedBranchSide2DcFlowEquationTerm ? ((ClosedBranchSide2DcFlowEquationTerm) branch.getP2()).eval(sv) : Double.NaN;
            i2[branch.getNum()] = Math.abs(p2[branch.getNum()]) / dcPowerFactor;
        }
    }

    /**
     * Returns post contingency result associated to the given contingency, with given supplier of post contingency states.
     */
    private PostContingencyResult processPostContingencyResult(DcLoadFlowContext context, ConnectivityAnalysisResult connectivityAnalysisResult, Supplier<double[]> postContingencyStatesSupplier,
                                                               LimitViolationManager preContingencyLimitViolationManager, PreContingencyNetworkResult preContingencyNetworkResult,
                                                               boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                               List<LimitReduction> limitReductions) {
        double[] postContingencyStates = postContingencyStatesSupplier.get();
        PostContingencyResult postContingencyResult = computePostContingencyResultFromPostContingencyStates(context, connectivityAnalysisResult,
                preContingencyLimitViolationManager, preContingencyNetworkResult, createResultExtension,
                violationsParameters, postContingencyStates, limitReductions);

        return postContingencyResult;
    }

    /**
     * Returns the operator strategy result associated to the given post contingency and post operator strategy states.
     */
    private OperatorStrategyResult computeOperatorStrategyResultFromPostContingencyAndOperatorStrategyStates(DcLoadFlowContext loadFlowContext, PropagatedContingency propagatedContingency, OperatorStrategy operatorStrategy,
                                                                                                             List<LfAction> operatorStrategyLfActions, LimitViolationManager preContingencyLimitViolationManager,
                                                                                                             boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                                                             double[] postContingencyAndOperatorStrategyStates, List<LimitReduction> limitReductions) {
        // update network state with post contingency and post operator strategy states
        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        loadFlowContext.getEquationSystem().getStateVector().set(postContingencyAndOperatorStrategyStates);
        updateNetwork(lfNetwork, loadFlowContext.getEquationSystem(), postContingencyAndOperatorStrategyStates);

        // apply modifications to compute results
//        lfContingency.apply(loadFlowContext.getParameters().getBalanceType());
//        LfAction.apply(operatorStrategyLfActions, lfNetwork, lfContingency, loadFlowContext.getParameters().getNetworkParameters());
//
        // update network result
        var postActionsNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
        postActionsNetworkResult.update();
//
//        // detect violations
//        var postActionsViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions, violationsParameters);
//        postActionsViolationManager.detectViolations(lfNetwork);

        return new OperatorStrategyResult(operatorStrategy, PostContingencyComputationStatus.CONVERGED,
                new LimitViolationsResult(Collections.emptyList()),
                new NetworkResult(postActionsNetworkResult.getBranchResults(),
                        postActionsNetworkResult.getBusResults(),
                        postActionsNetworkResult.getThreeWindingsTransformerResults()));
    }

    /**
     * Returns operator strategy result associated to the given operator strategy, with given supplier of post contingency and post operator strategy states.
     */
    private OperatorStrategyResult processOperatorStrategyResult(DcLoadFlowContext context, PropagatedContingency propagatedContingency, OperatorStrategy operatorStrategy,
                                                                 Function<List<LfAction>, double[]> postContingencyAndOperatorStrategyStatesSupplier,
                                                                 LimitViolationManager preContingencyLimitViolationManager, PostContingencyResult postContingencyResult,
                                                                 Map<String, LfAction> lfActionById, boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                 List<LimitReduction> limitReductions) {
        // get the actions associated to the operator strategy
        List<String> actionIds = checkCondition(operatorStrategy, postContingencyResult.getLimitViolationsResult());
        List<LfAction> operatorStrategyLfActions = actionIds.stream()
                .map(lfActionById::get)
                .filter(Objects::nonNull)
                .toList();

        logActionStart(context.getNetwork(), operatorStrategy);
        Stopwatch stopwatch = Stopwatch.createStarted();

        double[] postContingencyAndOperatorStrategyStates = postContingencyAndOperatorStrategyStatesSupplier.apply(operatorStrategyLfActions);
        OperatorStrategyResult operatorStrategyResult = computeOperatorStrategyResultFromPostContingencyAndOperatorStrategyStates(context, propagatedContingency, operatorStrategy, operatorStrategyLfActions,
                preContingencyLimitViolationManager, createResultExtension, violationsParameters, postContingencyAndOperatorStrategyStates, limitReductions);

        stopwatch.stop();
        logActionEnd(context.getNetwork(), operatorStrategy, stopwatch);
        return operatorStrategyResult;
    }

    /**
     * Add the post contingency and operator strategy results, associated to given contingency, in the given list of results.
     * The post contingency and post operator strategy states are computed with given supplier and function.
     */
    private void addPostContingencyAndOperatorStrategyResults(DcLoadFlowContext context, ConnectivityAnalysisResult connectivityAnalysisResult, Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId,
                                                              Map<String, LfAction> lfActionById, Supplier<double[]> toPostContingencyStates, Function<List<LfAction>, double[]> toPostContingencyAndOperatorStrategyStates,
                                                              Runnable restorePreContingencyStates, LimitViolationManager preContingencyLimitViolationManager, PreContingencyNetworkResult preContingencyNetworkResult,
                                                              boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters, List<LimitReduction> limitReductions,
                                                              List<PostContingencyResult> postContingencyResults, List<OperatorStrategyResult> operatorStrategyResults) {
        LfNetwork lfNetwork = context.getNetwork();

        // process results only if contingency impacts the network
        if (connectivityAnalysisResult.getPropagatedContingency().hasNoImpact()) {
            return;
        }

        PropagatedContingency propagatedContingency = connectivityAnalysisResult.getPropagatedContingency();
        ReportNode postContSimReportNode = Reports.createPostContingencySimulation(lfNetwork.getReportNode(), propagatedContingency.getContingency().getId());
        lfNetwork.setReportNode(postContSimReportNode);

        // process post contingency result with supplier giving post contingency states
        PostContingencyResult postContingencyResult = processPostContingencyResult(context, connectivityAnalysisResult, toPostContingencyStates, preContingencyLimitViolationManager,
                preContingencyNetworkResult, createResultExtension, violationsParameters, limitReductions);
        postContingencyResults.add(postContingencyResult);

        // restore pre contingency states for next calculation
        restorePreContingencyStates.run();

        List<OperatorStrategy> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId.get(propagatedContingency.getContingency().getId());
        if (operatorStrategiesForThisContingency != null) {
            for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
                ReportNode osSimReportNode = Reports.createOperatorStrategySimulation(postContSimReportNode, operatorStrategy.getId());
                lfNetwork.setReportNode(osSimReportNode);

                // process operator strategy result with supplier giving post contingency and post operator strategy states
                OperatorStrategyResult operatorStrategyResult = processOperatorStrategyResult(context, propagatedContingency, operatorStrategy, toPostContingencyAndOperatorStrategyStates, preContingencyLimitViolationManager,
                        postContingencyResult, lfActionById, createResultExtension, violationsParameters, limitReductions);
                operatorStrategyResults.add(operatorStrategyResult);

                // restore pre contingency states for next calculation
                restorePreContingencyStates.run();
            }
        }
    }

    private static Map<String, ComputedTapPositionChangeElement> createTapPositionChangeElementsIndexByBranchId(Map<String, LfAction> lfActionById, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        Map<String, ComputedTapPositionChangeElement> computedTapPositionChangeElements = lfActionById.values().stream()
                .filter(lfAction -> lfAction.getTapPositionChange() != null)
                .map(lfAction -> new ComputedTapPositionChangeElement(lfAction.getTapPositionChange(), equationSystem))
                .filter(computedTapPositionChangeElement -> computedTapPositionChangeElement.getLfBranchEquation() != null)
                .collect(Collectors.toMap(
                        computedTapPositionChangeElement -> computedTapPositionChangeElement.getLfBranch().getId(),
                        computedTapPositionChangeElement -> computedTapPositionChangeElement,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
        ComputedElement.setComputedElementIndexes(computedTapPositionChangeElements.values());
        return computedTapPositionChangeElements;
    }

    @Override
    protected SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies, DcLoadFlowParameters dcParameters,
                                                    SecurityAnalysisParameters securityAnalysisParameters, List<OperatorStrategy> operatorStrategies,
                                                    List<Action> actions, List<LimitReduction> limitReductions) {
        // Verify only PST actions are given
        filterActions(actions);
        Map<String, Action> actionsById = indexActionsById(actions);
        Set<Action> neededActions = new HashSet<>(actionsById.size());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = indexOperatorStrategiesByContingencyId(propagatedContingencies, operatorStrategies, actionsById, neededActions);
        Map<String, LfAction> lfActionById = createLfActions(lfNetwork, neededActions, network, dcParameters.getNetworkParameters()); // only convert needed actions

        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters, false)) {
            ReportNode networkReportNode = lfNetwork.getReportNode();
            ReportNode preContSimReportNode = Reports.createPreContingencySimulation(networkReportNode);
            lfNetwork.setReportNode(preContSimReportNode);

            // prepare contingencies for connectivity analysis and Woodbury engine
            // note that contingencies on branches connected only on one side are removed,
            // this is a difference with dc security analysis
            cleanContingencies(lfNetwork, propagatedContingencies);

            // compute the pre-contingency states
            double[] preContingencyStates = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, new DisabledNetwork(), reportNode);
            // create workingContingencyStates that will be a working copy of pre-contingency states
            double[] workingContingencyStates = new double[preContingencyStates.length];
            System.arraycopy(preContingencyStates, 0, workingContingencyStates, 0, preContingencyStates.length);

            // set pre contingency angle states as state vector of equation system
            context.getEquationSystem().getStateVector().set(preContingencyStates);

            // Update network voltages with pre contingency states
            updateNetwork(lfNetwork, context.getEquationSystem(), preContingencyStates);
            if (context.getParameters().isSetVToNan()) {
                for (LfBus bus : lfNetwork.getBuses()) {
                    bus.setV(Double.NaN);
                }
            }

            // update network result
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
            preContingencyNetworkResult.update();

            // detect violations
            var preContingencyLimitViolationManager = new LimitViolationManager(limitReductions);
            preContingencyLimitViolationManager.detectViolations(lfNetwork);

            // compute states with +1 -1 to model the contingencies and run connectivity analysis
            ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityBreakAnalysisResults = ConnectivityBreakAnalysis.run(context, propagatedContingencies);

            // compute states with +1 -1 to model the actions in Woodbury engine
            Map<String, ComputedTapPositionChangeElement> tapPositionChangeElementsByBranchId = createTapPositionChangeElementsIndexByBranchId(lfActionById, context.getEquationSystem());
            DenseMatrix actionsStates = ComputedElement.calculateElementsStates(context, tapPositionChangeElementsByBranchId.values());

            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();

            LOGGER.info("Processing post contingency results for contingencies with no connectivity break");
            connectivityBreakAnalysisResults.nonBreakingConnectivityContingencies().forEach(nonBreakingConnectivityContingency -> {
                // supplier to compute post contingency states
                Supplier<double[]> toPostContingencyStates = () -> calculatePostContingencyStates(context, connectivityBreakAnalysisResults.contingenciesStates(), workingContingencyStates,
                        nonBreakingConnectivityContingency, connectivityBreakAnalysisResults.contingencyElementByBranch(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), reportNode);
                // function to compute post contingency and post operator strategy states
                Function<List<LfAction>, double[]> toPostContingencyAndOperatorStrategyStates = operatorStrategyLfActions -> calculatePostContingencyAndOperatorStrategyStates(context, connectivityBreakAnalysisResults.contingenciesStates(), workingContingencyStates,
                        nonBreakingConnectivityContingency, connectivityBreakAnalysisResults.contingencyElementByBranch(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                        operatorStrategyLfActions, tapPositionChangeElementsByBranchId, actionsStates, reportNode);
                // runnable to restore pre contingency states, after modifications applied to the lfNetwork
                Runnable restorePreContingencyStates = () -> {
                    // update workingContingencyStates as it may have been updated by post contingency states calculation
                    System.arraycopy(preContingencyStates, 0, workingContingencyStates, 0, preContingencyStates.length);
                };
                addPostContingencyAndOperatorStrategyResults(context, new ConnectivityAnalysisResult(nonBreakingConnectivityContingency), operatorStrategiesByContingencyId, lfActionById, toPostContingencyStates,
                        toPostContingencyAndOperatorStrategyStates, restorePreContingencyStates, preContingencyLimitViolationManager, preContingencyNetworkResult, createResultExtension,
                        securityAnalysisParameters.getIncreasedViolationsParameters(), limitReductions, postContingencyResults, operatorStrategyResults);
            });

            LOGGER.info("Processing post contingency results for contingencies breaking connectivity");
            connectivityBreakAnalysisResults.connectivityAnalysisResults()
                    .forEach(connectivityAnalysisResult -> {
                        // supplier to compute post contingency states
                        // no need to distribute active mismatch due to connectivity modifications
                        // this is handled when the slack is distributed in pre contingency states override
                        Supplier<double[]> toPostContingencyStates = () -> calculatePostContingencyStatesForAContingencyBreakingConnectivity(connectivityAnalysisResult, context,
                                connectivityBreakAnalysisResults.contingencyElementByBranch(), workingContingencyStates, connectivityBreakAnalysisResults.contingenciesStates(), reportNode);
                        // function to compute post contingency and post operator strategy states
                        Function<List<LfAction>, double[]> toPostContingencyAndOperatorStrategyStates = operatorStrategyLfActions -> calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity(connectivityAnalysisResult, context,
                                connectivityBreakAnalysisResults.contingencyElementByBranch(), workingContingencyStates, connectivityBreakAnalysisResults.contingenciesStates(),
                                operatorStrategyLfActions, tapPositionChangeElementsByBranchId, actionsStates, reportNode);
                        // runnable to restore pre contingency states, after modifications applied to the lfNetwork
                        // no need to update workingContingencyStates as an override of flow states will be computed
                        Runnable restorePreContingencyStates = () -> {};
                        addPostContingencyAndOperatorStrategyResults(context, connectivityAnalysisResult, operatorStrategiesByContingencyId, lfActionById, toPostContingencyStates,
                                toPostContingencyAndOperatorStrategyStates, restorePreContingencyStates, preContingencyLimitViolationManager, preContingencyNetworkResult, createResultExtension,
                                securityAnalysisParameters.getIncreasedViolationsParameters(), limitReductions, postContingencyResults, operatorStrategyResults);
                    });

            return new SecurityAnalysisResult(
                    new PreContingencyResult(LoadFlowResult.ComponentResult.Status.CONVERGED,
                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
                            postContingencyResults, operatorStrategyResults);
        }
    }
}
