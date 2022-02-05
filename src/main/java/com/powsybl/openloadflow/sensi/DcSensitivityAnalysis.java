/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.AbstractLfBus;
import com.powsybl.openloadflow.network.impl.LfGeneratorImpl;
import com.powsybl.openloadflow.network.impl.LfVscConverterStationImpl;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.PropagatedContingency;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis<DcVariableType, DcEquationType> {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-7;

    static class ComputedContingencyElement {

        private int contingencyIndex = -1; // index of the element in the rhs for +1-1
        private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
        private double alphaForSensitivityValue = Double.NaN;
        private double alphaForFunctionReference = Double.NaN;
        private final ContingencyElement element;
        private final LfBranch lfBranch;
        private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

        public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
            this.element = element;
            lfBranch = lfNetwork.getBranchById(element.getId());
            branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        public int getContingencyIndex() {
            return contingencyIndex;
        }

        public void setContingencyIndex(final int index) {
            this.contingencyIndex = index;
        }

        public int getLocalIndex() {
            return localIndex;
        }

        public void setLocalIndex(final int index) {
            this.localIndex = index;
        }

        public double getAlphaForSensitivityValue() {
            return alphaForSensitivityValue;
        }

        public void setAlphaForSensitivityValue(final double alpha) {
            this.alphaForSensitivityValue = alpha;
        }

        public double getAlphaForFunctionReference() {
            return alphaForFunctionReference;
        }

        public void setAlphaForFunctionReference(final double alpha) {
            this.alphaForFunctionReference = alpha;
        }

        public ContingencyElement getElement() {
            return element;
        }

        public LfBranch getLfBranch() {
            return lfBranch;
        }

        public ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
            return branchEquation;
        }

        public static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index++);
            }
        }

        public static void setLocalIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setLocalIndex(index++);
            }
        }

    }

    static class PhaseTapChangerContingenciesIndexing {

        private Collection<PropagatedContingency> contingenciesWithoutTransformers;
        private Map<Set<LfBranch>, Collection<PropagatedContingency>> contingenciesIndexedByPhaseTapChangers;

        public PhaseTapChangerContingenciesIndexing(Collection<PropagatedContingency> contingencies, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
            this(contingencies, contingencyElementByBranch, Collections.emptySet());
        }

        public PhaseTapChangerContingenciesIndexing(Collection<PropagatedContingency> contingencies, Map<String,
                ComputedContingencyElement> contingencyElementByBranch, Collection<String> elementIdsToSkip) {
            contingenciesIndexedByPhaseTapChangers = new LinkedHashMap<>();
            contingenciesWithoutTransformers = new ArrayList<>();
            for (PropagatedContingency contingency : contingencies) {
                Set<LfBranch> lostTransformers = contingency.getBranchIdsToOpen().stream()
                        .filter(element -> !elementIdsToSkip.contains(element))
                        .map(contingencyElementByBranch::get)
                        .map(ComputedContingencyElement::getLfBranch)
                        .filter(LfBranch::hasPhaseControlCapability)
                        .collect(Collectors.toSet());
                if (lostTransformers.isEmpty()) {
                    contingenciesWithoutTransformers.add(contingency);
                } else {
                    contingenciesIndexedByPhaseTapChangers.computeIfAbsent(lostTransformers, key -> new ArrayList<>()).add(contingency);
                }
            }
        }

        public Collection<PropagatedContingency> getContingenciesWithoutPhaseTapChangerLoss() {
            return contingenciesWithoutTransformers;
        }

        public Map<Set<LfBranch>, Collection<PropagatedContingency>> getContingenciesIndexedByPhaseTapChangers() {
            return contingenciesIndexedByPhaseTapChangers;
        }
    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        super(matrixFactory, connectivityProvider);
    }

    protected DenseMatrix setReferenceActivePowerFlows(DcLoadFlowEngine dcLoadFlowEngine, EquationSystem<DcVariableType, DcEquationType> equationSystem, JacobianMatrix<DcVariableType, DcEquationType> j,
                                                       List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors, LoadFlowParameters lfParameters,
                                                       List<ParticipatingElement> participatingElements, Collection<LfBus> disabledBuses, Collection<LfBranch> disabledBranches,
                                                       Reporter reporter) {

        List<BusState> busStates = Collections.emptyList();
        if (lfParameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                .map(ParticipatingElement::getLfBus)
                .collect(Collectors.toSet()), BusState::save);
        }
        // the A1 variables will be set to 0 for disabledBranches, so we need to restore them at the end
        List<BranchState> branchStates = ElementState.save(disabledBranches, BranchState::save);

        dcLoadFlowEngine.run(equationSystem, j, disabledBuses, disabledBranches, reporter);

        for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
            factor.setFunctionReference(factor.getFunctionEquationTerm().eval());
        }

        if (lfParameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }
        ElementState.restore(branchStates);

        double[] dx = dcLoadFlowEngine.getTargetVector();
        return new DenseMatrix(dx.length, 1, dx);
    }

    private void createBranchSensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, DenseMatrix contingenciesStates,
                                              Collection<ComputedContingencyElement> contingencyElements,
                                              PropagatedContingency contingency, SensitivityValueWriter valueWriter) {
        boolean predefSensiValue = factor.getSensitivityValuePredefinedResult() != null;
        boolean predefFlowValue = factor.getFunctionPredefinedResult() != null;
        EquationTerm<DcVariableType, DcEquationType> p1 = factor.getFunctionEquationTerm();
        String functionBranchId = factor.getFunctionElement().getId();
        double sensiValue = predefSensiValue ? factor.getSensitivityValuePredefinedResult() : factor.getBaseSensitivityValue();
        double flowValue = predefFlowValue ? factor.getFunctionPredefinedResult() : factor.getFunctionReference();

        if (factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION) {
            sensiValue = 0d;
            predefSensiValue = true;
        }

        if (contingency != null && !contingency.getBranchIdsToOpen().stream().filter(id -> id.equals(functionBranchId)).collect(Collectors.toList()).isEmpty()) {
            // the monitored branch is in contingency, sensitivity and post-contingency flow equals to zero in any case.
            flowValue = 0d;
            sensiValue = 0d;
            predefFlowValue = true;
            predefSensiValue = true;
        }

        boolean zeroSensiValue = false;
        if (!(predefFlowValue && predefSensiValue)) {
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                double contingencySensitivity = p1.calculateSensi(contingenciesStates, contingencyElement.getContingencyIndex());
                if (!predefFlowValue) {
                    flowValue += contingencyElement.getAlphaForFunctionReference() * contingencySensitivity;
                }
                if (!predefSensiValue) {
                    sensiValue += contingencyElement.getAlphaForSensitivityValue() * contingencySensitivity;
                }
                if (contingencyElement.getElement().getId().equals(functionBranchId)) {
                    // the monitored branch is in contingency, sensitivity and post-contingency flow equals to zero in any case.
                    flowValue = 0d;
                    sensiValue = 0d;
                    break;
                }
                if (contingencyElement.getElement().getId().equals(factor.getVariableId())) {
                    // the equipment responsible for the variable is indeed in contingency, the sensitivity value equals to zero.
                    // No assumption about the reference flow on the monitored branch.
                    zeroSensiValue = true;
                }
            }
        }

        if ((contingency != null && contingency.getHvdcIdsToOpen().contains(factor.getVariableId())) || zeroSensiValue) {
            sensiValue = 0d;
        }

        valueWriter.write(factor.getContext(), contingency != null ? contingency.getContingency().getId() : null, contingency != null ? contingency.getIndex() : -1,
                          unscaleSensitivity(factor, sensiValue), unscaleFunction(factor, flowValue));
    }

    protected void setBaseCaseSensitivityValues(List<SensitivityFactorGroup<DcVariableType, DcEquationType>> factorGroups, DenseMatrix factorsState) {
        for (SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup : factorGroups) {
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorGroup.getFactors()) {
                factor.setBaseCaseSensitivityValue(factor.getFunctionEquationTerm().calculateSensi(factorsState, factorGroup.getIndex()));
            }
        }
    }

    protected void calculateSensitivityValues(List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors, DenseMatrix factorStates,
                                              DenseMatrix contingenciesStates, DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements,
                                              PropagatedContingency contingency, SensitivityValueWriter valueWriter) {
        if (lfFactors.isEmpty()) {
            return;
        }
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.ZERO)
                .forEach(factor -> valueWriter.write(factor.getContext(), contingency != null ? contingency.getContingency().getId() : null,
                                                     contingency != null ? contingency.getIndex() : -1, 0, Double.NaN));

        setAlphas(contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);

        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, valueWriter));

        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
            setAlphas(contingencyElements, factorStates, contingenciesStates, factorGroup.getIndex(), ComputedContingencyElement::setAlphaForSensitivityValue);
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
                createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, valueWriter);
            }
        }
    }

    private void setAlphas(Collection<ComputedContingencyElement> contingencyElements, DenseMatrix states,
                           DenseMatrix contingenciesStates, int columnState, ObjDoubleConsumer<ComputedContingencyElement> setValue) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = lfBranch.getPiModel().getX() - (contingenciesStates.get(p1.getVariables().get(0).getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getVariables().get(1).getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getVariables().get(0).getRow(), columnState) - states.get(p1.getVariables().get(1).getRow(), columnState);
            setValue.accept(element, b / a);
        } else {
            // FIXME: direct resolution if contingencyElements.size() == 2
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getVariables().get(0).getRow(), columnState)
                        - states.get(p1.getVariables().get(1).getRow(), columnState)
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = lfBranch.getPiModel().getX();
                    }
                    value = value - (contingenciesStates.get(p1.getVariables().get(0).getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getVariables().get(1).getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }
            }
            LUDecomposition lu = matrix.decomposeLU();
            lu.solve(rhs); // rhs now contains state matrix
            contingencyElements.forEach(element -> setValue.accept(element, rhs.get(element.getLocalIndex(), 0)));
        }
    }

    private Set<ComputedContingencyElement> getGroupOfElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingenciesStates,
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

    protected void fillRhsContingency(final LfNetwork lfNetwork, final EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                      final Collection<ComputedContingencyElement> contingencyElements, final Matrix rhs) {
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

    protected DenseMatrix initContingencyRhs(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<ComputedContingencyElement> contingencyElements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), contingencyElements.size());
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    private void detectPotentialConnectivityLoss(LfNetwork lfNetwork, DenseMatrix states, List<PropagatedContingency> contingencies,
                                                 Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                 EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<PropagatedContingency> nonLosingConnectivityContingencies,
                                                 Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity) {
        for (PropagatedContingency contingency : contingencies) {
            Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = getGroupOfElementsBreakingConnectivity(lfNetwork, states,
                    contingency.getBranchIdsToOpen().stream().map(contingencyElementByBranch::get).collect(Collectors.toList()), equationSystem);
            if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
                nonLosingConnectivityContingencies.add(contingency);
            } else {
                contingenciesByGroupOfElementsBreakingConnectivity.computeIfAbsent(groupOfElementsBreakingConnectivity, key -> new LinkedList<>()).add(contingency);
            }
        }
    }

    static class ConnectivityAnalysisResult {

        private Map<LfSensitivityFactor<DcVariableType, DcEquationType>, Double> predefinedResultsSensi;

        private Map<LfSensitivityFactor<DcVariableType, DcEquationType>, Double> predefinedResultsRef;

        private Collection<PropagatedContingency> contingencies = new HashSet<>();

        private Set<String> elementsToReconnect;

        private Set<LfBus> disabledBuses;

        private Set<LfBus> slackConnectedComponent;

        ConnectivityAnalysisResult(Collection<LfSensitivityFactor<DcVariableType, DcEquationType>> factors, Set<ComputedContingencyElement> elementsBreakingConnectivity,
                                   GraphDecrementalConnectivity<LfBus> connectivity, LfNetwork lfNetwork) {
            elementsToReconnect = computeElementsToReconnect(connectivity, elementsBreakingConnectivity);
            disabledBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
            slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
            slackConnectedComponent.removeAll(disabledBuses);
            predefinedResultsSensi = new HashMap<>();
            predefinedResultsRef = new HashMap<>();
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
                if (factor.getStatus() == LfSensitivityFactor.Status.VALID) {
                    // after a contingency, we check if the factor function and the variable are in different connected components
                    boolean variableConnected = factor.isVariableConnectedToSlackComponent(slackConnectedComponent);
                    boolean functionConnected = factor.isFunctionConnectedToSlackComponent(slackConnectedComponent);
                    if (!variableConnected && functionConnected) {
                        // VALID_ONLY_FOR_FUNCTION status
                        predefinedResultsSensi.put(factor, 0d);
                    }
                    if (!variableConnected && !functionConnected) {
                        // SKIP status
                        predefinedResultsSensi.put(factor, Double.NaN);
                        predefinedResultsRef.put(factor, Double.NaN);
                    }
                    if (variableConnected && !functionConnected) {
                        // ZERO status
                        predefinedResultsSensi.put(factor, 0d);
                        predefinedResultsRef.put(factor, Double.NaN);
                    }
                } else if (factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION) {
                    // Sensitivity equals 0 for VALID_REFERENCE factors
                    predefinedResultsSensi.put(factor, 0d);
                    if (!factor.isFunctionConnectedToSlackComponent(slackConnectedComponent)) {
                        // The reference is not in the main componant of the post contingency network.
                        // Therefore, its value cannot be computed.
                        predefinedResultsRef.put(factor, Double.NaN);
                    }
                }
            }
        }

        void setSensitivityValuePredefinedResults() {
            predefinedResultsSensi.forEach(LfSensitivityFactor::setSensitivityValuePredefinedResult);
        }

        void setFunctionPredefinedResults() {
            predefinedResultsRef.forEach(LfSensitivityFactor::setFunctionPredefinedResult);
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

        private static Set<String> computeElementsToReconnect(GraphDecrementalConnectivity<LfBus> connectivity, Set<ComputedContingencyElement> breakingConnectivityCandidates) {
            Set<String> elementsToReconnect = new LinkedHashSet<>();

            Map<Pair<Integer, Integer>, ComputedContingencyElement> elementByConnectedComponents = new LinkedHashMap<>();
            for (ComputedContingencyElement element : breakingConnectivityCandidates) {
                int bus1Cc = connectivity.getComponentNumber(element.getLfBranch().getBus1());
                int bus2Cc = connectivity.getComponentNumber(element.getLfBranch().getBus2());

                Pair<Integer, Integer> pairOfCc = bus1Cc > bus2Cc ? Pair.of(bus2Cc, bus1Cc) : Pair.of(bus1Cc, bus2Cc);
                // we only need to reconnect one line to restore connectivity
                elementByConnectedComponents.put(pairOfCc, element);
            }

            Map<Integer, Set<Integer>> connections = new HashMap<>();
            for (int i = 0; i < connectivity.getSmallComponents().size() + 1; i++) {
                connections.put(i, Collections.singleton(i));
            }

            for (Map.Entry<Pair<Integer, Integer>, ComputedContingencyElement> elementsByCc : elementByConnectedComponents.entrySet()) {
                Integer cc1 = elementsByCc.getKey().getKey();
                Integer cc2 = elementsByCc.getKey().getValue();
                if (connections.get(cc1).contains(cc2)) {
                    // cc are already connected
                    continue;
                }
                elementsToReconnect.add(elementsByCc.getValue().getElement().getId());
                Set<Integer> newCc = new HashSet<>();
                newCc.addAll(connections.get(cc1));
                newCc.addAll(connections.get(cc2));
                newCc.forEach(integer -> connections.put(integer, newCc));
            }

            return elementsToReconnect;
        }
    }

    private Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> computeConnectivityData(LfNetwork lfNetwork, SensitivityFactorHolder<DcVariableType, DcEquationType> factorHolder,
                                                                                                     Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity,
                                                                                                     Collection<PropagatedContingency> nonLosingConnectivityContingencies) {
        Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> connectivityAnalysisResults = new LinkedHashMap<>();
        if (contingenciesByGroupOfElementsBreakingConnectivity.isEmpty()) {
            return connectivityAnalysisResults;
        }

        GraphDecrementalConnectivity<LfBus> connectivity = lfNetwork.createDecrementalConnectivity(connectivityProvider);
        for (Map.Entry<Set<ComputedContingencyElement>, List<PropagatedContingency>> groupOfElementPotentiallyBreakingConnectivity : contingenciesByGroupOfElementsBreakingConnectivity.entrySet()) {
            Set<ComputedContingencyElement> breakingConnectivityCandidates = groupOfElementPotentiallyBreakingConnectivity.getKey();
            List<PropagatedContingency> contingencyList = groupOfElementPotentiallyBreakingConnectivity.getValue();
            cutConnectivity(lfNetwork, connectivity, breakingConnectivityCandidates.stream().map(ComputedContingencyElement::getElement).map(ContingencyElement::getId).collect(Collectors.toSet()));

            // filter the branches that really impacts connectivity
            Set<ComputedContingencyElement> breakingConnectivityElements = breakingConnectivityCandidates.stream().filter(element -> {
                LfBranch lfBranch = element.getLfBranch();
                return connectivity.getComponentNumber(lfBranch.getBus1()) != connectivity.getComponentNumber(lfBranch.getBus2());
            }).collect(Collectors.toCollection(LinkedHashSet::new));
            if (breakingConnectivityElements.isEmpty()) {
                // we did not break any connectivity
                nonLosingConnectivityContingencies.addAll(contingencyList);
            } else {
                // only compute for factors that have to be computed for this contingency lost
                List<String> contingenciesIds = contingencyList.stream().map(contingency -> contingency.getContingency().getId()).collect(Collectors.toList());

                List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors = factorHolder.getFactorsForContingencies(contingenciesIds).stream()
                        .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID || factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                        .collect(Collectors.toList());
                if (!lfFactors.isEmpty()) {
                    connectivityAnalysisResults.computeIfAbsent(breakingConnectivityElements, branches -> new ConnectivityAnalysisResult(lfFactors, branches, connectivity, lfNetwork)).getContingencies().addAll(contingencyList);
                }
            }
            connectivity.reset();
        }
        return connectivityAnalysisResults;
    }

    private void processConverterStation(LfNetwork lfNetwork, HvdcConverterStation<?> station,
                                         Collection<Pair<LfBus, LccConverterStation>> lccs, Collection<LfVscConverterStationImpl> vscs) {
        AbstractLfBus bus = (AbstractLfBus) lfNetwork.getBusById(station.getTerminal().getBusView().getBus().getId());
        if (bus != null) {
            if (station instanceof LccConverterStation) {
                lccs.add(Pair.of(bus, (LccConverterStation) station));
            } else if (station instanceof VscConverterStation) {
                vscs.add(bus.getGenerators().stream().filter(LfVscConverterStationImpl.class::isInstance)
                        .map(LfVscConverterStationImpl.class::cast)
                        .filter(lfVscConverterStation -> lfVscConverterStation.getId().equals(station.getId()))
                        .findFirst().orElseThrow());
            }
        }
    }

    private void processGenerator(LfNetwork lfNetwork, Generator generator,
                                  Collection<LfGeneratorImpl> generators) {
        AbstractLfBus bus = (AbstractLfBus) lfNetwork.getBusById(generator.getTerminal().getBusView().getBus().getId());
        if (bus != null) {
            generators.add(bus.getGenerators().stream().filter(LfGeneratorImpl.class::isInstance)
                    .map(LfGeneratorImpl.class::cast)
                    .filter(lfGenerator -> lfGenerator.getId().equals(generator.getId()))
                    .findFirst().orElseThrow());
        }
    }

    private void applyInjectionContingencies(Network network, LfNetwork lfNetwork, PropagatedContingency contingency,
                                             Set<LfGenerator> participatingGeneratorsToRemove, List<BusState> busStates,
                                             LoadFlowParameters lfParameters) {
        // it applies on the network the loss of DC lines contained in the contingency.
        // it applies on the network the loss of generators contained in the contingency.
        // it applies on the network the loss of loads contained in the contingency.
        // Buses state are stored.

        // DC lines.
        Collection<Pair<LfBus, LccConverterStation>> lccs = new HashSet<>();
        Collection<LfVscConverterStationImpl> vscs = new HashSet<>();
        for (String hvdcId : contingency.getHvdcIdsToOpen()) {
            HvdcLine hvdcLine = network.getHvdcLine(hvdcId);
            processConverterStation(lfNetwork, hvdcLine.getConverterStation1(), lccs, vscs);
            processConverterStation(lfNetwork, hvdcLine.getConverterStation2(), lccs, vscs);
        }

        // generators.
        Collection<LfGeneratorImpl> generators = new HashSet<>();
        for (String generatorId : contingency.getGeneratorIdsToLose()) {
            Generator generator = network.getGenerator(generatorId);
            processGenerator(lfNetwork, generator, generators);
        }

        for (String loadId : contingency.getLoadIdsToLose()) {
            Load load = network.getLoad(loadId);
            Bus bus = load.getTerminal().getBusView().getBus();
            if (bus != null) {
                LfBus lfBus = lfNetwork.getBusById(bus.getId());
                busStates.add(BusState.save(lfBus));
            }
        }

        lccs.stream().map(Pair::getKey).forEach(b -> busStates.add(BusState.save(b)));
        vscs.stream().map(LfGenerator::getBus).forEach(b -> busStates.add(BusState.save(b)));
        generators.stream().map(LfGenerator::getBus).forEach(b -> busStates.add(BusState.save(b)));

        for (Pair<LfBus, LccConverterStation> busAndlcc : lccs) {
            LfBus bus = busAndlcc.getKey();
            LccConverterStation lcc = busAndlcc.getValue();
            HvdcLine line = lcc.getHvdcLine();
            bus.setLoadTargetP(bus.getLoadTargetP() - AbstractLfBus.getLccConverterStationLoadTargetP(lcc, line));
        }

        for (LfVscConverterStationImpl vsc : vscs) {
            vsc.setTargetP(0);
        }

        boolean distributedSlackOnGenerators = isDistributedSlackOnGenerators(lfParameters);
        for (LfGeneratorImpl generator : generators) {
            generator.setTargetP(0);
            if (distributedSlackOnGenerators && generator.isParticipating()) {
                generator.setParticipating(false);
                participatingGeneratorsToRemove.add(generator);
            }
        }

        for (String loadId : contingency.getLoadIdsToLose()) {
            Load load = network.getLoad(loadId);
            Bus bus = load.getTerminal().getBusView().getBus();
            if (bus != null) {
                LfBus lfBus = lfNetwork.getBusById(bus.getId());
                lfBus.setLoadTargetP(lfBus.getLoadTargetP() - load.getP0() / PerUnit.SB);
                lfBus.getLfLoads().setAbsVariableLoadTargetP(lfBus.getLfLoads().getAbsVariableLoadTargetP() - load.getP0());
            }
        }
    }

    public DenseMatrix calculateStates(JacobianMatrix<DcVariableType, DcEquationType> j, EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                       List<SensitivityFactorGroup<DcVariableType, DcEquationType>> factorGroups,
                                       List<ParticipatingElement> participatingElements) {

        Map<LfBus, Double> slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
            ParticipatingElement::getLfBus,
            element -> -element.getFactor(),
            Double::sum
        ));
        DenseMatrix states = initFactorsRhs(equationSystem, factorGroups, slackParticipationByBus);
        j.solveTransposed(states);
        setBaseCaseSensitivityValues(factorGroups, states);
        return states;
    }

    public void calculateContingencySensitivityValues(PropagatedContingency contingency, List<SensitivityFactorGroup<DcVariableType, DcEquationType>> factorGroups, DenseMatrix factorStates, DenseMatrix contingenciesStates,
                                                      DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements, SensitivityValueWriter valueWriter, Network network,
                                                      LfNetwork lfNetwork, LoadFlowParameters lfParameters,  OpenLoadFlowParameters lfParametersExt, JacobianMatrix<DcVariableType, DcEquationType> j, EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                      DcLoadFlowEngine dcLoadFlowEngine, SensitivityFactorHolder<DcVariableType, DcEquationType> factorHolder, List<ParticipatingElement> participatingElements,
                                                      Collection<LfBus> disabledBuses, Collection<LfBranch> disabledBranches, Reporter reporter) {
        List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors = factorHolder.getFactorsForContingency(contingency.getContingency().getId());
        if (contingency.getHvdcIdsToOpen().isEmpty() && contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLose().isEmpty()) {
            calculateSensitivityValues(factors, factorStates, contingenciesStates, flowStates, contingencyElements,
                    contingency, valueWriter);
        } else {
            // if we have a contingency including the loss of a DC line or a generator
            // if we have a contingency including the loss of a generator
            // if we have a contingency including the loss of a DC line or a generator or a load

            List<BusState> busStates = new ArrayList<>();
            Set<LfGenerator> participatingGeneratorsToRemove = new HashSet<>();
            applyInjectionContingencies(network, lfNetwork, contingency, participatingGeneratorsToRemove, busStates, lfParameters);

            List<ParticipatingElement> newParticipatingElements = participatingElements;
            DenseMatrix newFactorStates = factorStates;
            boolean participatingElementsChanged = (isDistributedSlackOnGenerators(lfParameters) && !contingency.getGeneratorIdsToLose().isEmpty())
                    || (isDistributedSlackOnLoads(lfParameters) && !contingency.getLoadIdsToLose().isEmpty());
            if (participatingElementsChanged) {
                if (isDistributedSlackOnGenerators(lfParameters)) {
                    // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
                    newParticipatingElements = participatingElements.stream()
                            .filter(participatingElement -> !participatingGeneratorsToRemove.contains(participatingElement.getElement()))
                            .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                            .collect(Collectors.toList());
                    normalizeParticipationFactors(newParticipatingElements, "LfGenerators");
                } else { // slack distribution on loads
                    newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters, lfParametersExt);
                }
                newFactorStates = calculateStates(j, equationSystem, factorGroups, newParticipatingElements);
            }

            DenseMatrix newFlowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, factors, lfParameters,
                newParticipatingElements, disabledBuses, disabledBranches, reporter);

            calculateSensitivityValues(factors, newFactorStates, contingenciesStates, newFlowStates, contingencyElements, contingency, valueWriter);

            ElementState.restore(busStates);
            if (participatingElementsChanged) {
                setBaseCaseSensitivityValues(factorGroups, factorStates);
            }
        }
    }

    private JacobianMatrix<DcVariableType, DcEquationType> createJacobianMatrix(LfNetwork network, EquationSystem<DcVariableType, DcEquationType> equationSystem, VoltageInitializer voltageInitializer) {
        DcLoadFlowEngine.initStateVector(network, equationSystem, voltageInitializer);
        return new JacobianMatrix<>(equationSystem, matrixFactory);
    }

    private static DcLoadFlowParameters createDcLoadFlowParameters(SlackBusSelector slackBusSelector, MatrixFactory matrixFactory,
                                                                   LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        var networkParameters = new LfNetworkParameters(slackBusSelector,
                                                        false,
                                                        false,
                                                        false,
                                                        false,
                                                        lfParametersExt.getPlausibleActivePowerLimit(),
                                                        lfParametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(),
                                                        true,
                                                        lfParameters.getCountriesToBalance(),
                                                        lfParameters.isDistributedSlack() && lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                                                        false,
                                                        false,
                                                        false,
                                                        false,
                                                        true,
                                                        false,
                                                        false);

        var equationSystemCreationParameters = new DcEquationSystemCreationParameters(true,
                                                                                      true,
                                                                                      true,
                                                                                      lfParameters.isDcUseTransformerRatio());

        return new DcLoadFlowParameters(networkParameters,
                                        equationSystemCreationParameters,
                                        matrixFactory,
                                        lfParameters.isDistributedSlack(),
                                        lfParameters.getBalanceType(),
                                        true);
    }

    public void analyse(Network network, List<PropagatedContingency> contingencies, List<SensitivityVariableSet> variableSets,
                        LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, SensitivityFactorReader factorReader,
                        SensitivityValueWriter valueWriter, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(variableSets);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(valueWriter);

        Stopwatch stopwatch = Stopwatch.createStarted();

        // create the network (we only manage main connected component)
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(), lfParametersExt.getSlackBusesIds());
        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters(slackBusSelector, false, true, lfParameters.isTwtSplitShuntAdmittance(),
                false, lfParametersExt.getPlausibleActivePowerLimit(), false, true, lfParameters.getCountriesToBalance(),
                lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                false, false, false, false, true, false, false);
        List<LfNetwork> lfNetworks = Networks.load(network, lfNetworkParameters, reporter);
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkLoadFlowParameters(lfParameters);

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder<DcVariableType, DcEquationType> factorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork);
        List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors = factorHolder.getAllFactors();

        lfFactors.stream()
                .filter(lfFactor -> lfFactor.getFunctionType() != SensitivityFunctionType.BRANCH_ACTIVE_POWER
                    || (lfFactor.getVariableType() != SensitivityVariableType.INJECTION_ACTIVE_POWER
                    && lfFactor.getVariableType() != SensitivityVariableType.TRANSFORMER_PHASE
                    && lfFactor.getVariableType() != SensitivityVariableType.HVDC_LINE_ACTIVE_POWER))
                .findFirst()
                .ifPresent(ignored -> {
                    throw new PowsyblException("Only variables of type TRANSFORMER_PHASE or INJECTION_ACTIVE_POWER, and functions of type BRANCH_ACTIVE_POWER are yet supported in DC");
                });

        LOGGER.info("Running DC sensitivity analysis with {} factors and {} contingencies",  lfFactors.size(), contingencies.size());

        var dcLoadFlowParameters = createDcLoadFlowParameters(slackBusSelector, matrixFactory, lfParameters, lfParametersExt);

        DcLoadFlowEngine dcLoadFlowEngine = new DcLoadFlowEngine(lfNetworks, dcLoadFlowParameters);

        // create DC equation system for sensitivity analysis
        EquationSystem<DcVariableType, DcEquationType> equationSystem = DcEquationSystem.create(lfNetwork, dcLoadFlowParameters.getEquationSystemCreationParameters());

        writeSkippedFactors(lfFactors, valueWriter);

        // next we only work with valid factors
        lfFactors = lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID || factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION).collect(Collectors.toList());

        // index factors by variable group to compute the minimal number of states
        List<SensitivityFactorGroup<DcVariableType, DcEquationType>> factorGroups = createFactorGroups(lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

        boolean hasMultiVariables = factorGroups.stream().anyMatch(MultiVariablesFactorGroup.class::isInstance);

        // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
        // buses that contain elements participating to slack distribution)
        List<ParticipatingElement> participatingElements;
        Map<LfBus, Double> slackParticipationByBus;
        if (lfParameters.isDistributedSlack()) {
            participatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters, lfParametersExt);
            slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                ParticipatingElement::getLfBus,
                element -> -element.getFactor(),
                Double::sum
            ));
        } else {
            participatingElements = new ArrayList<>();
            slackParticipationByBus = Collections.singletonMap(lfNetwork.getSlackBus(), -1d);
        }

        // prepare management of contingencies
        Map<String, ComputedContingencyElement> contingencyElementByBranch =
            contingencies.stream()
                             .flatMap(contingency -> contingency.getBranchIdsToOpen().stream())
                             .map(branch -> new ComputedContingencyElement(new BranchContingency(branch), lfNetwork, equationSystem))
                             .filter(element -> element.getLfBranchEquation() != null)
                             .collect(Collectors.toMap(
                                 computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                 computedContingencyElement -> computedContingencyElement,
                                 (existing, replacement) -> existing,
                                 LinkedHashMap::new
                             ));
        ComputedContingencyElement.setContingencyIndexes(contingencyElementByBranch.values());

        // create jacobian matrix either using calculated voltages from pre-contingency network or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        try (JacobianMatrix<DcVariableType, DcEquationType> j = createJacobianMatrix(lfNetwork, equationSystem, voltageInitializer)) {

            // run DC load on pre-contingency network
            DenseMatrix flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactors, lfParameters, participatingElements, Collections.emptyList(), Collections.emptyList(), reporter);

            // compute the pre-contingency sensitivity values + the states with +1 -1 to model the contingencies
            DenseMatrix factorsStates = initFactorsRhs(equationSystem, factorGroups, slackParticipationByBus); // this is the rhs for the moment
            DenseMatrix contingenciesStates = initContingencyRhs(lfNetwork, equationSystem, contingencyElementByBranch.values()); // rhs with +1 -1 on contingency elements
            j.solveTransposed(factorsStates); // states for the sensitivity factors
            j.solveTransposed(contingenciesStates); // states for the +1 -1 of contingencies

            // sensitivity values for pre-contingency network
            setBaseCaseSensitivityValues(factorGroups, factorsStates);
            calculateSensitivityValues(factorHolder.getFactorsForBaseNetwork(), factorsStates, contingenciesStates, flowStates,
                    Collections.emptyList(), null, valueWriter);

            // connectivity analysis by contingency
            // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
            // so, we will index contingencies by a list of branch that may breaks connectivity
            // for example, if in the network, loosing line L1 breaks connectivity, and loosing L2 and L3 together breaks connectivity,
            // the index would be: {L1, L2, L3}
            // a contingency involving a phase tap changer loss has to be treated separately
            Collection<PropagatedContingency> nonLosingConnectivityContingencies = new LinkedList<>();
            Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity = new LinkedHashMap<>();

            detectPotentialConnectivityLoss(lfNetwork, contingenciesStates, contingencies, contingencyElementByBranch, equationSystem,
                    nonLosingConnectivityContingencies, contingenciesByGroupOfElementsBreakingConnectivity);

            // process connectivity data for all contingencies that potentially lose connectivity
            Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> connectivityAnalysisResults = computeConnectivityData(lfNetwork, factorHolder, contingenciesByGroupOfElementsBreakingConnectivity, nonLosingConnectivityContingencies);
            PhaseTapChangerContingenciesIndexing phaseTapChangerContingenciesIndexing = new PhaseTapChangerContingenciesIndexing(nonLosingConnectivityContingencies, contingencyElementByBranch);

            // compute the contingencies without loss of connectivity
            // first we compute the ones without loss of phase tap changers (because we reuse the load flows from the pre contingency network for all of them)
            for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
                List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());
                calculateContingencySensitivityValues(contingency, factorGroups, factorsStates, contingenciesStates, flowStates, contingencyElements, valueWriter,
                        network, lfNetwork, lfParameters, lfParametersExt, j, equationSystem, dcLoadFlowEngine, factorHolder, participatingElements,
                        Collections.emptyList(), Collections.emptyList(), reporter);
            }

            // then we compute the ones involving the loss of a phase tap changer (because we need to recompute the load flows)
            for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> entry : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
                Set<LfBranch> removedPhaseTapChangers = entry.getKey();
                Collection<PropagatedContingency> propagatedContingencies = entry.getValue();
                List<String> contingenciesIds = propagatedContingencies.stream().map(c -> c.getContingency().getId()).collect(Collectors.toList());
                List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactorsForContingencies = factorHolder.getFactorsForContingencies(contingenciesIds).stream()
                        .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID || factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                        .collect(Collectors.toList());
                if (!lfFactorsForContingencies.isEmpty()) {
                    flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactorsForContingencies, lfParameters, participatingElements, Collections.emptyList(), removedPhaseTapChangers, reporter);
                }
                for (PropagatedContingency contingency : propagatedContingencies) {
                    List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());
                    calculateContingencySensitivityValues(contingency, factorGroups, factorsStates, contingenciesStates, flowStates, contingencyElements, valueWriter,
                            network, lfNetwork, lfParameters, lfParametersExt, j, equationSystem, dcLoadFlowEngine, factorHolder, participatingElements,
                            Collections.emptyList(), removedPhaseTapChangers, reporter);
                }
            }

            if (contingenciesByGroupOfElementsBreakingConnectivity.isEmpty()) {
                return;
            }

            // compute the contingencies with loss of connectivity
            for (ConnectivityAnalysisResult connectivityAnalysisResult : connectivityAnalysisResults.values()) {
                List<String> contingenciesIds = connectivityAnalysisResult.getContingencies().stream().map(c -> c.getContingency().getId()).collect(Collectors.toList());
                List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactorsForContingencies = factorHolder.getFactorsForContingencies(contingenciesIds).stream()
                        .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID || factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                        .collect(Collectors.toList());

                // we need to reset predefined values for factor that need to be calculated for this set of contingency
                // predefined may have be set by a previous set of contingency with loss of connectivity
                lfFactorsForContingencies.forEach(factor -> factor.setSensitivityValuePredefinedResult(null));
                lfFactorsForContingencies.forEach(factor -> factor.setFunctionPredefinedResult(null));
                connectivityAnalysisResult.setSensitivityValuePredefinedResults();
                connectivityAnalysisResult.setFunctionPredefinedResults();

                Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();

                // null and unused if slack is not distributed
                List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
                boolean rhsChanged = false; // true if there if the disabled buses changes the slack distribution, or the GLSK
                DenseMatrix factorStateForThisConnectivity = factorsStates;
                if (lfParameters.isDistributedSlack()) {
                    rhsChanged = participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
                }
                if (hasMultiVariables) {
                    // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
                    rhsChanged |= rescaleGlsk(factorGroups, disabledBuses);
                }

                // we need to recompute the factor states because the connectivity changed
                if (rhsChanged) {
                    Map<LfBus, Double> slackParticipationByBusForThisConnectivity;

                    if (lfParameters.isDistributedSlack()) {
                        participatingElementsForThisConnectivity = getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters, lfParametersExt); // will also be used to recompute the loadflow
                        slackParticipationByBusForThisConnectivity = participatingElementsForThisConnectivity.stream().collect(Collectors.toMap(
                            element -> lfNetwork.getBusById(element.getLfBus().getId()),
                            element -> -element.getFactor(),
                            Double::sum
                        ));
                    } else {
                        slackParticipationByBusForThisConnectivity = Collections.singletonMap(lfNetwork.getBusById(lfNetwork.getSlackBus().getId()), -1d);
                    }

                    factorStateForThisConnectivity = initFactorsRhs(equationSystem, factorGroups, slackParticipationByBusForThisConnectivity);
                    j.solveTransposed(factorStateForThisConnectivity); // get the states for the new connectivity
                    setBaseCaseSensitivityValues(factorGroups, factorStateForThisConnectivity); // use this state to compute the base sensitivity (without +1-1)
                }

                Set<String> elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
                phaseTapChangerContingenciesIndexing = new PhaseTapChangerContingenciesIndexing(connectivityAnalysisResult.getContingencies(), contingencyElementByBranch, elementsToReconnect);

                if (!lfFactorsForContingencies.isEmpty()) {
                    flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactorsForContingencies, lfParameters,
                            participatingElementsForThisConnectivity, disabledBuses, Collections.emptyList(), reporter);
                }

                // compute contingencies without loss of phase tap changer
                for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
                    Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().filter(element -> !elementsToReconnect.contains(element)).map(contingencyElementByBranch::get).collect(Collectors.toList());
                    calculateContingencySensitivityValues(contingency, factorGroups, factorStateForThisConnectivity, contingenciesStates, flowStates, contingencyElements, valueWriter,
                            network, lfNetwork, lfParameters, lfParametersExt, j, equationSystem, dcLoadFlowEngine, factorHolder, participatingElementsForThisConnectivity,
                            disabledBuses, Collections.emptyList(), reporter);
                }

                // then we compute the ones involving the loss of a phase tap changer (because we need to recompute the load flows)
                for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> entry1 : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
                    Set<LfBranch> disabledPhaseTapChangers = entry1.getKey();
                    Collection<PropagatedContingency> propagatedContingencies = entry1.getValue();
                    List<String> contingenciesIds2 = propagatedContingencies.stream().map(c -> c.getContingency().getId()).collect(Collectors.toList());
                    List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactorsForContingencies2 = factorHolder.getFactorsForContingencies(contingenciesIds2).stream()
                            .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID || factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                            .collect(Collectors.toList());
                    if (!lfFactorsForContingencies2.isEmpty()) {
                        flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactorsForContingencies2, lfParameters, participatingElements, disabledBuses, disabledPhaseTapChangers, reporter);
                    }
                    for (PropagatedContingency contingency : propagatedContingencies) {
                        Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().filter(element -> !elementsToReconnect.contains(element)).map(contingencyElementByBranch::get).collect(Collectors.toList());
                        calculateContingencySensitivityValues(contingency, factorGroups, factorStateForThisConnectivity, contingenciesStates, flowStates, contingencyElements, valueWriter,
                                network, lfNetwork, lfParameters, lfParametersExt, j, equationSystem, dcLoadFlowEngine, factorHolder, participatingElementsForThisConnectivity,
                                disabledBuses, disabledPhaseTapChangers, reporter);
                    }
                }

                if (rhsChanged) {
                    setBaseCaseSensitivityValues(factorGroups, factorsStates); // we modified the rhs, we need to restore previous state
                }
            }
        }

        stopwatch.stop();
        LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }
}
