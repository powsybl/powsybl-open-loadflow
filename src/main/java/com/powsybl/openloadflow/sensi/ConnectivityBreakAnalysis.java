/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

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

    public static final class ConnectivityAnalysisResult {

        private PropagatedContingency contingency;

        private final Set<String> elementsToReconnect;

        private final Set<LfBus> disabledBuses;

        private final Set<LfBus> slackConnectedComponent;

        private final Set<LfBranch> partialDisabledBranches; // branches disabled because of connectivity loss.

        private ConnectivityAnalysisResult(Set<String> elementsToReconnect,
                                           GraphConnectivity<LfBus, LfBranch> connectivity,
                                           LfNetwork lfNetwork) {
            this.elementsToReconnect = elementsToReconnect;
            slackConnectedComponent = connectivity.getConnectedComponent(lfNetwork.getSlackBus());
            disabledBuses = connectivity.getVerticesRemovedFromMainComponent();
            partialDisabledBranches = connectivity.getEdgesRemovedFromMainComponent();
        }

        public PropagatedContingency getPropagatedContingency() {
            return contingency;
        }

        public void setPropagatedContingency(PropagatedContingency contingency) {
            this.contingency = contingency;
        }

        public Set<String> getElementsToReconnect() {
            return elementsToReconnect;
        }

        public Set<LfBus> getDisabledBuses() {
            return disabledBuses;
        }

        public Set<LfBus> getSlackConnectedComponent() {
            return slackConnectedComponent;
        }

        public Set<LfBranch> getPartialDisabledBranches() {
            return partialDisabledBranches;
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
            List<ComputedContingencyElement> breakingConnectivityCandidates = contingency.getBranchIdsToOpen().keySet().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());

            // we confirm the breaking of connectivity by network connectivity
            Set<ComputedContingencyElement> breakingConnectivityElements;
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
                    ConnectivityAnalysisResult connectivityAnalysisResult = new ConnectivityAnalysisResult(elementsToReconnect, connectivity, lfNetwork);
                    connectivityAnalysisResult.setPropagatedContingency(contingency);
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
        return run(loadFlowContext, contingencies, List.of());
    }

    public static ConnectivityBreakAnalysisResults run(DcLoadFlowContext loadFlowContext, List<PropagatedContingency> contingencies, List<LfAction> actions) {
        // index contingency elements by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = createContingencyElementsIndexByBranchId(contingencies, loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem());

        // TODO : index actions elements by branch id

        // compute states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(loadFlowContext, contingencyElementByBranch.values());

        // TODO : compute states with +1 -1 to model the actions

        // connectivity analysis by contingency
        // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
        // a contingency involving a phase tap changer loss has to be processed separately
        // TODO : replace the following by couples of (PropagatedContingency, List<LfAction>)
        List<PropagatedContingency> nonBreakingConnectivityContingencies = new ArrayList<>();
        List<PropagatedContingency> potentiallyBreakingConnectivityContingencies = new ArrayList<>();

        // this first method based on sensitivity criteria is able to detect some contingencies that do not break
        // connectivity and other contingencies that potentially break connectivity
        // TODO : Detect potential connectivity break with actions taken into account, so usage of (PropagatedContingency, List<LfAction>) instead of PropagatedContingency
        detectPotentialConnectivityBreak(loadFlowContext.getNetwork(), contingenciesStates, contingencies, contingencyElementByBranch, loadFlowContext.getEquationSystem(),
                nonBreakingConnectivityContingencies, potentiallyBreakingConnectivityContingencies);
        LOGGER.info("After sensitivity based connectivity analysis, {} contingencies do not break connectivity, {} contingencies potentially break connectivity",
                nonBreakingConnectivityContingencies.size(), potentiallyBreakingConnectivityContingencies.size());

        // this second method process all contingencies that potentially break connectivity and using graph algorithms
        // find remaining contingencies that do not break connectivity
        // TODO : Detect connectivity break with actions taken into account, so usage of (PropagatedContingency, List<LfAction>) instead of PropagatedContingency
        List<ConnectivityAnalysisResult> connectivityAnalysisResults = computeConnectivityData(loadFlowContext.getNetwork(),
                potentiallyBreakingConnectivityContingencies, contingencyElementByBranch, nonBreakingConnectivityContingencies);
        LOGGER.info("After graph based connectivity analysis, {} contingencies do not break connectivity, {} contingencies break connectivity",
                nonBreakingConnectivityContingencies.size(), connectivityAnalysisResults.size());

        // TODO : Refactor the ConnectivityAnalysisResult to take into account the actions
        // TODO : modify format of nonBreakingConnectivityContingencies to accept couples of things which do not break connectivity
        // TODO : pay attention to the fact that nonBreakingConnectivityContingencies is used in many places. This should still be accessible
        return new ConnectivityBreakAnalysisResults(nonBreakingConnectivityContingencies, connectivityAnalysisResults, contingenciesStates, contingencyElementByBranch);
    }
}
