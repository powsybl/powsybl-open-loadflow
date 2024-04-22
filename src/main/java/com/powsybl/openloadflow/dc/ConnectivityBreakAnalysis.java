/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectivityBreakAnalysis.class);
    private static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-7;

    public static final class ConnectivityAnalysisResult {

        private final Collection<PropagatedContingency> contingencies = new HashSet<>();

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

        public Collection<PropagatedContingency> getContingencies() {
            return contingencies;
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

    private static Map<String, ComputedContingencyElement> createContingencyElementsIndexByBranchId(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, List<PropagatedContingency> contingencies) {
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
        ComputedContingencyElement.setContingencyIndexes(contingencyElementByBranch.values());
        return contingencyElementByBranch;
    }

    private static void detectPotentialConnectivityBreak(LfNetwork lfNetwork, DenseMatrix states, List<PropagatedContingency> contingencies,
                                                         Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                         EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                         Collection<PropagatedContingency> nonLosingConnectivityContingencies,
                                                         Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity) {
        for (PropagatedContingency contingency : contingencies) {
            List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream().map(contingencyElementByBranch::get).toList();
            Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = getGroupOfElementsBreakingConnectivity(lfNetwork, states, contingencyElements, equationSystem);
            if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
                nonLosingConnectivityContingencies.add(contingency);
            } else {
                contingenciesByGroupOfElementsBreakingConnectivity.computeIfAbsent(groupOfElementsBreakingConnectivity, key -> new LinkedList<>()).add(contingency);
            }
        }
    }

    private static Set<ComputedContingencyElement> getGroupOfElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingenciesStates,
                                                                                                         Collection<ComputedContingencyElement> contingencyElements,
                                                                                                         EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity after a contingency
        // we consider a +1 -1 on a line, and we observe the sensitivity of these injections on the other contingency elements
        // if the sum of the sensitivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // thus, losing these lines will lose the connectivity
        Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = new LinkedHashSet<>();
        for (ComputedContingencyElement element : contingencyElements) {
            Set<ComputedContingencyElement> responsibleElements = new LinkedHashSet<>();
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(ElementType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculateSensi(contingenciesStates, element.getContingencyIndex()));
                if (value > CONNECTIVITY_LOSS_THRESHOLD) {
                    responsibleElements.add(element2);
                }
                sum += value;
            }
            if (sum > 1d - CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                groupOfElementsBreakingConnectivity.addAll(responsibleElements);
            }
        }
        return groupOfElementsBreakingConnectivity;
    }

    private static List<ConnectivityAnalysisResult> computeConnectivityData(LfNetwork lfNetwork, Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity,
                                                                            List<PropagatedContingency> nonLosingConnectivityContingencies) {
        if (contingenciesByGroupOfElementsBreakingConnectivity.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> connectivityAnalysisResults = new LinkedHashMap<>();

        GraphConnectivity<LfBus, LfBranch> connectivity = lfNetwork.getConnectivity();
        for (Map.Entry<Set<ComputedContingencyElement>, List<PropagatedContingency>> e : contingenciesByGroupOfElementsBreakingConnectivity.entrySet()) {
            Set<ComputedContingencyElement> breakingConnectivityCandidates = e.getKey();
            List<PropagatedContingency> contingencyList = e.getValue();
            connectivity.startTemporaryChanges();
            breakingConnectivityCandidates.stream()
                    .map(ComputedContingencyElement::getElement)
                    .map(ContingencyElement::getId)
                    .distinct()
                    .map(lfNetwork::getBranchById)
                    .filter(b -> b.getBus1() != null && b.getBus2() != null)
                    .forEach(connectivity::removeEdge);

            // filter the branches that really impacts connectivity
            Set<ComputedContingencyElement> breakingConnectivityElements = breakingConnectivityCandidates.stream()
                    .filter(element -> isBreakingConnectivity(connectivity, element))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (breakingConnectivityElements.isEmpty()) {
                // we did not break any connectivity
                nonLosingConnectivityContingencies.addAll(contingencyList);
            } else {
                ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult = connectivityAnalysisResults.computeIfAbsent(breakingConnectivityElements, k -> {
                    Set<String> elementsToReconnect = computeElementsToReconnect(connectivity, breakingConnectivityElements);
                    return new ConnectivityAnalysisResult(elementsToReconnect, connectivity, lfNetwork);
                });
                connectivityAnalysisResult.getContingencies().addAll(contingencyList);
            }
            connectivity.undoTemporaryChanges();
        }
        return new ArrayList<>(connectivityAnalysisResults.values());
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

    /**
     * Fills the right hand side with +1/-1 to model a branch contingency.
     */
    private static void fillRhsContingency(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                           Collection<ComputedContingencyElement> contingencyElements, Matrix rhs) {
        for (ComputedContingencyElement element : contingencyElements) {
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                continue;
            }
            LfBus bus1 = lfBranch.getBus1();
            LfBus bus2 = lfBranch.getBus2();
            if (bus1.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), -1);
            } else if (bus2.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), 1);
            } else {
                Equation<DcVariableType, DcEquationType> p1 = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                Equation<DcVariableType, DcEquationType> p2 = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p1.getColumn(), element.getContingencyIndex(), 1);
                rhs.set(p2.getColumn(), element.getContingencyIndex(), -1);
            }
        }
    }

    public static DenseMatrix initContingencyRhs(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<ComputedContingencyElement> contingencyElements) {
        // otherwise, defining the rhs matrix will result in integer overflow
        int equationCount = equationSystem.getIndex().getSortedEquationsToSolve().size();
        int maxContingencyElements = Integer.MAX_VALUE / (equationCount * Double.BYTES);
        if (contingencyElements.size() > maxContingencyElements) {
            throw new PowsyblException("Too many contingency elements " + contingencyElements.size()
                    + ", maximum is " + maxContingencyElements + " for a system with " + equationCount + " equations");
        }

        DenseMatrix rhs = new DenseMatrix(equationCount, contingencyElements.size());
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    static DenseMatrix calculateContingenciesStates(DcLoadFlowContext loadFlowContext, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
        DenseMatrix contingenciesStates = initContingencyRhs(loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem(), contingencyElementByBranch.values()); // rhs with +1 -1 on contingency elements
        loadFlowContext.getJacobianMatrix().solveTransposed(contingenciesStates);
        return contingenciesStates;
    }

    public static ConnectivityBreakAnalysisResults run(DcLoadFlowContext loadFlowContext, List<PropagatedContingency> contingencies) {
        // index contingency elements by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = createContingencyElementsIndexByBranchId(loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem(), contingencies);

        // compute states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = calculateContingenciesStates(loadFlowContext, contingencyElementByBranch);

        // connectivity analysis by contingency
        // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
        // so, we will index contingencies by a list of branch that may break connectivity
        // for example, if in the network, loosing line L1 breaks connectivity, and loosing L2 and L3 together breaks connectivity,
        // the index would be: L1, L2, L3
        // a contingency involving a phase tap changer loss has to be processed separately
        List<PropagatedContingency> nonBreakingConnectivityContingencies = new ArrayList<>();
        Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsPotentiallyBreakingConnectivity = new LinkedHashMap<>();

        // this first method based on sensitivity criteria is able to detect some contingencies that do not break
        // connectivity and other contingencies that potentially break connectivity
        detectPotentialConnectivityBreak(loadFlowContext.getNetwork(), contingenciesStates, contingencies, contingencyElementByBranch, loadFlowContext.getEquationSystem(),
                nonBreakingConnectivityContingencies, contingenciesByGroupOfElementsPotentiallyBreakingConnectivity);
        LOGGER.info("After sensitivity based connectivity analysis, {} contingencies do not break connectivity, {} contingencies potentially break connectivity",
                nonBreakingConnectivityContingencies.size(), contingenciesByGroupOfElementsPotentiallyBreakingConnectivity.values().stream().mapToInt(List::size).count());

        // this second method process all contingencies that potentially break connectivity and using graph algorithms
        // find remaining contingencies that do not break connectivity
        List<ConnectivityAnalysisResult> connectivityAnalysisResults = computeConnectivityData(loadFlowContext.getNetwork(), contingenciesByGroupOfElementsPotentiallyBreakingConnectivity,
                nonBreakingConnectivityContingencies);
        LOGGER.info("After graph based connectivity analysis, {} contingencies do not break connectivity, {} contingencies break connectivity",
                nonBreakingConnectivityContingencies.size(), connectivityAnalysisResults.stream().mapToInt(results -> results.getContingencies().size()).count());

        return new ConnectivityBreakAnalysisResults(nonBreakingConnectivityContingencies, connectivityAnalysisResults, contingenciesStates, contingencyElementByBranch);
    }

}
