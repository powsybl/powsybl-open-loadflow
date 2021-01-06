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
public class DcFastSensitivityAnalysis extends AbstractDcSensitivityAnalysis {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-6;

    static class ComputedContingencyElement {
        private int contingencyIndex = -1;
        private int globalIndex = -1;
        private double alpha = Double.NaN;
        private final ContingencyElement element;

        public ComputedContingencyElement(final ContingencyElement element) {
            this.element = element;
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

    public DcFastSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private SensitivityValue createBranchSensitivityValue(ClosedBranchSide1DcFlowEquationTerm p1,
                                                          SensitivityFactor<?, ?> factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, Double functionReference,
                                                          List<ComputedContingencyElement> contingencyElements, Double predefinedValue) {
        double value;
        if (predefinedValue != null) {
            value = predefinedValue;
        } else {
            value = p1.calculate(states, factorGroup.getIndex());
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                if (contingencyElement.getElement().getId().equals(factor.getFunction().getId())) {
                    // the sensitivity on a removed branch is 0
                    value = 0d;
                    break;
                }
                value = value + contingencyElement.getAlpha() * p1.calculate(states, contingencyElement.getGlobalIndex());
            }
        }
        return new SensitivityValue(factor, value * PerUnit.SB, functionReference, 0);
    }

    protected List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                                List<SensitivityFactorGroup> factorGroups,
                                                                DenseMatrix states, Map<String, Double> functionReferenceByBranch,
                                                                List<ComputedContingencyElement> contingencyElements, Map<SensitivityFactor, Double> predefinedResults) {
        List<SensitivityValue> sensitivityValuesContingencies = new ArrayList<>();
        Map<String, ClosedBranchSide1DcFlowEquationTerm> equationTermByBranchId = new HashMap<>(); // cache the equation term for each branch, because getting it is expensive

        for (SensitivityFactorGroup factorGroup : factorGroups) {
            setAlphas(contingencyElements, factorGroup, states, lfNetwork, equationSystem);
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                String branchId;
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    branchId = ((BranchFlowPerInjectionIncrease) factor).getFunction().getBranchId();
                } else if (factor instanceof BranchFlowPerPSTAngle) {
                    branchId = ((BranchFlowPerPSTAngle) factor).getFunction().getBranchId();
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
                LfBranch lfBranch = lfNetwork.getBranchById(branchId);
                ClosedBranchSide1DcFlowEquationTerm p1 = equationTermByBranchId.computeIfAbsent(branchId, branch -> equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class));
                sensitivityValuesContingencies.add(createBranchSensitivityValue(p1,
                        factor, factorGroup, states, functionReferenceByBranch.get(branchId), contingencyElements, predefinedResults.get(factor)));
            }
        }
        return sensitivityValuesContingencies;
    }

    private void setAlphas(List<ComputedContingencyElement> contingencyElements, SensitivityFactorGroup sensitivityFactorGroup, DenseMatrix states,
                           LfNetwork lfNetwork, EquationSystem equationSystem) {
        ComputedContingencyElement.setContingencyIndexes(contingencyElements);
        DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
        DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
        for (ComputedContingencyElement element : contingencyElements) {
            if (!element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
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

    private List<Set<ComputedContingencyElement>> getElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix preContingencyStates,
                                                                             List<ComputedContingencyElement> contingencyElements,
                                                                             EquationSystem equationSystem) {
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

        // index factors by variable configuration to compute minimal number of states
        List<SensitivityFactorGroup> factorsGroups = createFactorGroups(network, factors);

        if (factorsGroups.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        // Compute the partipation for every factor
        List<ParticipatingElement> participatingElements = null;
        Function<String, Map<String, Double>> getParticipationForBus;
        if (lfParameters.isDistributedSlack()) {
            participatingElements = getParticipatingElements(lfNetwork, lfParameters);
            Map<String, Double> participationPerBus = participatingElements.stream().collect(Collectors.toMap(
                element -> getParticipatingElementBus(element).getId(),
                element -> -element.getFactor(),
                Double::sum
            ));
            getParticipationForBus = busId -> participationPerBus;
        } else {
            getParticipationForBus = busId -> Collections.singletonMap(lfNetwork.getSlackBus().getId(), -1d);
        }
        computeFactorsInjection(getParticipationForBus, factorsGroups, new HashMap<>());

        Map<String, ComputedContingencyElement> contingenciesElements = contingencies.stream()
                                                                                    .flatMap(contingency -> contingency.getElements().stream())
                                                                                    .map(ComputedContingencyElement::new)
                                                                                    .collect(Collectors.toMap(
                                                                                        computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                                                                        computedContingencyElement -> computedContingencyElement
                                                                                    ));

        ComputedContingencyElement.setGlobalIndexes(contingenciesElements.values(), factorsGroups.size());

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);
        // compute pre-contingency sensitivity values
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsGroups, contingenciesElements);
        DenseMatrix states = solveTransposed(rhs, j); // states contains angles for the sensitivity factors, but also for the +1-1 related to contingencies

        // sensitivities without contingency
        List<SensitivityValue> sensitivityValues = calculateSensitivityValues(lfNetwork, equationSystem, factorsGroups,
                states, functionReferenceByBranch, Collections.emptyList(), Collections.emptyMap());

        Collection<Contingency> straightforwardContingencies = new LinkedList<>();
        Map<List<Set<ComputedContingencyElement>>, List<Contingency>> losingConnectivityContingency = new HashMap<>();

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
            Map<SensitivityFactor, Double> predefinedResults = new HashMap<>();

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
                getParticipationForBus = busId -> participationPerCc.get(connectivity.getConnectivity().getComponentNumber(lfNetwork.getBusById(busId)));
                computeFactorsInjection(getParticipationForBus, factorsGroups, predefinedResults);

                ComputedContingencyElement.setGlobalIndexes(contingenciesElements.values(), factorsGroups.size());
                rhs = initRhs(lfNetwork, equationSystem, factorsGroups, contingenciesElements);
                states = solveTransposed(rhs, j);
            }

            for (SensitivityFactor factor : factors) {
                if (!(factor instanceof BranchFlowPerInjectionIncrease)) {
                    throw new UnsupportedOperationException("Only factors of type BranchFlowPerInjectionIncrease are supported for post-contingency analysis");
                }
                LfBranch lfBranch = lfNetwork.getBranchById(factor.getFunction().getId());
                LfBus lfBus = getInjectionBus(network, lfNetwork, (BranchFlowPerInjectionIncrease) factor);
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
