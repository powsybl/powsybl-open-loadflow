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
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.openloadflow.util.PerUnit;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.updateNetwork;
import static com.powsybl.openloadflow.network.impl.PropagatedContingency.cleanContingencies;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * TODO : change the actions parameters to operator strategy parameters
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
    private double[] calculatePostContingencyStatesForAContingency(DcLoadFlowContext loadFlowContext, DenseMatrix contingenciesStates, double[] flowStates,
                                                                   PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                   Set<LfBus> disabledBuses, Set<String> elementsToReconnect, Set<LfBranch> partialDisabledBranches, ReportNode reportNode) {
        return calculatePostContingencyAndOperatorStrategyStatesForAContingency(loadFlowContext, contingenciesStates, flowStates, contingency, contingencyElementByBranch, disabledBuses, elementsToReconnect,
                partialDisabledBranches, Collections.emptyList(), Collections.emptyMap(), WoodburyEngine.EMPTY_DENSE_MATRIX, reportNode);
    }

    /**
     * Calculate post contingency and post operator strategy states, for a contingency.
     * In case of connectivity break, a pre-computation has been done in {@link #calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity}
     * to reset active power flow of hvdc lines on which one bus is lost.
     * If connectivity, a generator, a load or a phase tap changer is lost/modified due to the contingency/operator strategy, the pre contingency flowStates are overridden.
     */
    private double[] calculatePostContingencyAndOperatorStrategyStatesForAContingency(DcLoadFlowContext loadFlowContext, DenseMatrix contingenciesStates, double[] flowStates,
                                                                                      PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                                      Set<LfBus> disabledBuses, Set<String> elementsToReconnect, Set<LfBranch> partialDisabledBranches,
                                                                                      List<LfAction> operatorStrategyLfActions, Map<String, ComputedTapPositionChangeElement> tapPositionChangeElementByBranch,
                                                                                      DenseMatrix actionsStates, ReportNode reportNode) {

        List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(contingencyElementByBranch::get)
                .collect(Collectors.toList());
        List<ComputedTapPositionChangeElement> actionElements = operatorStrategyLfActions.stream()
                .map(lfAction -> lfAction.getTapPositionChange().getBranch().getId())
                .map(tapPositionChangeElementByBranch::get)
                .collect(Collectors.toList());

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
            DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
            NetworkState networkState = NetworkState.save(lfNetwork);
            contingency.toLfContingency(lfNetwork, false)
                    .ifPresent(lfContingency -> lfContingency.apply(lfParameters.getBalanceType()));
            newFlowStates = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(loadFlowContext, disabledNetwork, reportNode);
            engine.toPostContingencyAndOperatorStrategyStates(newFlowStates);
            networkState.restore();
        }

        return newFlowStates;
    }

    /**
     * Calculate post contingency states for a contingency breaking connectivity, without remedial actions.
     */
    private double[] calculatePostContingencyStatesForAContingencyBreakingConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                                                       Map<String, ComputedContingencyElement> contingencyElementByBranch, double[] flowStates, DenseMatrix contingenciesStates,
                                                                                       ReportNode reportNode) {
        return calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, contingencyElementByBranch, flowStates,
                contingenciesStates, Collections.emptyList(), Collections.emptyMap(), WoodburyEngine.EMPTY_DENSE_MATRIX, reportNode);
    }

    /**
     * Calculate post contingency and post operator strategy states, for a contingency breaking connectivity.
     */
    private double[] calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
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

        return calculatePostContingencyAndOperatorStrategyStatesForAContingency(loadFlowContext, contingenciesStates, flowStates,
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
    private PostContingencyResult computePostContingencyResultFromPostContingencyStates(DcLoadFlowContext loadFlowContext, Contingency contingency, LfContingency lfContingency,
                                                                                        LimitViolationManager preContingencyLimitViolationManager, PreContingencyNetworkResult preContingencyNetworkResult,
                                                                                        boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                                        double[] postContingencyStates, List<LimitReduction> limitReductions) {

        // update network state with post contingency states
        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        loadFlowContext.getEquationSystem().getStateVector().set(postContingencyStates);
        updateNetwork(lfNetwork, loadFlowContext.getEquationSystem(), postContingencyStates);

        lfContingency.apply(loadFlowContext.getParameters().getBalanceType());

        // update post contingency network result
        var postContingencyNetworkResult = new PostContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency);
        postContingencyNetworkResult.update();

        // detect violations
        var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions, violationsParameters);
        postContingencyLimitViolationManager.detectViolations(lfNetwork);

        // connectivity result due to the application of the lf contingency
        var connectivityResult = new ConnectivityResult(
                lfContingency.getCreatedSynchronousComponentsCount(), 0,
                lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                lfContingency.getDisconnectedElementIds());

        return new PostContingencyResult(contingency,
                PostContingencyComputationStatus.CONVERGED,
                new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                postContingencyNetworkResult.getBranchResults(),
                postContingencyNetworkResult.getBusResults(),
                postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                connectivityResult);
    }

    /**
     * Returns post contingency result associated to the given contingency if it impacts the network.
     * Otherwise, returns an empty Optional.
     */
    private Optional<PostContingencyResult> processPostContingencyResult(DcLoadFlowContext context, PropagatedContingency contingency, Supplier<double[]> postContingencyStatesSupplier,
                                                                         LimitViolationManager preContingencyLimitViolationManager, PreContingencyNetworkResult preContingencyNetworkResult,
                                                                         boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                         List<LimitReduction> limitReductions) {
        LfNetwork lfNetwork = context.getNetwork();
        Optional<LfContingency> lfContingencyOptional = contingency.toLfContingency(lfNetwork, false);
        // only process contingencies that impact the network
        if (lfContingencyOptional.isPresent()) {
            LfContingency lfContingency = lfContingencyOptional.get();

            ReportNode postContSimReportNode = Reports.createPostContingencySimulation(lfNetwork.getReportNode(), contingency.getContingency().getId());
            lfNetwork.setReportNode(postContSimReportNode);

            logPostContingencyStart(lfNetwork, lfContingency);
            Stopwatch stopwatch = Stopwatch.createStarted();

            double[] postContingencyStates = postContingencyStatesSupplier.get();
            PostContingencyResult postContingencyResult = computePostContingencyResultFromPostContingencyStates(context, contingency.getContingency(),
                    lfContingency, preContingencyLimitViolationManager, preContingencyNetworkResult, createResultExtension,
                    violationsParameters, postContingencyStates, limitReductions);

            stopwatch.stop();
            logPostContingencyEnd(lfNetwork, lfContingency, stopwatch);

            return Optional.of(postContingencyResult);
        }
        return Optional.empty();
    }

    /**
     * Returns the operator strategy result associated to given post contingency and post actions states.
     */
    private OperatorStrategyResult computeOperatorStrategyResultFromPostContingencyAndOperatorStrategyStates(OperatorStrategy operatorStrategy,
                                                                                                             DcLoadFlowContext loadFlowContext, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                                                             List<LfAction> operatorStrategyLfActions, PropagatedContingency contingency, double[] postContingencyAndActionsStates,
                                                                                                             LimitViolationManager preContingencyLimitViolationManager, List<LimitReduction> limitReductions,
                                                                                                             boolean createResultExtension) {
        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        loadFlowContext.getEquationSystem().getStateVector().set(postContingencyAndActionsStates);
        updateNetwork(lfNetwork, loadFlowContext.getEquationSystem(), postContingencyAndActionsStates);

        LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElseThrow(); // the contingency can not be null
        lfContingency.apply(loadFlowContext.getParameters().getBalanceType());
        LfAction.apply(operatorStrategyLfActions, lfNetwork, lfContingency, loadFlowContext.getParameters().getNetworkParameters());

        // update network result
        var postActionsNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
        postActionsNetworkResult.update();

        // detect violations
        var postActionsViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions, violationsParameters);
        postActionsViolationManager.detectViolations(lfNetwork);

        return new OperatorStrategyResult(operatorStrategy, PostContingencyComputationStatus.CONVERGED,
                new LimitViolationsResult(postActionsViolationManager.getLimitViolations()),
                new NetworkResult(postActionsNetworkResult.getBranchResults(),
                        postActionsNetworkResult.getBusResults(),
                        postActionsNetworkResult.getThreeWindingsTransformerResults()));
    }

    /**
     * Returns post contingency result associated to the given contingency if it impacts the network.
     * Otherwise, returns an empty Optional.
     */
    private List<OperatorStrategyResult> processPostContingencyAndOperatorStrategyResults(DcLoadFlowContext context, PropagatedContingency contingency, Supplier<double[]> postContingencyAndOperatorStrategyStatesSupplier,
                                                                                              LimitViolationManager preContingencyLimitViolationManager, PreContingencyNetworkResult preContingencyNetworkResult,
                                                                                              boolean createResultExtension, SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                                              List<LimitReduction> limitReductions, List<OperatorStrategy> operatorStrategiesForThisContingency) {
        LfNetwork lfNetwork = context.getNetwork();
        List<OperatorStrategyResult> operatorStrategyResultsForThisContingency = new ArrayList<>();
        if (operatorStrategiesForThisContingency != null) {
            for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
                ReportNode osSimReportNode = Reports.createOperatorStrategySimulation(lfNetwork.getReportNode(), operatorStrategy.getId()); // FIXME : does the report node matter there ?
                lfNetwork.setReportNode(osSimReportNode);

                List<String> actionIds = checkCondition(operatorStrategy, postContingencyResult.getLimitViolationsResult());
                List<LfAction> operatorStrategyLfActions = actionIds.stream()
                        .map(lfActionById::get)
                        .filter(Objects::nonNull)
                        .toList();

                logActionStart(lfNetwork, operatorStrategy);
                Stopwatch stopwatch = Stopwatch.createStarted();

                double[] postContingencyAndOperatorStrategyStates = postContingencyAndOperatorStrategyStatesSupplier.get();
                OperatorStrategyResult operatorStrategyResult = computeOperatorStrategyResultFromPostContingencyAndOperatorStrategyStates(operatorStrategy, context, violationsParameters,
                        operatorStrategyLfActions, contingency, postContingencyAndOperatorStrategyStates, preContingencyLimitViolationManager, limitReductions, createResultExtension);

                stopwatch.stop();
                logActionEnd(lfNetwork, operatorStrategy, stopwatch);
                operatorStrategyResultsForThisContingency.add(operatorStrategyResult);
            }
        }
        return operatorStrategyResultsForThisContingency;
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

            // save base state for later restoration after each contingency/action
            NetworkState networkState = NetworkState.save(lfNetwork);

            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();
            LOGGER.info("Processing post contingency results for contingencies with no connectivity break");
            connectivityBreakAnalysisResults.nonBreakingConnectivityContingencies().forEach(
                    nonBreakingConnectivityContingency -> {
                        Supplier<double[]> toPostContingencyStates = () -> calculatePostContingencyStatesForAContingency(context, connectivityBreakAnalysisResults.contingenciesStates(), workingContingencyStates, nonBreakingConnectivityContingency, connectivityBreakAnalysisResults.contingencyElementByBranch(),
                                        Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), reportNode);
                        Optional<PostContingencyResult> postContingencyResult = processPostContingencyResult(context, nonBreakingConnectivityContingency, toPostContingencyStates, preContingencyLimitViolationManager,
                                preContingencyNetworkResult, createResultExtension, securityAnalysisParameters.getIncreasedViolationsParameters(), limitReductions);

                        postContingencyResult.ifPresent(result -> {
                            postContingencyResults.add(result);
                            // update workingContingencyStates as it may have been updated by post contingency states calculation
                            System.arraycopy(preContingencyStates, 0, workingContingencyStates, 0, preContingencyStates.length);
                            // restore pre contingency state for next post contingency computation
                            networkState.restore();

                            Supplier<double[]> toPostContingencyAndOperatorStrategyStates = (operatorStrategyLfActions) -> calculatePostContingencyAndOperatorStrategyStatesForAContingency(context, connectivityBreakAnalysisResults.contingenciesStates(), preContingencyStates, propagatedContingency,
                                    connectivityBreakAnalysisResults.contingencyElementByBranch(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                                    operatorStrategyLfActions, tapPositionChangeElementsByBranchId, actionsStates, reportNode);
                            List<OperatorStrategy> operatorStrategyForThisContingency = operatorStrategiesByContingencyId.get(nonBreakingConnectivityContingency.getContingency().getId());
                            List<OperatorStrategyResult> postOperatorStrategyResults = processPostContingencyAndOperatorStrategyResults(context, nonBreakingConnectivityContingency, toPostContingencyAndOperatorStrategyStates, preContingencyLimitViolationManager,
                                    preContingencyNetworkResult, createResultExtension, securityAnalysisParameters.getIncreasedViolationsParameters(), limitReductions, operatorStrategyForThisContingency);
                            operatorStrategyResults.addAll(postOperatorStrategyResults); // FIXME : should not add if the action is void ?
                            // TODO : should the case without accepted action tested in fast dc sa
                        });
                    }
            );

            LOGGER.info("Processing post contingency results for contingencies breaking connectivity");
            connectivityBreakAnalysisResults.connectivityAnalysisResults()
                    .forEach(connectivityAnalysisResult -> {
                        PropagatedContingency breakingConnectivityContingency = connectivityAnalysisResult.getPropagatedContingency();
                        // no need to distribute active mismatch due to connectivity modifications
                        // this is handled when the slack is distributed in pre contingency states override
                        Supplier<double[]> toPostContingencyStates = () -> calculatePostContingencyStatesForAContingencyBreakingConnectivity(connectivityAnalysisResult, context,
                                connectivityBreakAnalysisResults.contingencyElementByBranch(), preContingencyStates,
                                connectivityBreakAnalysisResults.contingenciesStates(), reportNode);
                        Optional<PostContingencyResult> postContingencyBreakingConnectivityResult = processPostContingencyResult(context, breakingConnectivityContingency, toPostContingencyStates, preContingencyLimitViolationManager,
                                preContingencyNetworkResult, createResultExtension, securityAnalysisParameters.getIncreasedViolationsParameters(), limitReductions);

                        postContingencyBreakingConnectivityResult.ifPresent(result -> {
                            postContingencyResults.add(result);
                            networkState.restore();

                            Supplier<double[]> toPostContingencyAndOperatorStrategyStates = (operatorStrategyLfActions) -> {
                                return calculatePostContingencyAndOperatorStrategyStatesForAContingencyBreakingConnectivity(connectivityAnalysisResult, context,
                                        connectivityBreakAnalysisResults.contingencyElementByBranch(), preContingencyStates, connectivityBreakAnalysisResults.contingenciesStates(),
                                        operatorStrategyLfActions, tapPositionChangeElementsByBranchId, actionsStates, reportNode);
                            };
                            List<OperatorStrategy> operatorStrategyForThisContingency = operatorStrategiesByContingencyId.get(breakingConnectivityContingency.getContingency().getId());
                            List<OperatorStrategyResult> postOperatorStrategyResults = processPostContingencyAndOperatorStrategyResults(context, breakingConnectivityContingency, toPostContingencyAndOperatorStrategyStates, preContingencyLimitViolationManager,
                                    preContingencyNetworkResult, createResultExtension, securityAnalysisParameters.getIncreasedViolationsParameters(), limitReductions, operatorStrategyForThisContingency);
                            operatorStrategyResults.addAll(postOperatorStrategyResults);
                        });
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
