/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class DcSensitivityAnalysis extends AbstractDcSensitivityAnalysis {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-6;

    static class ComputedContingencyElement {
        private int contingencyIndex = -1; // local index of the element inside of a contingency : index of the element in the matrix used in the setAlphas method
        private int globalIndex = -1; // the index of the contingency in the global rhs, with sensitivity factors
        private double alpha = Double.NaN;
        private final ContingencyElement element;
        private final LfBranch lfBranch;
        private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

        public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem equationSystem) {
            this.element = element;
            lfBranch = lfNetwork.getBranchById(element.getId());
            branchEquation = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        public int getGlobalIndex() {
            return globalIndex;
        }

        public void setGlobalIndex(final int index) {
            this.globalIndex = index;
        }

        public int getContingencyIndex() {
            return contingencyIndex;
        }

        public void setContingencyIndex(final int index) {
            this.contingencyIndex = index;
        }

        public double getAlpha() {
            return alpha;
        }

        public void setAlpha(final double alpha) {
            this.alpha = alpha;
        }

        public ContingencyElement getElement() {
            return element;
        }

        public LfBranch getLfBranch() {
            return lfBranch;
        }

        public ClosedBranchSide1DcFlowEquationTerm getBranchEquation() {
            return branchEquation;
        }

        public static void setGlobalIndexes(Collection<ComputedContingencyElement> elements, int globalOffset) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setGlobalIndex(globalOffset + index++);
            }
        }

        public static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index++);
            }
        }

    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private SensitivityValue createBranchSensitivityValue(SensitivityFactorWrapped factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, Double functionReference,
                                                          List<ComputedContingencyElement> contingencyElements, Double predefinedValue) {
        double value;
        ClosedBranchSide1DcFlowEquationTerm p1 = factor.getEquationTerm();
        if (predefinedValue != null) {
            value = predefinedValue;
        } else {
            value = p1.calculate(states, factorGroup.getIndex());
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                if (contingencyElement.getElement().getId().equals(factor.getFunctionBranch().getId()) || contingencyElement.getElement().getId().equals(factor.getFactor().getVariable().getId())) {
                    // the sensitivity on a removed branch is 0, the sensitivity if the variable was a removed branch is 0
                    value = 0d;
                    break;
                }
                value = value + contingencyElement.getAlpha() * p1.calculate(states, contingencyElement.getGlobalIndex());
            }
        }
        return new SensitivityValue(factor.getFactor(), value * PerUnit.SB, functionReference, 0);
    }

    protected List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                                List<SensitivityFactorGroup> factorGroups,
                                                                DenseMatrix states, Map<String, Double> functionReferenceByBranch,
                                                                List<ComputedContingencyElement> contingencyElements, Map<SensitivityFactorWrapped, Double> predefinedResults) {
        List<SensitivityValue> sensitivityValuesContingencies = new ArrayList<>();

        for (SensitivityFactorGroup factorGroup : factorGroups) {
            setAlphas(contingencyElements, factorGroup, states);
            for (SensitivityFactorWrapped factor : factorGroup.getFactors()) {
                LfBranch lfBranch = factor.getFunctionBranch();
                sensitivityValuesContingencies.add(createBranchSensitivityValue(factor, factorGroup, states, functionReferenceByBranch.get(lfBranch.getId()), contingencyElements, predefinedResults.get(factor)));
            }
        }
        return sensitivityValuesContingencies;
    }

    private void setAlphas(List<ComputedContingencyElement> contingencyElements, SensitivityFactorGroup sensitivityFactorGroup, DenseMatrix states) {
        ComputedContingencyElement.setContingencyIndexes(contingencyElements);
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.get(0);
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getBranchEquation();
            // we solve a*alpha = b
            double a = lfBranch.getPiModel().getX() / PerUnit.SB - (states.get(p1.getVariables().get(0).getRow(), element.getGlobalIndex())
                             - states.get(p1.getVariables().get(1).getRow(), element.getGlobalIndex()));
            double b = states.get(p1.getVariables().get(0).getRow(), sensitivityFactorGroup.getIndex())
                       - states.get(p1.getVariables().get(1).getRow(), sensitivityFactorGroup.getIndex());
            element.setAlpha(b / a);
        } else {
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                if (!element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                    throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
                }
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getBranchEquation();
                rhs.set(element.getContingencyIndex(), 0, states.get(p1.getVariables().get(0).getRow(), sensitivityFactorGroup.getIndex())
                                                          - states.get(p1.getVariables().get(1).getRow(), sensitivityFactorGroup.getIndex())
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = lfBranch.getPiModel().getX() / PerUnit.SB;
                    }
                    value = value - (states.get(p1.getVariables().get(0).getRow(), element2.getGlobalIndex())
                                     - states.get(p1.getVariables().get(1).getRow(), element2.getGlobalIndex()));
                    matrix.set(element.getContingencyIndex(), element2.getContingencyIndex(), value);
                }
            }
            LUDecomposition lu = matrix.decomposeLU();
            lu.solve(rhs); // rhs now contains state matrix
            contingencyElements.forEach(element -> element.setAlpha(rhs.get(element.getContingencyIndex(), 0)));
        }
    }

    private List<Set<ComputedContingencyElement>> getElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix preContingencyStates,
                                                                             List<ComputedContingencyElement> contingencyElements,
                                                                             EquationSystem equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity
        // We consider a +1 -1 on a line, and we observe the sensivity of this injection on the other contingency elements
        // If the sum of the sentivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // Thus, losing these lines will lose the connectivity
        List<Set<ComputedContingencyElement>> responsibleAssociations = new LinkedList<>();
        for (ComputedContingencyElement element : contingencyElements) {
            Set<ComputedContingencyElement> responsibleElements = new HashSet<>();
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculate(preContingencyStates, element.getGlobalIndex()));
                if (value > CONNECTIVITY_LOSS_THRESHOLD) {
                    responsibleElements.add(element2);
                }
                sum += value;
            }
            if (Math.abs(sum * PerUnit.SB - 1d) < CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                responsibleAssociations.add(responsibleElements);
            }
        }
        return responsibleAssociations;
    }

    protected void fillRhsContingency(final LfNetwork lfNetwork, final EquationSystem equationSystem, final Map<String, ComputedContingencyElement> contingencyElements, final Matrix rhs) {
        for (ComputedContingencyElement element : contingencyElements.values()) {
            if (element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
                if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                    continue;
                }
                LfBus bus1 = lfBranch.getBus1();
                LfBus bus2 = lfBranch.getBus2();
                if (bus1.isSlack()) {
                    Equation p = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    rhs.set(p.getColumn(), element.getGlobalIndex(), -1 / PerUnit.SB);
                } else if (bus2.isSlack()) {
                    Equation p = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    rhs.set(p.getColumn(), element.getGlobalIndex(), 1 / PerUnit.SB);
                } else {
                    Equation p1 = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    Equation p2 = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    rhs.set(p1.getColumn(), element.getGlobalIndex(), 1 / PerUnit.SB);
                    rhs.set(p2.getColumn(), element.getGlobalIndex(), -1 / PerUnit.SB);
                }
            } else {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
        }
    }

    protected DenseMatrix initRhs(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorsGroups, Map<String, ComputedContingencyElement> contingencyElements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsGroups.size() + contingencyElements.values().size());
        fillRhsSensitivityVariable(lfNetwork, equationSystem, factorsGroups, rhs);
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    private void lookForConnectivityLoss(LfNetwork lfNetwork, DenseMatrix states, List<Contingency> contingencies, Map<String, ComputedContingencyElement> contingenciesElements, EquationSystem equationSystem, Collection<Contingency> straightforwardContingencies,  Map<List<Set<ComputedContingencyElement>>, List<Contingency>> losingConnectivityContingency) {
        for (Contingency contingency : contingencies) {
            List<Set<ComputedContingencyElement>> groupOfElementsBreakingConnectivity = getElementsBreakingConnectivity(lfNetwork, states, contingency.getElements().stream().map(element -> contingenciesElements.get(element.getId())).collect(Collectors.toList()), equationSystem);
            if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
                straightforwardContingencies.add(contingency);
            } else if (groupOfElementsBreakingConnectivity.stream().mapToInt(Set::size).max().getAsInt() > 1) {
                throw new PowsyblException("The contingency " + contingency.getId() + " breaks the connectivity on more than 1 branch.");
            } else {
                losingConnectivityContingency.computeIfAbsent(groupOfElementsBreakingConnectivity, key -> new LinkedList<>()).add(contingency);
            }
        }
    }

    @Override
    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors,
                                                                                     List<Contingency> contingencies, LoadFlowParameters lfParameters,
                                                                                     OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);

        // create the network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkSensitivities(network, lfNetwork, factors);
        LazyConnectivity connectivity = new LazyConnectivity(lfNetwork);

        // run DC load
        Map<String, Double> functionReferenceByBranch = getFunctionReferenceByBranch(lfNetworks, lfParameters, lfParametersExt);

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // we wrap the factor into a class that allows us to have access to their branch and EquationTerm instantly
        List<SensitivityFactorWrapped> factorsWrapped = factors.stream().map(factor -> new SensitivityFactorWrapped(factor, lfNetwork, equationSystem)).collect(Collectors.toList());
        // index factors by variable configuration to compute minimal number of states
        List<SensitivityFactorGroup> factorsGroups = createFactorGroups(network, factorsWrapped);

        if (factorsGroups.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        // Compute the partipation for every factor
        List<ParticipatingElement> participatingElements = null;
        Map<String, Double> participationPerBus;
        if (lfParameters.isDistributedSlack()) {
            participatingElements = getParticipatingElements(lfNetwork, lfParameters);
            participationPerBus = participatingElements.stream().collect(Collectors.toMap(
                element -> getParticipatingElementBus(element).getId(),
                element -> -element.getFactor(),
                Double::sum
            ));
        } else {
            participationPerBus = Collections.singletonMap(lfNetwork.getSlackBus().getId(), -1d);
        }
        // compute the participation for each injection factor (+1 on the injection, and then -participation on all
        // elements participating to load balancing)
        computeFactorsInjection(participationPerBus, factorsGroups, new HashMap<>());

        Map<String, ComputedContingencyElement> contingenciesElements = contingencies.stream()
                                                                                    .flatMap(contingency -> contingency.getElements().stream())
                                                                                    .map(contingencyElement -> new ComputedContingencyElement(contingencyElement, lfNetwork, equationSystem))
                                                                                    .collect(Collectors.toMap(
                                                                                        computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                                                                        computedContingencyElement -> computedContingencyElement
                                                                                    ));

        ComputedContingencyElement.setGlobalIndexes(contingenciesElements.values(), factorsGroups.size());

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);
        // compute pre-contingency sensitivity values + the states for making +1-1 on the contingencies
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsGroups, contingenciesElements);
        DenseMatrix states = solveTransposed(rhs, j); // states contains angles for the sensitivity factors, but also for the +1-1 related to contingencies

        // sensitivities without contingency
        List<SensitivityValue> sensitivityValues = calculateSensitivityValues(lfNetwork, equationSystem, factorsGroups,
                states, functionReferenceByBranch, Collections.emptyList(), Collections.emptyMap());

        Collection<Contingency> straightforwardContingencies = new LinkedList<>();
        Map<List<Set<ComputedContingencyElement>>, List<Contingency>> losingConnectivityContingency = new HashMap<>();
        // We will index contingencies by the list of "problems" contained in this contingency
        // For example, For example, if in the network, loosing line L1 breaks connectivity, and loosing L2 and L3 together breaks connectivity,
        // the responsible associations would be: [{L1}, {L2, L3}]

        lookForConnectivityLoss(lfNetwork, states, contingencies, contingenciesElements, equationSystem, straightforwardContingencies, losingConnectivityContingency);

        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();
        // compute the contingencies with no loss of connectivity
        for (Contingency contingency : straightforwardContingencies) {
            contingenciesValue.put(contingency.getId(), calculateSensitivityValues(lfNetwork, equationSystem, factorsGroups,
                    states, functionReferenceByBranch, contingency.getElements().stream().map(element -> contingenciesElements.get(element.getId())).collect(Collectors.toList()),
                    Collections.emptyMap()
            ));
        }

        for (Map.Entry<List<Set<ComputedContingencyElement>>, List<Contingency>> contingencyEntry : losingConnectivityContingency.entrySet()) {
            Map<SensitivityFactorWrapped, Double> predefinedResults = new HashMap<>();

            contingencyEntry.getKey().stream().flatMap(Set::stream)
                            .map(ComputedContingencyElement::getElement)
                            .map(element -> lfNetwork.getBranchById(element.getId()))
                            .forEach(lfBranch -> connectivity.getConnectivity().cut(lfBranch.getBus1(), lfBranch.getBus2()));

            if (lfParameters.isDistributedSlack()) {
                Map<Integer, List<ParticipatingElement>> participatingElementsPerCc = new HashMap<>();
                participatingElements.forEach(element -> participatingElementsPerCc.computeIfAbsent(connectivity.getConnectivity().getComponentNumber(getParticipatingElementBus(element)), key -> new LinkedList<>())
                                                                                   .add(element));
                participatingElementsPerCc.values().forEach(ccElements -> ParticipatingElement.normalizeParticipationFactors(ccElements, "bus"));
                Map<Integer, Map<String, Double>> participationPerCc = participatingElementsPerCc.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream().collect(Collectors.toMap(
                        element -> getParticipatingElementBus(element).getId(),
                        element -> -element.getFactor(),
                        Double::sum
                    ))
                ));

                // compute the participation for each injection factor (+1 on the injection, and then -participation on all
                // elements participating to load balancing, that are still in the connected component of the factor's variable)
                Function<String, Map<String, Double>> getParticipationForBus = busId -> participationPerCc.get(connectivity.getConnectivity().getComponentNumber(lfNetwork.getBusById(busId)));
                computeFactorsInjection(getParticipationForBus, factorsGroups, predefinedResults);

                ComputedContingencyElement.setGlobalIndexes(contingenciesElements.values(), factorsGroups.size());
                rhs = initRhs(lfNetwork, equationSystem, factorsGroups, contingenciesElements);
                states = solveTransposed(rhs, j);
            }

            for (SensitivityFactorWrapped factor : factorsWrapped) {
                LfBranch lfBranch = factor.getFunctionBranch();
                LfBus lfBus;
                if (factor.getFactor() instanceof BranchFlowPerInjectionIncrease) {
                    lfBus = getInjectionBus(network, lfNetwork, (BranchFlowPerInjectionIncrease) factor.getFactor());
                } else if (factor.getFactor() instanceof  BranchFlowPerPSTAngle) {
                    LfBranch transformerBranch = getPhaseChangerBranch(network, lfNetwork, (BranchFlowPerPSTAngle) factor.getFactor());
                    lfBus = transformerBranch.getBus1();
                } else {
                    throw new UnsupportedOperationException("Only factors of type BranchFlowPerInjectionIncrease and BranchFlowPerPSTAngle are supported for post-contingency analysis");
                }
                // Check if the factor function and variable are in different connected components
                if (connectivity.getConnectivity().getComponentNumber(lfBus) != connectivity.getConnectivity().getComponentNumber(lfBranch.getBus1())
                    || connectivity.getConnectivity().getComponentNumber(lfBus) != connectivity.getConnectivity().getComponentNumber(lfBranch.getBus2()))  {
                    predefinedResults.put(factor, 0d);
                }
            }

            Set<String> elementIdLosingConnectivity = contingencyEntry.getKey().stream().flatMap(Set::stream).map(contingencyElement -> contingencyElement.getElement().getId()).collect(Collectors.toSet());

            for (Contingency contingency : contingencyEntry.getValue()) {
                contingenciesValue.put(contingency.getId(), calculateSensitivityValues(lfNetwork, equationSystem, factorsGroups,
                        states, functionReferenceByBranch, contingency.getElements().stream().filter(element -> !elementIdLosingConnectivity.contains(element.getId())).map(element -> contingenciesElements.get(element.getId())).collect(Collectors.toList()),
                        predefinedResults
                ));
            }

            connectivity.getConnectivity().reset();
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
