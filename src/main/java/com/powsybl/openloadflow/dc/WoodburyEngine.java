/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ObjDoubleConsumer;
import java.util.stream.Collectors;

/**
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngine {

    protected static final Logger LOGGER = LoggerFactory.getLogger(WoodburyEngine.class);
    private static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-7;
    private WoodburyEngineResult woodburyEngineResult;

    public static final class ComputedContingencyElement {

        private int contingencyIndex = -1; // index of the element in the rhs for +1-1
        private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
        private double alphaForStateValue = Double.NaN;
        private double alphaForFunctionReference = Double.NaN;
        private final ContingencyElement element;
        private final LfBranch lfBranch;
        private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

        private ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
            this.element = element;
            lfBranch = lfNetwork.getBranchById(element.getId());
            branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        private int getContingencyIndex() {
            return contingencyIndex;
        }

        private void setContingencyIndex(final int index) {
            this.contingencyIndex = index;
        }

        private int getLocalIndex() {
            return localIndex;
        }

        private void setLocalIndex(final int index) {
            this.localIndex = index;
        }

        private double getAlphaForStateValue() {
            return alphaForStateValue;
        }

        private void setAlphaForStateValue(final double alpha) {
            this.alphaForStateValue = alpha;
        }

        private double getAlphaForFunctionReference() {
            return alphaForFunctionReference;
        }

        private void setAlphaForFunctionReference(final double alpha) {
            this.alphaForFunctionReference = alpha;
        }

        private ContingencyElement getElement() {
            return element;
        }

        private LfBranch getLfBranch() {
            return lfBranch;
        }

        private ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
            return branchEquation;
        }

        private static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index++);
            }
        }

        private static void setLocalIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setLocalIndex(index++);
            }
        }

    }

    private static final class PhaseTapChangerContingenciesIndexing {

        private final List<PropagatedContingency> contingenciesWithoutTransformers = new ArrayList<>();
        private final Map<Set<LfBranch>, Collection<PropagatedContingency>> contingenciesIndexedByPhaseTapChangers = new LinkedHashMap<>();

        private PhaseTapChangerContingenciesIndexing(Collection<PropagatedContingency> contingencies,
                                                     Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                     Collection<String> elementIdsToSkip) {
            for (PropagatedContingency contingency : contingencies) {
                Set<LfBranch> lostTransformers = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementIdsToSkip.contains(element))
                        .map(contingencyElementByBranch::get)
                        .map(ComputedContingencyElement::getLfBranch)
                        .filter(LfBranch::hasPhaseControllerCapability)
                        .collect(Collectors.toSet());
                if (lostTransformers.isEmpty()) {
                    contingenciesWithoutTransformers.add(contingency);
                } else {
                    contingenciesIndexedByPhaseTapChangers.computeIfAbsent(lostTransformers, key -> new ArrayList<>()).add(contingency);
                }
            }
        }

        private Collection<PropagatedContingency> getContingenciesWithoutPhaseTapChangerLoss() {
            return contingenciesWithoutTransformers;
        }

        private Map<Set<LfBranch>, Collection<PropagatedContingency>> getContingenciesIndexedByPhaseTapChangers() {
            return contingenciesIndexedByPhaseTapChangers;
        }
    }

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

        private Set<String> getElementsToReconnect() {
            return elementsToReconnect;
        }

        public Set<LfBus> getDisabledBuses() {
            return disabledBuses;
        }

        public Set<LfBus> getSlackConnectedComponent() {
            return slackConnectedComponent;
        }

        private Set<LfBranch> getPartialDisabledBranches() {
            return partialDisabledBranches;
        }
    }

//    /**
//     * Calculate the states for a fictitious pre-contingency situation.
//     * The interesting disabled branches are only phase shifters.
//     */
//    private DenseMatrix calculatePreContingencyStates(DcLoadFlowContext loadFlowContext,
//                                               List<ParticipatingElement> participatingElements,
//                                               DisabledNetwork disabledNetwork, ReportNode reporter,
//                                               boolean updateFlowStates) {
//        List<BusState> busStates = Collections.emptyList();
//        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
//        if (parameters.isDistributedSlack()) {
//            busStates = ElementState.save(participatingElements.stream()
//                    .map(ParticipatingElement::getLfBus)
//                    .collect(Collectors.toSet()), BusState::save);
//        }
//
//        double[] dx = runDcLoadFlow(loadFlowContext, disabledNetwork, reporter);
//
//        if (updateFlowStates) {
//            woodburyEngineResult.setPreContingenciesFlowStates(dx);
//        }
//
//        if (parameters.isDistributedSlack()) {
//            ElementState.restore(busStates);
//        }
//
//        return new DenseMatrix(dx.length, 1, dx);
//    }
//
//    /**
//     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling and that do not
//     * update the state vector and the network at the end (because we don't need it to just evaluate a few equations)
//     */
//    public double[] runDcLoadFlow(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork, ReportNode reporter) {
//
//        Collection<LfBus> remainingBuses;
//        if (disabledNetwork.getBuses().isEmpty()) {
//            remainingBuses = loadFlowContext.getNetwork().getBuses();
//        } else {
//            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
//            remainingBuses.removeAll(disabledNetwork.getBuses());
//        }
//
//        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
//        if (parameters.isDistributedSlack()) {
//            DcLoadFlowEngine.distributeSlack(remainingBuses, parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
//        }
//
//        // we need to copy the target array because:
//        //  - in case of disabled buses or branches some elements could be overwritten to zero
//        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
//        //    to fill with the solution
//        // so we need to copy to later the target as it is and reusable for next run
//        var targetVectorArray = loadFlowContext.getTargetVector().getArray().clone();
//
//        if (!disabledNetwork.getBuses().isEmpty()) {
//            // set buses injections and transformers to 0
//            disabledNetwork.getBuses().stream()
//                    .flatMap(lfBus -> loadFlowContext.getEquationSystem().getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
//                    .map(Equation::getColumn)
//                    .forEach(column -> targetVectorArray[column] = 0);
//        }
//
//        if (!disabledNetwork.getBranches().isEmpty()) {
//            // set transformer phase shift to 0
//            disabledNetwork.getBranches().stream()
//                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
//                    .map(Equation::getColumn)
//                    .forEach(column -> targetVectorArray[column] = 0);
//        }
//
//        boolean succeeded = DcLoadFlowEngine.solve(targetVectorArray, loadFlowContext.getJacobianMatrix(), reporter);
//        if (!succeeded) {
//            throw new PowsyblException("DC solver failed");
//        }
//
//        return targetVectorArray; // now contains dx
//    }

    private static double calculatePower(DcLoadFlowContext loadFlowContext, LfBranch lfBranch) {
        PiModel piModel = lfBranch.getPiModel();
        DcEquationSystemCreationParameters creationParameters = loadFlowContext.getParameters().getEquationSystemCreationParameters();
        return AbstractClosedBranchDcFlowEquationTerm.calculatePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    private static void setAlphas(DcLoadFlowContext loadFlowContext, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix states,
                                  DenseMatrix contingenciesStates, int columnState, ObjDoubleConsumer<ComputedContingencyElement> setValue) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = 1d / calculatePower(loadFlowContext, lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            setValue.accept(element, b / a);
        } else {
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState)
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = 1d / calculatePower(loadFlowContext, lfBranch);
                    }
                    value = value - (contingenciesStates.get(p1.getPh1Var().getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> setValue.accept(element, rhs.get(element.getLocalIndex(), 0)));
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

    private static DenseMatrix calculateContingenciesStates(DcLoadFlowContext loadFlowContext, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
        DenseMatrix contingenciesStates = initContingencyRhs(loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem(), contingencyElementByBranch.values()); // rhs with +1 -1 on contingency elements
        loadFlowContext.getJacobianMatrix().solveTransposed(contingenciesStates);
        return contingenciesStates;
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

    private List<ConnectivityAnalysisResult> computeConnectivityData(LfNetwork lfNetwork, Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity,
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
                ConnectivityAnalysisResult connectivityAnalysisResult = connectivityAnalysisResults.computeIfAbsent(breakingConnectivityElements, k -> {
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

    private void processContingenciesBreakingConnectivity(ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext, DenseMatrix preContingencyStates,
                                                          DenseMatrix contingenciesStates, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                          List<ParticipatingElement> participatingElements,
                                                          ReportNode reporter, WoodburyEngineRhs rhsModification) {
        Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();
        Set<LfBranch> partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();

        // null and unused if slack bus is not distributed
        List<ParticipatingElement> participatingElementsForThisConnectivity = rhsModification.getNewParticipantElementsForAConnectivity().getOrDefault(connectivityAnalysisResult, participatingElements);
        DenseMatrix statesForThisConnectivity;
        if (rhsModification.getNewInjectionVectorsForAConnectivity().containsKey(connectivityAnalysisResult)) {
            statesForThisConnectivity = rhsModification.getNewInjectionVectorsForAConnectivity().get(connectivityAnalysisResult);
            loadFlowContext.getJacobianMatrix().solveTransposed(statesForThisConnectivity);
        } else {
            statesForThisConnectivity = preContingencyStates;
        }

//        DenseMatrix modifiedFlowStates = calculatePreContingencyStates(loadFlowContext, participatingElementsForThisConnectivity,
//                new DisabledNetwork(disabledBuses, Collections.emptySet()),
//                reporter, false); // TODO : remove me
        calculateStateValuesForContingencyList(loadFlowContext, contingenciesStates, new DenseMatrix(woodburyEngineResult.getPreContingenciesFlowStates().length, 1, woodburyEngineResult.getPreContingenciesFlowStates()), statesForThisConnectivity, connectivityAnalysisResult.getContingencies(),
                contingencyElementByBranch, disabledBuses, participatingElementsForThisConnectivity, connectivityAnalysisResult.getElementsToReconnect(),
                reporter, partialDisabledBranches, rhsModification);
    }

    private void calculateStateValuesForContingencyList(DcLoadFlowContext loadFlowContext, DenseMatrix contingenciesStates, DenseMatrix flowStates,
                                                        DenseMatrix preContingencyStates, Collection<PropagatedContingency> contingencies, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                        Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements, Set<String> elementsToReconnect,
                                                        ReportNode reporter, Set<LfBranch> partialDisabledBranches, WoodburyEngineRhs input) {
        // TODO : see if we can not merge the two for loops
        PhaseTapChangerContingenciesIndexing phaseTapChangerContingenciesIndexing = new PhaseTapChangerContingenciesIndexing(contingencies, contingencyElementByBranch, elementsToReconnect);

        var lfNetwork = loadFlowContext.getNetwork();

        // compute states without loss of phase tap changer
        // first we compute the ones without loss of phase tap changers (because we reuse the load flows from the pre contingency network for all of them)
        for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .toList();

            Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
            disabledBranches.addAll(partialDisabledBranches);

            calculateContingencyStateValues(loadFlowContext, flowStates, preContingencyStates, contingency, contingenciesStates, contingencyElements, // TODO : remove null
                    new DisabledNetwork(disabledBuses, disabledBranches), reporter, input);
        }

        // then we compute the ones involving the loss of a phase tap changer (because we need to recompute the load flows)
        for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> e : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
            Set<LfBranch> disabledPhaseTapChangers = e.getKey();
            Collection<PropagatedContingency> propagatedContingencies = e.getValue();
//            modifiedFlowStates = calculatePreContingencyStates(loadFlowContext, participatingElements,
//                    new DisabledNetwork(disabledBuses, disabledPhaseTapChangers), reporter, false); // TODO : remove

            for (PropagatedContingency contingency : propagatedContingencies) {
                Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementsToReconnect.contains(element))
                        .map(contingencyElementByBranch::get)
                        .toList();
//
                Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
                disabledBranches.addAll(partialDisabledBranches);

                calculateContingencyStateValues(loadFlowContext, flowStates, preContingencyStates, contingency, contingenciesStates, // TODO : modify flowStates for modifiedFlowStates
                        contingencyElements, new DisabledNetwork(disabledBuses, disabledBranches), reporter, input);
            }
        }
    }

    /**
     * Calculate values for a post-contingency state.
     * When a contingency involves the loss of a load or a generator, the slack distribution could changed
     * or the sensitivity factors in case of a glsk sensitivity calculation.
     */
    private void calculateContingencyStateValues(DcLoadFlowContext loadFlowContext, DenseMatrix flowStates, DenseMatrix preContingencyStates,
                                                 PropagatedContingency contingency, DenseMatrix contingenciesStates, Collection<ComputedContingencyElement> contingencyElements, DisabledNetwork disabledNetwork,
                                                 ReportNode reporter, WoodburyEngineRhs input) {

        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
            calculateStateValues(loadFlowContext, flowStates, preContingencyStates, contingenciesStates, contingency, contingencyElements, disabledNetwork); // TODO : remove
        } else {
            LfNetwork lfNetwork = loadFlowContext.getNetwork();
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            DenseMatrix newPreContingencyStates = preContingencyStates;

            if (lfContingency != null) {
                if (input.getNewInjectionVectorsByPropagatedContingency().containsKey(contingency)) {
                    newPreContingencyStates = input.getNewInjectionVectorsByPropagatedContingency().get(contingency);
                    loadFlowContext.getJacobianMatrix().solveTransposed(newPreContingencyStates);
                }
            }

            double[] tempo = input.getNewFlowRhsByPropagatedContingecy().get(contingency);
            DcLoadFlowEngine.solve(tempo, loadFlowContext.getJacobianMatrix(), reporter);
            DenseMatrix newFlowStates = new DenseMatrix(preContingencyStates.getRowCount(), 1, tempo);

            calculateStateValues(loadFlowContext, newFlowStates, newPreContingencyStates, contingenciesStates, contingency, contingencyElements,
                    disabledNetwork);
        }
    }

    /**
     * Calculate values for post-contingency state using the pre-contingency state value and some flow transfer factors (alphas).
     */
    private void calculateStateValues(DcLoadFlowContext loadFlowContext, DenseMatrix flowStates, DenseMatrix preContingencyStates, DenseMatrix contingenciesStates,
                                      PropagatedContingency contingency, Collection<ComputedContingencyElement> contingencyElements, DisabledNetwork disabledNetwork) {

        // add the post contingency matrices of flow states
        DenseMatrix postContingencyFlowStates = new DenseMatrix(flowStates.getRowCount(), 1);
        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
        for (int rowIndex = 0; rowIndex < flowStates.getRowCount(); rowIndex++) {
            double postContingencyFlowValue = flowStates.get(rowIndex, 0);
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                postContingencyFlowValue += contingencyElement.getAlphaForFunctionReference() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
            }
            postContingencyFlowStates.set(rowIndex, 0, postContingencyFlowValue);
        }

        // add the post contingency matrices of the states
        DenseMatrix postContingencyStates = new DenseMatrix(preContingencyStates.getRowCount(), preContingencyStates.getColumnCount());
        for (int columnIndex = 0; columnIndex < preContingencyStates.getColumnCount(); columnIndex++) {
            setAlphas(loadFlowContext, contingencyElements, preContingencyStates, contingenciesStates, columnIndex, ComputedContingencyElement::setAlphaForStateValue);
            for (int rowIndex = 0; rowIndex < preContingencyStates.getRowCount(); rowIndex++) {
                double postContingencyValue = preContingencyStates.get(rowIndex, columnIndex);
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForStateValue() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
                }
                postContingencyStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }

        WoodburyEngineResult.PostContingencyWoodburyResult postContingencyWoodburyResult =
                new WoodburyEngineResult.PostContingencyWoodburyResult(postContingencyFlowStates, postContingencyStates, disabledNetwork);
        woodburyEngineResult.getPostContingencyWoodburyResults().put(contingency, postContingencyWoodburyResult);
    }

    private static Map<String, ComputedContingencyElement> createContingencyElementsIndexByBranchId(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, List<PropagatedContingency> contingencies) {
        Map<String, WoodburyEngine.ComputedContingencyElement> contingencyElementByBranch =
                contingencies.stream()
                        .flatMap(contingency -> contingency.getBranchIdsToOpen().keySet().stream())
                        .map(branch -> new WoodburyEngine.ComputedContingencyElement(new BranchContingency(branch), lfNetwork, equationSystem))
                        .filter(element -> element.getLfBranchEquation() != null)
                        .collect(Collectors.toMap(
                                computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                computedContingencyElement -> computedContingencyElement,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ));
        WoodburyEngine.ComputedContingencyElement.setContingencyIndexes(contingencyElementByBranch.values());
        return contingencyElementByBranch;
    }

    public record ConnectivityDataResult(List<PropagatedContingency> nonBreakingConnectivityContingencies,
                                         List<ConnectivityAnalysisResult> connectivityAnalysisResults,
                                         DenseMatrix contingenciesStates,
                                         Map<String, ComputedContingencyElement> contingencyElementByBranch) {

    }

    public ConnectivityDataResult runConnectivityData(DcLoadFlowContext loadFlowContext, List<PropagatedContingency> contingencies) {
        woodburyEngineResult = new WoodburyEngineResult();

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

        return new ConnectivityDataResult(nonBreakingConnectivityContingencies, connectivityAnalysisResults, contingenciesStates, contingencyElementByBranch);
    }

    public WoodburyEngineResult run(DcLoadFlowContext loadFlowContext, WoodburyEngineRhs input, List<ParticipatingElement> participatingElements,
                                    ReportNode reporter, ConnectivityDataResult connectivityDataResult) {

        // compute the pre-contingency states
        DenseMatrix injectionRhs = input.getInitialInjectionRhs();
        loadFlowContext.getJacobianMatrix().solveTransposed(injectionRhs);
        woodburyEngineResult.setPreContingenciesStates(injectionRhs);

        double[] flowRhs = input.getInitialFlowRhs();
        DcLoadFlowEngine.solve(flowRhs, loadFlowContext.getJacobianMatrix(), reporter);
        woodburyEngineResult.setPreContingenciesFlowStates(flowRhs);

        // get contingency elements indexed by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = connectivityDataResult.contingencyElementByBranch();

        // get states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = connectivityDataResult.contingenciesStates();

        LOGGER.info("Processing contingencies with no connectivity break");

        // calculate state values for contingencies with no connectivity break
        calculateStateValuesForContingencyList(loadFlowContext, contingenciesStates, new DenseMatrix(flowRhs.length, 1, flowRhs), injectionRhs, connectivityDataResult.nonBreakingConnectivityContingencies(), contingencyElementByBranch,
                Collections.emptySet(), participatingElements, Collections.emptySet(), reporter, Collections.emptySet(), input);

        LOGGER.info("Processing contingencies with connectivity break");

        // process contingencies with connectivity break
        for (ConnectivityAnalysisResult connectivityAnalysisResult : connectivityDataResult.connectivityAnalysisResults()) {
            processContingenciesBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, injectionRhs, contingenciesStates, contingencyElementByBranch, participatingElements, reporter, input);
        }

        return woodburyEngineResult;
    }
}
