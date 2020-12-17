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
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.GenerationActionPowerDistributionStep;
import com.powsybl.openloadflow.network.util.LoadActivePowerDistributionStep;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
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
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {
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

    static class SensitivityVariableConfiguration {

        private final Map<String, Double> busInjectionById;

        private final Set<String> phaseTapChangerHolderIds;

        SensitivityVariableConfiguration(Map<String, Double> busInjectionById, Set<String> phaseTapChangerHolderIds) {
            this.busInjectionById = Objects.requireNonNull(busInjectionById);
            this.phaseTapChangerHolderIds = Objects.requireNonNull(phaseTapChangerHolderIds);
        }

        Map<String, Double> busInjectionById() {
            return busInjectionById;
        }

        Set<String> getPhaseTapChangerHolderIds() {
            return phaseTapChangerHolderIds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SensitivityVariableConfiguration that = (SensitivityVariableConfiguration) o;
            return busInjectionById.equals(that.busInjectionById) && phaseTapChangerHolderIds.equals(that.phaseTapChangerHolderIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(busInjectionById);
        }
    }

    static class SensitivityFactorGroup {

        private final List<SensitivityFactor<?, ?>> factors = new ArrayList<>();

        private int index = -1;

        List<SensitivityFactor<?, ?>> getFactors() {
            return factors;
        }

        int getIndex() {
            return index;
        }

        void setIndex(int index) {
            this.index = index;
        }
    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private SensitivityValue createBranchSensitivityValue(LfNetwork lfNetwork, EquationSystem equationSystem, String branchId,
                                                          SensitivityFactor<?, ?> factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, Map<String, Double> functionReferenceByBranch,
                                                          List<ComputedContingencyElement> contingencyElements) {
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        if (lfBranch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
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

    private Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                              Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                                              DenseMatrix states, Map<String, Double> functionReferenceByBranch, Map<String, List<ComputedContingencyElement>> contingencyMap) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>();
        Map<String, List<SensitivityValue>> sensitivityValuesContingencies = new HashMap<>();
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityFactorGroup factorGroup = e.getValue();
            setAlphas(contingencyMap, factorGroup, states, lfNetwork, equationSystem);
            // todo: refactor
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, injectionFactor.getFunction().getBranchId(),
                            factor, factorGroup, states, functionReferenceByBranch, Collections.emptyList()));
                } else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, pstAngleFactor.getFunction().getBranchId(),
                            factor, factorGroup, states, functionReferenceByBranch, Collections.emptyList()));
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
                for (Map.Entry<String, List<ComputedContingencyElement>> contingencyMapEntry : contingencyMap.entrySet()) {
                    List<SensitivityValue> contingencySensitivityValues = sensitivityValuesContingencies.computeIfAbsent(contingencyMapEntry.getKey(), key -> new ArrayList<>());
                    if (factor instanceof BranchFlowPerInjectionIncrease) {
                        BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                        contingencySensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, injectionFactor.getFunction().getBranchId(),
                                factor, factorGroup, states, functionReferenceByBranch, contingencyMapEntry.getValue()));
                    } else if (factor instanceof BranchFlowPerPSTAngle) {
                        BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                        contingencySensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, pstAngleFactor.getFunction().getBranchId(),
                                factor, factorGroup, states, functionReferenceByBranch, contingencyMapEntry.getValue()));
                    } else {
                        throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                    }
                }
            }
        }
        return Pair.of(sensitivityValues, sensitivityValuesContingencies);
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

    private DenseMatrix initRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig, Map<String, List<ComputedContingencyElement>> contingencyMap) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsByVarConfig.size() + contingencyMap.values().stream().mapToInt(List::size).sum());
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityVariableConfiguration configuration = e.getKey();
            SensitivityFactorGroup factorGroup = e.getValue();
            for (Map.Entry<String, Double> busAndInjection : configuration.busInjectionById().entrySet()) {
                LfBus lfBus = lfNetwork.getBusById(busAndInjection.getKey());
                if (lfBus.isSlack()) {
                    throw new PowsyblException("Cannot set injection increase at slack bus");
                }
                Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), factorGroup.getIndex(), busAndInjection.getValue() / PerUnit.SB);
            }
            for (String phaseTapChangerHolderId : configuration.getPhaseTapChangerHolderIds()) {
                LfBranch lfBranch = lfNetwork.getBranchById(phaseTapChangerHolderId);
                Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
                rhs.set(a1.getColumn(), factorGroup.getIndex(), Math.toRadians(1d));
            }
        }

        for (ComputedContingencyElement element : contingencyMap.values().stream().flatMap(List::stream).collect(Collectors.toList())) {
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
        return rhs;
    }

    private Map<SensitivityVariableConfiguration, SensitivityFactorGroup> indexFactorsByVariableConfig(Network network, List<SensitivityFactor> factors, LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters) {
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = new LinkedHashMap<>(factors.size());
        Map<String, Double> participationFactorByBus = getParticipationFactorByBus(lfNetwork, loadFlowParameters); // empty if slack is not distributed
        participationFactorByBus.remove(lfNetwork.getSlackBus().getId()); // the injection on the slack bus will not appear in the rhs
        participationFactorByBus.replaceAll((key, value) -> -value); // the slack distribution on a bus will be the opposite of its participation

        // index factors by variable config
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                Bus bus = injection.getTerminal().getBusView().getBus();
                // skip disconnected injections
                if (bus != null) {
                    Map<String, Double> busInjectionById = new HashMap<>(participationFactorByBus);
                    // add 1 where we are making the injection
                    // when the slack is not distributed, then bus compensation is a singleton {bus -> 1}
                    if (lfNetwork.getBusById(bus.getId()).isSlack()) {
                        if (!loadFlowParameters.isDistributedSlack()) {
                            throw new PowsyblException("Cannot compute injection increase at slack bus in case of non distributed slack");
                        }
                    } else {
                        busInjectionById.put(bus.getId(), busInjectionById.getOrDefault(bus.getId(), 0d) + 1);
                    }

                    SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(busInjectionById, Collections.emptySet());
                    factorsByVarConfig.computeIfAbsent(varConfig, k -> new SensitivityFactorGroup())
                            .getFactors().add(injectionFactor);
                }
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                String phaseTapChangerHolderId = pstAngleFactor.getVariable().getPhaseTapChangerHolderId();
                TwoWindingsTransformer twt = network.getTwoWindingsTransformer(phaseTapChangerHolderId);
                if (twt == null) {
                    throw new PowsyblException("Phase shifter '" + phaseTapChangerHolderId + "' not found");
                }
                SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(Collections.emptyMap(), Collections.singleton(phaseTapChangerHolderId));
                factorsByVarConfig.computeIfAbsent(varConfig, k -> new SensitivityFactorGroup())
                        .getFactors().add(pstAngleFactor);
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
        }

        // assign an index to each factor group
        int index = 0;
        for (SensitivityFactorGroup factorGroup : factorsByVarConfig.values()) {
            factorGroup.setIndex(index++);
        }

        return factorsByVarConfig;
    }

    private String getParticipatingElementBusId(ParticipatingElement participatingElement) {
        if (participatingElement.getElement() instanceof LfGenerator) {
            return ((LfGenerator) participatingElement.getElement()).getBus().getId();
        } else if (participatingElement.getElement() instanceof LfBus) {
            return ((LfBus) participatingElement.getElement()).getId();
        } else {
            throw new UnsupportedOperationException("Unsupported participating element");
        }
    }

    /**
     * Return a mapping between the participating element id through its connected bus, and its participation value in the slack distribution
     * @param lfNetwork
     * @param loadFlowParameters
     * @return
     */
    private Map<String, Double> getParticipationFactorByBus(LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters) {

        Map<String, Double> participationFactorByBusMap = new HashMap<>();

        if (loadFlowParameters.isDistributedSlack()) {
            ActivePowerDistribution.Step step;
            List<ParticipatingElement> participatingElements;

            switch (loadFlowParameters.getBalanceType()) {
                case PROPORTIONAL_TO_GENERATION_P_MAX:
                    step = new GenerationActionPowerDistributionStep();
                    break;
                case PROPORTIONAL_TO_LOAD:
                    step = new LoadActivePowerDistributionStep(false, false);
                    break;
                default:
                    throw new UnsupportedOperationException("Balance type not yet supported: " + loadFlowParameters.getBalanceType());
            }

            participatingElements = step.getParticipatingElements(lfNetwork);
            ParticipatingElement.normalizeParticipationFactors(participatingElements, "bus");

            participationFactorByBusMap = participatingElements.stream().collect(Collectors.toMap(
                this::getParticipatingElementBusId,
                ParticipatingElement::getFactor,
                Double::sum
            ));
        }
        return participationFactorByBusMap;
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
        Map<String, Double> functionReferenceByBranch = new HashMap<>();
        DcLoadFlowParameters dcLoadFlowParameters = new DcLoadFlowParameters(lfParametersExt.getSlackBusSelector(), matrixFactory,
                true, lfParametersExt.isDcUseTransformerRatio(), lfParameters.isDistributedSlack(),  lfParameters.getBalanceType());
        DcLoadFlowResult dcLoadFlowResult = new DcLoadFlowEngine(lfNetworks, dcLoadFlowParameters).run();
        for (LfBranch branch : dcLoadFlowResult.getNetwork().getBranches()) {
            functionReferenceByBranch.put(branch.getId(), branch.getP1() * PerUnit.SB);
        }

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, factors, lfNetwork, lfParameters);

        if (factorsByVarConfig.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        Map<String, List<ComputedContingencyElement>> contingencyMap = new HashMap<>(); // may be a specific object like VariableConfiguration ?

        if (contingencies.size() > 0) {
            contingencyMap = contingencies.stream().collect(Collectors.toMap(
                Contingency::getId,
                contingency -> contingency.getElements().stream().map(ComputedContingencyElement::new).collect(Collectors.toList())
            ));
        }
        int globalIndex = factorsByVarConfig.size(); // index in the rhs
        for (List<ComputedContingencyElement> elements : contingencyMap.values()) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index);
                element.setGlobalIndex(globalIndex);
                index++;
                globalIndex++;
            }
        }

        // initialize right hand side
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig, contingencyMap);

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // solve system
        DenseMatrix states = solveTransposed(rhs, j);

        // calculate sensitivity values
        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states, functionReferenceByBranch, contingencyMap);
    }
}
