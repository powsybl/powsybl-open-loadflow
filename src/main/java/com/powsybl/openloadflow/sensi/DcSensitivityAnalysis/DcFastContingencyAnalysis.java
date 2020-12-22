/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.DcSensitivityAnalysis;

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
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import org.jgrapht.alg.util.Pair;

import java.util.*;
import java.util.stream.Collectors;


/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcFastContingencyAnalysis extends AbstractDcSensitivityAnalysis {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-6;

    static class ComputedContingencyElement {
        private int contingencyIndex = -1;
        private int globalIndex = -1;
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

        public int getGlobalIndex() {
            return globalIndex;
        }

        public void setGlobalIndex(final int index) {
            this.globalIndex = index;
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
    }

    private DcSlowContingencyAnalysis backupSensitivityAnalysis;

    public DcFastContingencyAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
        backupSensitivityAnalysis = new DcSlowContingencyAnalysis(matrixFactory);
    }

    private SensitivityValue createBranchSensitivityValue(LfNetwork lfNetwork, EquationSystem equationSystem, String branchId,
                                                          SensitivityFactor<?, ?> factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, Map<String, Double> functionReferenceByBranch,
                                                          List<ComputedContingencyElement> contingencyElements) {
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        if (lfBranch == null) {
            if (computeSensitivityOnContingency()) {
                return new SensitivityValue(factor, 0d, functionReferenceByBranch.get(branchId), 0);
            } else {
                throw new PowsyblException("Branch '" + branchId + "' not found");
            }
        }

        ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        double value = p1.calculate(states, factorGroup.getIndex());

        for (ComputedContingencyElement contingencyElement : contingencyElements) {
            // todo: manage contingency if it is not a branch / throw exception
            if (contingencyElement.getElement().getId().equals(factor.getFunction().getId())) {
                // the sensitivity on a removed branch is 0
                value = 0d;
                break;
            }
            value = value + contingencyElement.getAlpha() * p1.calculate(states, contingencyElement.getGlobalIndex());
        }

        return new SensitivityValue(factor, value * PerUnit.SB, functionReferenceByBranch.get(branchId), 0);
    }

    protected List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                                                                           Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                                                                                           DenseMatrix states, Map<String, Double> functionReferenceByBranch, List<ComputedContingencyElement> contingencyElements) {
        List<SensitivityValue> sensitivityValuesContingencies = new ArrayList<>();
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityFactorGroup factorGroup = e.getValue();
            setAlphas(contingencyElements, factorGroup, states, lfNetwork, equationSystem);
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    sensitivityValuesContingencies.add(createBranchSensitivityValue(lfNetwork, equationSystem, injectionFactor.getFunction().getBranchId(),
                            factor, factorGroup, states, functionReferenceByBranch, contingencyElements));
                } else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    sensitivityValuesContingencies.add(createBranchSensitivityValue(lfNetwork, equationSystem, pstAngleFactor.getFunction().getBranchId(),
                            factor, factorGroup, states, functionReferenceByBranch, contingencyElements));
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }
        }

        return sensitivityValuesContingencies;
    }

    private void setAlphas(List<ComputedContingencyElement> contingencyElements, SensitivityFactorGroup sensitivityFactorGroup, DenseMatrix states, LfNetwork lfNetwork, EquationSystem equationSystem) {
        DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
        DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
        for (ComputedContingencyElement element : contingencyElements) {
            if (!element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Can only use contingencies on branches");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
            rhs.set(
                    element.getContingencyIndex(),
                    0,
                    states.get(p1.getVariables().get(0).getRow(), sensitivityFactorGroup.getIndex()) - states.get(p1.getVariables().get(1).getRow(), sensitivityFactorGroup.getIndex())
            );
            for (ComputedContingencyElement element2 : contingencyElements) {
                double value = 0d;
                if (element.equals(element2)) {
                    value = lfBranch.getPiModel().getX() / PerUnit.SB;
                }
                value = value - (states.get(p1.getVariables().get(0).getRow(), element2.getGlobalIndex()) - states.get(p1.getVariables().get(1).getRow(), element2.getGlobalIndex()));
                matrix.set(element.getContingencyIndex(), element2.getContingencyIndex(), value);
            }
        }
        LUDecomposition lu = matrix.decomposeLU();
        lu.solve(rhs); // rhs now contains state matrix
        contingencyElements.forEach(element -> element.setAlpha(rhs.get(element.getContingencyIndex(), 0)));
    }

    private void setAlphas(Map<String, List<ComputedContingencyElement>> contingencyElements, SensitivityFactorGroup sensitivityFactorGroup, DenseMatrix states, LfNetwork lfNetwork, EquationSystem equationSystem) {
        contingencyElements.values().forEach(elements -> setAlphas(elements, sensitivityFactorGroup, states, lfNetwork, equationSystem));
    }

    private DenseMatrix initRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                List<ComputedContingencyElement> elements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsByVarConfig.size() + elements.size());
        fillRhs(lfNetwork, equationSystem, factorsByVarConfig, elements, rhs);
        return rhs;
    }

    protected void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig, List<ComputedContingencyElement> contingencyElements, Matrix rhs) {
        fillRhs(lfNetwork, equationSystem, factorsByVarConfig, rhs); // fill sensitivity vectors
        for (ComputedContingencyElement element : contingencyElements) {
            if (element.getElement().getType().equals(ContingencyElementType.BRANCH)) {
                LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
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
                throw new UnsupportedOperationException("Can only use contingencies on branches");
            }
        }
    }

    private boolean isAbleToCompute(LfNetwork lfNetwork, DenseMatrix states, List<ComputedContingencyElement> contingencyElements, EquationSystem equationSystem) {
        for (ComputedContingencyElement element : contingencyElements) {
            double sum = contingencyElements.stream()
                                            .map(element2 -> lfNetwork.getBranchById(element2.getElement().getId()))
                                            .map(lfBranch -> equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class))
                                            .mapToDouble(equationTerm -> equationTerm.calculate(states, element.getGlobalIndex()))
                                            .map(Math::abs)
                                            .sum();
            if (Math.abs(sum * PerUnit.SB - 1d) < CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                return false;
            }
        }
        return true;
    }

    private List<SensitivityValue> analyseContingency(LfNetwork lfNetwork, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig, EquationSystem equationSystem, JacobianMatrix jacobian, Contingency contingency, Map<String, Double> functionReferenceByBranch, List<Contingency> unableToCompute) {
        int index = 0;
        List<ComputedContingencyElement> elements = contingency != null ?
                contingency.getElements().stream().map(ComputedContingencyElement::new).collect(Collectors.toList())
                : Collections.emptyList();

        for (ComputedContingencyElement element : elements) {
            element.setContingencyIndex(index);
            element.setGlobalIndex(factorsByVarConfig.size() + index);
            index++;
        }
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig, elements);
        DenseMatrix states = solveTransposed(rhs, jacobian);
        if (!isAbleToCompute(lfNetwork, states, elements, equationSystem)) {
            unableToCompute.add(contingency);
            return Collections.emptyList();
        }
        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states, functionReferenceByBranch, elements);
    }

    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, List<Contingency> contingencies, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                                                     OpenSensitivityAnalysisParameters sensiParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(sensiParametersExt);
        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        // run DC load
        Map<String, Double> functionReferenceByBranch = getFunctionReferenceByBranch(lfNetworks, lfParameters, lfParametersExt);

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, factors, lfNetwork, lfParameters);

        if (factorsByVarConfig.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        List<Contingency> contingencyToComputeSlowly = new LinkedList<>();
        List<SensitivityValue> sensitivityValues = analyseContingency(lfNetwork, factorsByVarConfig, equationSystem, j, null, functionReferenceByBranch, contingencyToComputeSlowly);
        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();

        for (Contingency contingency : contingencies) {
            List<SensitivityValue> contingencyValues = analyseContingency(lfNetwork, factorsByVarConfig, equationSystem, j, contingency, functionReferenceByBranch, contingencyToComputeSlowly);
            contingenciesValue.put(contingency.getId(), contingencyValues);
        }

        // todo: we could just wrap the contingency into another object and flag it as "slowRequired"
        for (Contingency contingency : contingencyToComputeSlowly) {
            List<SensitivityValue> contingencyValues = backupSensitivityAnalysis.analyseContingency(network, factors, contingency, functionReferenceByBranch, lfParameters, lfParametersExt, sensiParametersExt);
            contingenciesValue.put(contingency.getId(), contingencyValues);
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
