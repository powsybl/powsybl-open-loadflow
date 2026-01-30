/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.google.common.base.Stopwatch;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.AbstractLfBranchAction;
import com.powsybl.openloadflow.network.action.LfAction;
import com.powsybl.openloadflow.network.action.LfOperatorStrategy;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public final class ConnectivityBreakAnalysis {

    private static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-7;

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectivityBreakAnalysis.class);

    public record DisabledElements(Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches, Set<LfHvdc> hvdcsWithoutPower) {

        public static final DisabledElements NO_DISABLED_ELEMENTS = new DisabledElements(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    }

    public static final class ConnectivityAnalysisResult {
        private final PropagatedContingency propagatedContingency;

        private final LfNetwork network;

        private final Set<String> elementsToReconnect;

        private final Set<LfBus> slackConnectedComponent; // buses of connected component where the slack is

        private final int createdSynchronousComponents;

        private final DisabledElements disabledElements;

        private final LfOperatorStrategy operatorStrategy;

        public ConnectivityAnalysisResult(PropagatedContingency propagatedContingency, LfOperatorStrategy operatorStrategy, LfNetwork network, Set<String> elementsToReconnect,
                                          DisabledElements disabledElements, Set<LfBus> slackConnectedComponentBuses,
                                          int createdSynchronousComponents) {
            this.propagatedContingency = Objects.requireNonNull(propagatedContingency);
            this.operatorStrategy = operatorStrategy;
            this.network = Objects.requireNonNull(network);
            this.elementsToReconnect = elementsToReconnect;
            this.disabledElements = disabledElements;
            this.slackConnectedComponent = slackConnectedComponentBuses;
            this.createdSynchronousComponents = createdSynchronousComponents;
        }

        public static ConnectivityAnalysisResult createNonBreakingConnectivityAnalysisResult(PropagatedContingency propagatedContingency,
                                                                                             LfOperatorStrategy operatorStrategy,
                                                                                             LfNetwork network) {
            return new ConnectivityAnalysisResult(propagatedContingency, operatorStrategy, network, Collections.emptySet(), DisabledElements.NO_DISABLED_ELEMENTS, Collections.emptySet(), 0);
        }

        public PropagatedContingency getPropagatedContingency() {
            return propagatedContingency;
        }

        public Set<String> getElementsToReconnect() {
            return elementsToReconnect;
        }

        public Set<LfBus> getDisabledBuses() {
            return disabledElements.disabledBuses;
        }

        public Set<LfBus> getSlackConnectedComponent() {
            return slackConnectedComponent;
        }

        public Set<LfBranch> getPartialDisabledBranches() {
            return disabledElements.partialDisabledBranches;
        }

        public Set<LfHvdc> getHvdcsWithoutPower() {
            return disabledElements.hvdcsWithoutPower;
        }

        public LfOperatorStrategy getOperatorStrategy() {
            return operatorStrategy;
        }

        public Optional<LfContingency> toLfContingency() {
            PropagatedContingency.ContingencyConnectivityLossImpactAnalysis analysis = (network, contingencyId, branchesToOpen, relocateSlackBus)
                    -> new PropagatedContingency.ContingencyConnectivityLossImpact(createdSynchronousComponents, disabledElements.disabledBuses, disabledElements.hvdcsWithoutPower);
            return propagatedContingency.toLfContingency(network, false, analysis);
        }

        public ConnectivityAnalysisResult withOperatorStrategy(LfOperatorStrategy operatorStrategy) {
            return new ConnectivityAnalysisResult(this.propagatedContingency, operatorStrategy, this.network, this.elementsToReconnect, this.disabledElements,
                    this.slackConnectedComponent, this.createdSynchronousComponents
            );
        }
    }

    public record ConnectivityBreakAnalysisResults(List<ConnectivityAnalysisResult> nonBreakingConnectivityAnalysisResults,
                                                   List<ConnectivityAnalysisResult> connectivityBreakingAnalysisResults,
                                                   DenseMatrix contingenciesStates,
                                                   Map<String, ComputedContingencyElement> contingencyElementByBranch) {
    }

    private ConnectivityBreakAnalysis() {
    }

    private record States(DenseMatrix contingencyStates, DenseMatrix actionStates) {
    }

    private static void detectPotentialConnectivityBreak(LfNetwork lfNetwork, DenseMatrix contingencyStates, List<PropagatedContingency> contingencies,
                                                         Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                         EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                         List<ConnectivityAnalysisResult> nonBreakingConnectivityAnalysisResults,
                                                         List<PropagatedContingency> potentiallyBreakingConnectivityContingencies) {
        for (PropagatedContingency contingency : contingencies) {
            if (isConnectivityPotentiallyModifiedByContingencyAndOperatorStrategy(lfNetwork, new States(contingencyStates, DenseMatrix.EMPTY), contingency, contingencyElementByBranch,
                    Collections.emptyList(), Collections.emptyMap(), equationSystem)) { // connectivity broken
                potentiallyBreakingConnectivityContingencies.add(contingency);
            } else {
                nonBreakingConnectivityAnalysisResults.add(ConnectivityAnalysisResult.createNonBreakingConnectivityAnalysisResult(contingency, null, lfNetwork));
            }
        }
    }

    /**
     * Returns true if the given contingency and operator strategy actions potentially break connectivity.
     * This is determined with a "worst case" sensitivity-criterion. If the criterion is not verified, there is no connectivity break.
     */
    private static boolean isConnectivityPotentiallyModifiedByContingencyAndOperatorStrategy(LfNetwork lfNetwork, States states, PropagatedContingency contingency,
                                                                                             Map<String, ComputedContingencyElement> contingencyElementByBranch, List<LfAction> operatorStrategyLfActions,
                                                                                             Map<LfAction, ComputedElement> actionElementByBranch, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .map(contingencyElementByBranch::get)
                .collect(Collectors.toList());
        // The sensitivity criterion only considers actions that disable branches in order to compute a "worst-case" scenario,
        // i.e. that if the criterion is not met, there is no connectivity break.
        // As the actions removed either have no impact or can only close branches (and therefore affect the criterion negatively),
        // it is not necessary to consider them to ensure that there is no loss of connectivity.
        List<ComputedElement> actionElements = operatorStrategyLfActions.stream()
                .map(actionElementByBranch::get)
                .filter(actionElement -> actionElement instanceof ComputedSwitchBranchElement computedSwitchBranchElement && !computedSwitchBranchElement.isEnabled())
                .collect(Collectors.toList());
        return isGroupOfElementsBreakingConnectivity(lfNetwork, states.contingencyStates(), contingencyElements, states.actionStates(), actionElements, equationSystem);
    }

    private static boolean isGroupOfElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingenciesStates,
                                                                 List<ComputedContingencyElement> contingencyElements,
                                                                 DenseMatrix actionStates, List<ComputedElement> actionElements,
                                                                 EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity after a contingency
        // we consider a +1 -1 on a line, and we observe the sensitivity of these injections on the other contingency elements
        // if the sum of the sensitivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // thus, losing these lines will lose the connectivity
        List<ComputedElement> computedElements = Stream.concat(contingencyElements.stream(), actionElements.stream()).toList();
        for (ComputedElement element : computedElements) {
            double sum = 0d;
            for (ComputedElement element2 : computedElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getLfBranch().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(ElementType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                DenseMatrix elementMatrix = element instanceof ComputedContingencyElement ? contingenciesStates : actionStates;
                double value = Math.abs(p.calculateSensi(elementMatrix, element.getComputedElementIndex()));
                sum += value;
            }

            if (sum > 1d - CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                return true;
            }
        }
        return false;
    }

    /**
     * Compute post contingency and operator strategy connectivity analysis result by analyzing network connectivity.
     * Both contingency and actions can impact connectivity.
     */
    private static Optional<ConnectivityAnalysisResult> computeConnectivityAnalysisResult(LfNetwork lfNetwork,
                                                                                          PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                                          LfOperatorStrategy operatorStrategy, Map<LfAction, ComputedElement> actionElementByBranch) {
        GraphConnectivity<LfBus, LfBranch> connectivity = lfNetwork.getConnectivity();

        // concatenate all computed elements, to apply them on the connectivity
        List<LfAction> lfActions = operatorStrategy == null ? Collections.emptyList() : operatorStrategy.getActions();
        List<ComputedElement> modifyingConnectivityCandidates = Stream.concat(
                contingency.getBranchIdsToOpen().keySet().stream().map(contingencyElementByBranch::get),
                lfActions.stream().map(actionElementByBranch::get)
        ).sorted(Comparator.comparing(element -> element.getLfBranch().getId())).toList();

        // we confirm the breaking of connectivity by network connectivity
        ConnectivityAnalysisResult connectivityAnalysisResult = null;
        int nbConnectedComponentsBefore = connectivity.getNbConnectedComponents();
        connectivity.startTemporaryChanges();
        try {
            // apply all modifications of connectivity, due to the lost/enabled/disabled branches
            modifyingConnectivityCandidates.forEach(computedElement -> computedElement.applyToConnectivity(connectivity));

            // filter the branches that really impacts connectivity
            // the traversal order of the set must be deterministic to ensure consistent element selection when multiple elements can restore connectivity
            // without this, fast DC post contingency states may vary for buses disconnected from main connected component, depending on which elements were selected
            LinkedHashSet<ComputedElement> breakingConnectivityElements = modifyingConnectivityCandidates.stream()
                    .filter(element -> isBreakingConnectivity(connectivity, element))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (!breakingConnectivityElements.isEmpty()) {
                // only compute for factors that have to be computed for this contingency lost
                Set<String> elementsToReconnect = computeElementsToReconnect(connectivity, breakingConnectivityElements, nbConnectedComponentsBefore);
                int createdSynchronousComponents = connectivity.getNbConnectedComponents() - 1;
                Set<LfBus> disabledBuses = connectivity.getVerticesRemovedFromMainComponent();
                Set<LfHvdc> hvdcsWithoutPower = PropagatedContingency.getHvdcsWithoutPower(lfNetwork, disabledBuses, connectivity);
                connectivityAnalysisResult = new ConnectivityAnalysisResult(contingency, operatorStrategy, lfNetwork, elementsToReconnect,
                        new DisabledElements(disabledBuses, connectivity.getEdgesRemovedFromMainComponent(), hvdcsWithoutPower),
                        connectivity.getConnectedComponent(lfNetwork.getSlackBus()), createdSynchronousComponents);
            }
        } finally {
            connectivity.undoTemporaryChanges();
        }
        return Optional.ofNullable(connectivityAnalysisResult);
    }

    private static boolean isBreakingConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity, ComputedElement element) {
        LfBranch lfBranch = element.getLfBranch();
        return connectivity.getComponentNumber(lfBranch.getBus1()) != connectivity.getComponentNumber(lfBranch.getBus2());
    }

    /**
     * Given the elements breaking the connectivity, extract the minimum number of elements which reconnect all connected components together
     */
    private static Set<String> computeElementsToReconnect(GraphConnectivity<LfBus, LfBranch> connectivity,
                                                          Set<ComputedElement> breakingConnectivityElements,
                                                          int nbConnectedComponentsBefore) {
        Set<String> elementsToReconnect = new LinkedHashSet<>();

        // We suppose we're reconnecting one by one each element breaking connectivity.
        // At each step we look if the reconnection was needed on the connectivity level by maintaining a list of grouped connected components.
        List<Set<Integer>> reconnectedCc = new ArrayList<>();
        for (ComputedElement element : breakingConnectivityElements) {
            int cc1 = connectivity.getComponentNumber(element.getLfBranch().getBus1());
            int cc2 = connectivity.getComponentNumber(element.getLfBranch().getBus2());

            Set<Integer> recCc1 = reconnectedCc.stream().filter(s -> s.contains(cc1)).findFirst().orElseGet(() -> new HashSet<>(List.of(cc1)));
            Set<Integer> recCc2 = reconnectedCc.stream().filter(s -> s.contains(cc2)).findFirst().orElseGet(() -> Set.of(cc2));
            if (recCc1 != recCc2) {
                // cc1 and cc2 are still separated:
                // - mark the element as needed to reconnect all connected components together
                // - update the list of grouped connected components
                elementsToReconnect.add(element.getLfBranch().getId());
                reconnectedCc.remove(recCc2);
                if (recCc1.size() == 1) {
                    // adding the new set (the list of grouped connected components is not initialized with the singleton sets)
                    reconnectedCc.add(recCc1);
                }
                recCc1.addAll(recCc2);
            }
        }

        // !!! we can have more than one connected component on base case because of actions potentially reconnecting
        // some elements
        int createdConnectedComponents = connectivity.getNbConnectedComponents() - nbConnectedComponentsBefore;
        if (reconnectedCc.size() != 1 || reconnectedCc.getFirst().size() - 1 != createdConnectedComponents) {
            LOGGER.error("Elements to reconnect computed do not reconnect all connected components together");
        }

        return elementsToReconnect;
    }

    private static Map<String, ComputedContingencyElement> createContingencyElementsIndexByBranchId(List<PropagatedContingency> contingencies,
                                                                                                    LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        Map<String, ComputedContingencyElement> contingencyElementByBranch =
                contingencies.stream()
                        .flatMap(contingency -> contingency.getBranchIdsToOpen().keySet().stream())
                        .map(branch -> new ComputedContingencyElement(new BranchContingency(branch), lfNetwork, equationSystem))
                        .filter(element -> element.getLfBranchEquation() != null)
                        .collect(Collectors.toMap(
                                computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                computedContingencyElement -> computedContingencyElement,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ));
        ComputedElement.setComputedElementIndexes(contingencyElementByBranch.values());
        return contingencyElementByBranch;
    }

    public static ConnectivityBreakAnalysisResults run(DcLoadFlowContext loadFlowContext, List<PropagatedContingency> contingencies) {
        // index contingency elements by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = createContingencyElementsIndexByBranchId(contingencies, loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem());

        // compute states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(loadFlowContext, contingencyElementByBranch.values());

        List<ConnectivityAnalysisResult> nonBreakingConnectivityAnalysisResults = new ArrayList<>();
        List<ConnectivityAnalysisResult> connectivityBreakingAnalysisResults = new ArrayList<>();

        // connectivity analysis by contingency
        // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
        // a contingency involving a phase tap changer loss has to be processed separately
        List<PropagatedContingency> potentiallyBreakingConnectivityContingencies = new ArrayList<>();

        // this first method based on sensitivity criteria is able to detect some contingencies that do not break
        // connectivity and other contingencies that potentially break connectivity
        LOGGER.info("Running sensitivity based connectivity analysis...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        detectPotentialConnectivityBreak(loadFlowContext.getNetwork(), contingenciesStates, contingencies, contingencyElementByBranch, loadFlowContext.getEquationSystem(),
                nonBreakingConnectivityAnalysisResults, potentiallyBreakingConnectivityContingencies);
        stopwatch.stop();
        LOGGER.info("Sensitivity based connectivity analysis done in {} ms, {} contingencies do not break connectivity, {} contingencies potentially break connectivity",
                stopwatch.elapsed(TimeUnit.MILLISECONDS), nonBreakingConnectivityAnalysisResults.size(), potentiallyBreakingConnectivityContingencies.size());

        // this second method process all contingencies that potentially break connectivity and using graph algorithms
        // find remaining contingencies that do not break connectivity
        LOGGER.info("Running graph based connectivity analysis...");
        stopwatch.reset().start();
        for (PropagatedContingency propagatedContingency : potentiallyBreakingConnectivityContingencies) {
            // compute connectivity analysis result, with contingency only
            computeConnectivityAnalysisResult(loadFlowContext.getNetwork(), propagatedContingency, contingencyElementByBranch, null, Collections.emptyMap())
                    .ifPresentOrElse(connectivityBreakingAnalysisResults::add,
                                     () -> nonBreakingConnectivityAnalysisResults.add(ConnectivityAnalysisResult.createNonBreakingConnectivityAnalysisResult(propagatedContingency, null, loadFlowContext.getNetwork())));
        }
        stopwatch.stop();
        LOGGER.info("Graph based connectivity analysis done in {} ms, {} contingencies do not break connectivity, {} contingencies break connectivity",
                stopwatch.elapsed(TimeUnit.MILLISECONDS), nonBreakingConnectivityAnalysisResults.size(), connectivityBreakingAnalysisResults.size());

        return new ConnectivityBreakAnalysisResults(nonBreakingConnectivityAnalysisResults, connectivityBreakingAnalysisResults, contingenciesStates, contingencyElementByBranch);
    }

    /**
     * Processes post contingency and operator strategy connectivity analysis result, from post contingency connectivity result.
     * If there is no switching action or if the connectivity is not modified, the post contingency result is returned, as connectivity has not changed.
     */
    public static ConnectivityAnalysisResult processPostContingencyAndPostOperatorStrategyConnectivityAnalysisResult(DcLoadFlowContext loadFlowContext, ConnectivityAnalysisResult postContingencyConnectivityAnalysisResult,
                                                                                                                     Map<String, ComputedContingencyElement> contingencyElementByBranch, DenseMatrix contingenciesStates,
                                                                                                                     LfOperatorStrategy operatorStrategy, Map<LfAction, ComputedElement> actionElementsIndexByLfAction, DenseMatrix actionsStates) {
        // if there is no topological action, no need to process anything as the connectivity has not changed from post contingency result
        boolean hasAnyTopologicalAction = operatorStrategy.getActions().stream().anyMatch(lfAction -> lfAction instanceof AbstractLfBranchAction<?>);
        if (!hasAnyTopologicalAction) {
            return postContingencyConnectivityAnalysisResult.withOperatorStrategy(operatorStrategy);
        }

        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        PropagatedContingency contingency = postContingencyConnectivityAnalysisResult.getPropagatedContingency();

        // verify if the connectivity is potentially modified, and returns post contingency connectivity result if this is not the case
        boolean isConnectivityPotentiallyModified = isConnectivityPotentiallyModifiedByContingencyAndOperatorStrategy(lfNetwork, new States(contingenciesStates, actionsStates), contingency,
                contingencyElementByBranch, operatorStrategy.getActions(), actionElementsIndexByLfAction, loadFlowContext.getEquationSystem());
        if (!isConnectivityPotentiallyModified) {
            return postContingencyConnectivityAnalysisResult.withOperatorStrategy(operatorStrategy);
        }

        // compute the connectivity result for the contingency and the associated actions
        return computeConnectivityAnalysisResult(lfNetwork, contingency, contingencyElementByBranch, operatorStrategy, actionElementsIndexByLfAction)
                .map(postContingencyAndOperatorStrategyConnectivityAnalysisResult -> {
                    LOGGER.debug("After graph based connectivity analysis, the contingency '{}' and operator strategy '{}' break connectivity",
                            contingency.getContingency().getId(), operatorStrategy.getIndexedOperatorStrategy().value().getId());
                    return postContingencyAndOperatorStrategyConnectivityAnalysisResult;
                })
                .orElseGet(() -> {
                    LOGGER.debug("After graph based connectivity analysis, the contingency '{}' and operator strategy '{}' do not break connectivity",
                            contingency.getContingency().getId(), operatorStrategy.getIndexedOperatorStrategy().value().getId());
                    return ConnectivityAnalysisResult.createNonBreakingConnectivityAnalysisResult(postContingencyConnectivityAnalysisResult.propagatedContingency, operatorStrategy, postContingencyConnectivityAnalysisResult.network);
                });
    }
}
