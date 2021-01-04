/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class DcFastSensitivityAnalysis extends AbstractDcSensitivityAnalysis {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-6;

    static class ComputedContingencyElement {
        private int contingencyIndex = -1;
        private double alpha = Double.NaN;
        private final ContingencyElement element;

        public ComputedContingencyElement(final ContingencyElement element) {
            this.element = element;
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

        public static void setIndexes(List<ComputedContingencyElement> elements) {
            IntStream.range(0, elements.size()).forEach(idx -> elements.get(idx).setContingencyIndex(idx));
        }
    }

    private DcSlowSensitivityAnalysis backupSensitivityAnalysis;

    public DcFastSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
        backupSensitivityAnalysis = new DcSlowSensitivityAnalysis(matrixFactory);
    }

    private SensitivityValue createBranchSensitivityValue(LfBranch lfBranch, EquationSystem equationSystem,
                                                          SensitivityFactor<?, ?> factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, DenseMatrix contingencyStates, Double functionReference,
                                                          List<ComputedContingencyElement> contingencyElements, Double predefinedValue) {
        ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
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
                value = value + contingencyElement.getAlpha() * p1.calculate(contingencyStates, contingencyElement.getContingencyIndex());
            }
        }
        return new SensitivityValue(factor, value * PerUnit.SB, functionReference, 0);
    }

    protected List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                                Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                                                DenseMatrix states, DenseMatrix contingencyStates, Map<String, Double> functionReferenceByBranch,
                                                                List<ComputedContingencyElement> contingencyElements, Map<SensitivityFactor, Double> predefinedResults) {
        List<SensitivityValue> sensitivityValuesContingencies = new ArrayList<>();
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityFactorGroup factorGroup = e.getValue();
            setAlphas(contingencyElements, factorGroup, states, contingencyStates, lfNetwork, equationSystem);
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    String branchId = injectionFactor.getFunction().getBranchId();
                    sensitivityValuesContingencies.add(createBranchSensitivityValue(lfNetwork.getBranchById(branchId), equationSystem,
                            factor, factorGroup, states, contingencyStates, functionReferenceByBranch.get(branchId), contingencyElements, predefinedResults.get(factor)));
                } else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    String branchId = pstAngleFactor.getFunction().getBranchId();
                    sensitivityValuesContingencies.add(createBranchSensitivityValue(lfNetwork.getBranchById(branchId), equationSystem,
                            factor, factorGroup, states, contingencyStates, functionReferenceByBranch.get(branchId), contingencyElements, predefinedResults.get(factor)));
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }
        }
        return sensitivityValuesContingencies;
    }

    private void setAlphas(List<ComputedContingencyElement> contingencyElements, SensitivityFactorGroup sensitivityFactorGroup, DenseMatrix preContingencyStates,
                           DenseMatrix postContingenciesStates, LfNetwork lfNetwork, EquationSystem equationSystem) {
        DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
        DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
        for (ComputedContingencyElement element : contingencyElements) {
            if (!element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
            rhs.set(element.getContingencyIndex(), 0, preContingencyStates.get(p1.getVariables().get(0).getRow(), sensitivityFactorGroup.getIndex())
                            - preContingencyStates.get(p1.getVariables().get(1).getRow(), sensitivityFactorGroup.getIndex())
            );
            for (ComputedContingencyElement element2 : contingencyElements) {
                double value = 0d;
                if (element.equals(element2)) {
                    value = lfBranch.getPiModel().getX() / PerUnit.SB;
                }
                value = value - (postContingenciesStates.get(p1.getVariables().get(0).getRow(), element2.getContingencyIndex())
                        - postContingenciesStates.get(p1.getVariables().get(1).getRow(), element2.getContingencyIndex()));
                matrix.set(element.getContingencyIndex(), element2.getContingencyIndex(), value);
            }
        }
        LUDecomposition lu = matrix.decomposeLU();
        lu.solve(rhs); // rhs now contains state matrix
        contingencyElements.forEach(element -> element.setAlpha(rhs.get(element.getContingencyIndex(), 0)));
    }

    private DenseMatrix initPostContingenciesRhs(LfNetwork lfNetwork, EquationSystem equationSystem, List<ComputedContingencyElement> contingencyElements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), contingencyElements.size());
        for (ComputedContingencyElement element : contingencyElements) {
            if (element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
                if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                    continue;
                }
                LfBus bus1 = lfBranch.getBus1();
                LfBus bus2 = lfBranch.getBus2();
                if (bus1.isSlack()) {
                    Equation p = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    rhs.set(p.getColumn(), element.getContingencyIndex(), -1 / PerUnit.SB);
                } else if (bus2.isSlack()) {
                    Equation p = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    rhs.set(p.getColumn(), element.getContingencyIndex(), 1 / PerUnit.SB);
                } else {
                    Equation p1 = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    Equation p2 = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    rhs.set(p1.getColumn(), element.getContingencyIndex(), 1 / PerUnit.SB);
                    rhs.set(p2.getColumn(), element.getContingencyIndex(), -1 / PerUnit.SB);
                }
            } else {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
        }
        return rhs;
    }

    private List<List<ComputedContingencyElement>> getElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix preContingencyStates,
                                                                             List<ComputedContingencyElement> contingencyElements,
                                                                             EquationSystem equationSystem) {
        List<List<ComputedContingencyElement>> responsibleAssociations = new LinkedList<>();
        for (ComputedContingencyElement element : contingencyElements) {
            List<ComputedContingencyElement> responsibleElements = new LinkedList<>();
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculate(preContingencyStates, element.getContingencyIndex()));
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

    private List<SensitivityValue> analyseContingency(Network network, LfNetwork lfNetwork, DenseMatrix preContingencyStates, LazyConnectivity lazyConnectivity,
                                                      List<SensitivityFactor> factors, final Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                                      EquationSystem equationSystem, JacobianMatrix jacobian, Contingency contingency,
                                                      Map<String, Double> functionReferenceByBranch, LoadFlowParameters lfParameters) {
        List<ComputedContingencyElement> elements = contingency != null
                ? contingency.getElements().stream().map(ComputedContingencyElement::new).collect(Collectors.toList())
                : new ArrayList<>();

        ComputedContingencyElement.setIndexes(elements);

        DenseMatrix postContingenciesRhs = initPostContingenciesRhs(lfNetwork, equationSystem, elements);
        DenseMatrix postContingenciesStates = solveTransposed(postContingenciesRhs, jacobian); // states for +1-1 on each contingency branch

        // connectivity analysis
        List<List<ComputedContingencyElement>> groupOfElementsBreakingConnectivity = getElementsBreakingConnectivity(lfNetwork, postContingenciesStates, elements, equationSystem);
        if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
            return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, preContingencyStates,
                    postContingenciesStates, functionReferenceByBranch, elements, Collections.emptyMap());
        }

        if (groupOfElementsBreakingConnectivity.stream().mapToInt(List::size).max().getAsInt() > 1) { // connectivity broken in N-k
            return backupSensitivityAnalysis.analyseContingency(network, lfNetwork, lazyConnectivity.getConnectivity(),
                    equationSystem, contingency, factors, functionReferenceByBranch, lfParameters);
        }

        // connectivity broken in N-1 (due to a single element)
        GraphDecrementalConnectivity<LfBus> connectivity = lazyConnectivity.getConnectivity();
        Map<SensitivityFactor, Double> postContingenciesPredefinedResults = new HashMap<>(); // to store the result that should be 0 because of the connectivity loss
        // we need to recompute the new participation factors to slack distribution
        for (ComputedContingencyElement contingencyElement : groupOfElementsBreakingConnectivity.stream().flatMap(List::stream).collect(Collectors.toList())) {
            ContingencyElement element = contingencyElement.getElement();
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(element.getId());
            connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2());
            elements.remove(contingencyElement);
        }
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> remainingFactors = indexFactorsByVariableConfig(network, connectivity, factors, lfNetwork, lfParameters);

        // now, we use the connectivity to know if some sensitivities must be 0
        for (SensitivityFactor factor : factors) {
            if (!(factor instanceof BranchFlowPerInjectionIncrease)) {
                throw new UnsupportedOperationException("Only factors of type BranchFlowPerInjectionIncrease are supported for post-contingency analysis");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(factor.getFunction().getId());
            LfBus lfBus = getInjectionBus(network, lfNetwork, (BranchFlowPerInjectionIncrease) factor);
            if (connectivity.getComponentNumber(lfBus) != connectivity.getComponentNumber(lfBranch.getBus1())
                    || connectivity.getComponentNumber(lfBus) != connectivity.getComponentNumber(lfBranch.getBus2()))  {
                postContingenciesPredefinedResults.put(factor, 0d);
            }
        }
        connectivity.reset();
        // then, we need to compute the states for the new distribution of the slack
        DenseMatrix states;
        if (lfParameters.isDistributedSlack()) {
            DenseMatrix rhs = initRhs(lfNetwork, equationSystem, remainingFactors);
            states = solveTransposed(rhs, jacobian);
        } else {
            states = preContingencyStates;
        }

        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states, postContingenciesStates,
                functionReferenceByBranch, elements, postContingenciesPredefinedResults);
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
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, null, factors, lfNetwork, lfParameters);
        if (factorsByVarConfig.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // compute pre-contingency sensitivity values
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig);
        DenseMatrix preContingencyStates = solveTransposed(rhs, j);
        List<SensitivityValue> sensitivityValues = analyseContingency(network, lfNetwork, preContingencyStates, connectivity, factors,
                factorsByVarConfig, equationSystem, j, null, functionReferenceByBranch, lfParameters);

        // compute post-contingency sensitivity values
        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();
        for (Contingency contingency : contingencies) {
            List<SensitivityValue> contingencyValues = analyseContingency(network, lfNetwork, preContingencyStates, connectivity, factors,
                    factorsByVarConfig, equationSystem, j, contingency, functionReferenceByBranch, lfParameters);
            contingenciesValue.put(contingency.getId(), contingencyValues);
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
