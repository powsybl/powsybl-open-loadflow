/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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

        public ConnectivityAnalysisResult(PropagatedContingency nonBreakingConnectivityContingency, LfNetwork network) {
            this(nonBreakingConnectivityContingency, network, Collections.emptySet(), DisabledElements.NO_DISABLED_ELEMENTS, Collections.emptySet(), 0);
        }

        public ConnectivityAnalysisResult(PropagatedContingency propagatedContingency, LfNetwork network, Set<String> elementsToReconnect,
                                          DisabledElements disabledElements, Set<LfBus> slackConnectedComponentBuses,
                                          int createdSynchronousComponents) {
            this.propagatedContingency = Objects.requireNonNull(propagatedContingency);
            this.network = Objects.requireNonNull(network);
            this.elementsToReconnect = elementsToReconnect;
            this.disabledElements = disabledElements;
            this.slackConnectedComponent = slackConnectedComponentBuses;
            this.createdSynchronousComponents = createdSynchronousComponents;
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

        public Optional<LfContingency> toLfContingency() {
            PropagatedContingency.ContingencyConnectivityLossImpactAnalysis analysis = (network, contingencyId, branchesToOpen, relocateSlackBus)
                    -> new PropagatedContingency.ContingencyConnectivityLossImpact(createdSynchronousComponents, disabledElements.disabledBuses, disabledElements.hvdcsWithoutPower);
            return propagatedContingency.toLfContingency(network, false, analysis);
        }
    }

    public record ConnectivityBreakAnalysisResults(List<PropagatedContingency> nonBreakingConnectivityContingencies,
                                                   List<ConnectivityAnalysisResult> connectivityAnalysisResults,
                                                   DenseMatrix contingenciesStates,
                                                   Map<String, ComputedContingencyElement> contingencyElementByBranch) {

    }

    private ConnectivityBreakAnalysis() {

    }

    private static void detectPotentialConnectivityBreak(LfNetwork lfNetwork, DenseMatrix states, List<PropagatedContingency> contingencies,
                                                         Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                         EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                         List<PropagatedContingency> nonBreakingConnectivityContingencies,
                                                         List<PropagatedContingency> potentiallyBreakingConnectivityContingencies) {
        for (PropagatedContingency contingency : contingencies) {
            List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());
            if (isGroupOfElementsBreakingConnectivity(lfNetwork, states, contingencyElements, equationSystem)) { // connectivity broken
                potentiallyBreakingConnectivityContingencies.add(contingency);
            } else {
                nonBreakingConnectivityContingencies.add(contingency);
            }
        }
    }

    private static boolean isGroupOfElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingenciesStates,
                                                                 List<ComputedContingencyElement> contingencyElements,
                                                                 EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity after a contingency
        // we consider a +1 -1 on a line, and we observe the sensitivity of these injections on the other contingency elements
        // if the sum of the sensitivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // thus, losing these lines will lose the connectivity
        for (ComputedContingencyElement element : contingencyElements) {
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(ElementType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculateSensi(contingenciesStates, element.getComputedElementIndex()));
                sum += value;
            }
            if (sum > 1d - CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                return true;
            }
        }
        return false;
    }

    private static List<ConnectivityAnalysisResult> computeConnectivityData(LfNetwork lfNetwork, List<PropagatedContingency> potentiallyBreakingConnectivityContingencies,
                                                                            Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                            List<PropagatedContingency> nonBreakingConnectivityContingencies) {
        if (potentiallyBreakingConnectivityContingencies.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConnectivityAnalysisResult> connectivityAnalysisResults = new ArrayList<>();

        GraphConnectivity<LfBus, LfBranch> connectivity = lfNetwork.getConnectivity();
        for (PropagatedContingency contingency : potentiallyBreakingConnectivityContingencies) {
            List<ComputedContingencyElement> breakingConnectivityCandidates = contingency.getBranchIdsToOpen().keySet().stream().sorted().map(contingencyElementByBranch::get).collect(Collectors.toList());

            // we confirm the breaking of connectivity by network connectivity
            // the traversal order of the set must be deterministic to ensure consistent element selection when multiple elements can restore connectivity
            // without this, fast DC post contingency states may vary for buses disconnected from main connected component, depending on which elements were selected
            LinkedHashSet<ComputedContingencyElement> breakingConnectivityElements;
            connectivity.startTemporaryChanges();
            try {
                ComputedContingencyElement.applyToConnectivity(lfNetwork, connectivity, breakingConnectivityCandidates);
                // filter the branches that really impacts connectivity
                breakingConnectivityElements = breakingConnectivityCandidates.stream()
                        .filter(element -> isBreakingConnectivity(connectivity, element))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (breakingConnectivityElements.isEmpty()) {
                    // we did not break any connectivity
                    nonBreakingConnectivityContingencies.add(contingency);
                } else {
                    // only compute for factors that have to be computed for this contingency lost
                    Set<String> elementsToReconnect = computeElementsToReconnect(connectivity, breakingConnectivityElements);
                    int createdSynchronousComponents = connectivity.getNbConnectedComponents() - 1;
                    Set<LfBus> disabledBuses = connectivity.getVerticesRemovedFromMainComponent();
                    Set<LfHvdc> hvdcsWithoutPower = PropagatedContingency.getHvdcsWithoutPower(lfNetwork, disabledBuses, connectivity);
                    ConnectivityAnalysisResult connectivityAnalysisResult = new ConnectivityAnalysisResult(contingency, lfNetwork, elementsToReconnect,
                            new DisabledElements(disabledBuses, connectivity.getEdgesRemovedFromMainComponent(), hvdcsWithoutPower),
                            connectivity.getConnectedComponent(lfNetwork.getSlackBus()), createdSynchronousComponents);
                    connectivityAnalysisResults.add(connectivityAnalysisResult);
                }
            } finally {
                connectivity.undoTemporaryChanges();
            }
        }
        return connectivityAnalysisResults;
    }

    private static boolean isBreakingConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity, ComputedContingencyElement element) {
        LfBranch lfBranch = element.getLfBranch();
        return connectivity.getComponentNumber(lfBranch.getBus1()) != connectivity.getComponentNumber(lfBranch.getBus2());
    }

    /**
     * Given the elements breaking the connectivity, extract the minimum number of elements which reconnect all connected components together
     */
    private static Set<String> computeElementsToReconnect(GraphConnectivity<LfBus, LfBranch> connectivity, Set<ComputedContingencyElement> breakingConnectivityElements) {
        Set<String> elementsToReconnect = new LinkedHashSet<>();

        // We suppose we're reconnecting one by one each element breaking connectivity.
        // At each step we look if the reconnection was needed on the connectivity level by maintaining a list of grouped connected components.
        List<Set<Integer>> reconnectedCc = new ArrayList<>();
        for (ComputedContingencyElement element : breakingConnectivityElements) {
            int cc1 = connectivity.getComponentNumber(element.getLfBranch().getBus1());
            int cc2 = connectivity.getComponentNumber(element.getLfBranch().getBus2());

            Set<Integer> recCc1 = reconnectedCc.stream().filter(s -> s.contains(cc1)).findFirst().orElseGet(() -> new HashSet<>(List.of(cc1)));
            Set<Integer> recCc2 = reconnectedCc.stream().filter(s -> s.contains(cc2)).findFirst().orElseGet(() -> Set.of(cc2));
            if (recCc1 != recCc2) {
                // cc1 and cc2 are still separated:
                // - mark the element as needed to reconnect all connected components together
                // - update the list of grouped connected components
                elementsToReconnect.add(element.getElement().getId());
                reconnectedCc.remove(recCc2);
                if (recCc1.size() == 1) {
                    // adding the new set (the list of grouped connected components is not initialized with the singleton sets)
                    reconnectedCc.add(recCc1);
                }
                recCc1.addAll(recCc2);
            }
        }

        if (reconnectedCc.size() != 1 || reconnectedCc.get(0).size() != connectivity.getNbConnectedComponents()) {
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

        // connectivity analysis by contingency
        // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
        // a contingency involving a phase tap changer loss has to be processed separately
        List<PropagatedContingency> nonBreakingConnectivityContingencies = new ArrayList<>();
        List<PropagatedContingency> potentiallyBreakingConnectivityContingencies = new ArrayList<>();

        // this first method based on sensitivity criteria is able to detect some contingencies that do not break
        // connectivity and other contingencies that potentially break connectivity
        detectPotentialConnectivityBreak(loadFlowContext.getNetwork(), contingenciesStates, contingencies, contingencyElementByBranch, loadFlowContext.getEquationSystem(),
                nonBreakingConnectivityContingencies, potentiallyBreakingConnectivityContingencies);
        LOGGER.info("After sensitivity based connectivity analysis, {} contingencies do not break connectivity, {} contingencies potentially break connectivity",
                nonBreakingConnectivityContingencies.size(), potentiallyBreakingConnectivityContingencies.size());

        // this second method process all contingencies that potentially break connectivity and using graph algorithms
        // find remaining contingencies that do not break connectivity
        List<ConnectivityAnalysisResult> connectivityAnalysisResults = computeConnectivityData(loadFlowContext.getNetwork(),
                potentiallyBreakingConnectivityContingencies, contingencyElementByBranch, nonBreakingConnectivityContingencies);
        LOGGER.info("After graph based connectivity analysis, {} contingencies do not break connectivity, {} contingencies break connectivity",
                nonBreakingConnectivityContingencies.size(), connectivityAnalysisResults.size());

        return new ConnectivityBreakAnalysisResults(nonBreakingConnectivityContingencies, connectivityAnalysisResults, contingenciesStates, contingencyElementByBranch);
    }
}
