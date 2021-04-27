/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.HvdcConverterStations;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.PropagatedContingency;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public abstract class AbstractSensitivityAnalysis {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSensitivityAnalysis.class);

    protected final MatrixFactory matrixFactory;

    protected final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider;

    protected AbstractSensitivityAnalysis(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityProvider = Objects.requireNonNull(connectivityProvider);
    }

    protected static Terminal getEquipmentRegulatingTerminal(Network network, String equipmentId) {
        Generator generator = network.getGenerator(equipmentId);
        if (generator != null) {
            return generator.getRegulatingTerminal();
        }
        StaticVarCompensator staticVarCompensator = network.getStaticVarCompensator(equipmentId);
        if (staticVarCompensator != null) {
            return staticVarCompensator.getRegulatingTerminal();
        }
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer(equipmentId);
        if (t2wt != null) {
            RatioTapChanger rtc = t2wt.getRatioTapChanger();
            if (rtc != null) {
                throw new NotImplementedException(String.format("[%s] Bus target voltage on two windings transformer is not managed yet", equipmentId));
            }
            return null;
        }
        ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer(equipmentId);
        Terminal regulatingTerminal = null;
        if (t3wt != null) {
            for (ThreeWindingsTransformer.Leg leg : t3wt.getLegs()) {
                RatioTapChanger rtc = leg.getRatioTapChanger();
                if (rtc != null && rtc.isRegulating()) {
                    regulatingTerminal = rtc.getRegulationTerminal();
                }
            }
            if (regulatingTerminal != null) {
                throw new NotImplementedException(String.format("[%s] Bus target voltage on three windings transformer is not managed yet", equipmentId));
            }
            return null;
        }
        ShuntCompensator shuntCompensator = network.getShuntCompensator(equipmentId);
        if (shuntCompensator != null) {
            return shuntCompensator.getRegulatingTerminal();
        }
        VscConverterStation vsc = network.getVscConverterStation(equipmentId);
        if (vsc != null) {
            return vsc.getTerminal(); // local regulation only
        }
        return null;
    }

    protected JacobianMatrix createJacobianMatrix(EquationSystem equationSystem, VoltageInitializer voltageInitializer) {
        double[] x = equationSystem.createStateVector(voltageInitializer);
        equationSystem.updateEquations(x);
        return new JacobianMatrix(equationSystem, matrixFactory);
    }

    interface LfSensitivityFactor {

        enum Status {
            VALID,
            SKIP,
            ZERO
        }

        Object getContext();

        String getVariableId();

        SensitivityVariableType getVariableType();

        LfElement getFunctionElement();

        SensitivityFunctionType getFunctionType();

        EquationTerm getEquationTerm();

        Double getPredefinedResult();

        void setPredefinedResult(Double predefinedResult);

        double getFunctionReference();

        void setFunctionReference(double functionReference);

        double getBaseSensitivityValue();

        void setBaseCaseSensitivityValue(double baseCaseSensitivityValue);

        Status getStatus();

        void setStatus(Status status);

        boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity);

        boolean isConnectedToComponent(Set<LfBus> connectedComponent);
    }

    abstract static class AbstractLfSensitivityFactor implements LfSensitivityFactor {

        // Wrap factors in specific class to have instant access to their branch and their equation term
        private final Object context;

        private final String variableId;

        protected final LfElement functionElement;

        protected final SensitivityFunctionType functionType;

        protected final SensitivityVariableType variableType;

        private Double predefinedResult = null;

        private double functionReference = 0d;

        private double baseCaseSensitivityValue = Double.NaN; // the sensitivity value on pre contingency network, that needs to be recomputed if the stack distribution change

        protected Status status = Status.VALID;

        public AbstractLfSensitivityFactor(Object context, String variableId,
                                           LfElement functionElement, SensitivityFunctionType functionType,
                                           SensitivityVariableType variableType) {
            this.context = context;
            this.variableId = Objects.requireNonNull(variableId);
            this.functionElement = functionElement;
            this.functionType = functionType;
            this.variableType = variableType;
            if (functionElement == null) {
                status = Status.ZERO;
            }
        }

        @Override
        public Object getContext() {
            return context;
        }

        @Override
        public String getVariableId() {
            return variableId;
        }

        @Override
        public SensitivityVariableType getVariableType() {
            return variableType;
        }

        @Override
        public LfElement getFunctionElement() {
            return functionElement;
        }

        @Override
        public SensitivityFunctionType getFunctionType() {
            return functionType;
        }

        @Override
        public EquationTerm getEquationTerm() {
            switch (functionType) {
                case BRANCH_ACTIVE_POWER:
                    return (EquationTerm) ((LfBranch) functionElement).getP1();
                case BRANCH_CURRENT:
                    return (EquationTerm) ((LfBranch) functionElement).getI1();
                case BUS_VOLTAGE:
                    return (EquationTerm) ((LfBus) functionElement).getV();
                default:
                    throw new PowsyblException("Function type " + functionType + " is not implement.");
            }
        }

        @Override
        public Double getPredefinedResult() {
            return predefinedResult;
        }

        @Override
        public void setPredefinedResult(Double predefinedResult) {
            this.predefinedResult = predefinedResult;
        }

        @Override
        public double getFunctionReference() {
            return functionReference;
        }

        @Override
        public void setFunctionReference(double functionReference) {
            this.functionReference = functionReference;
        }

        @Override
        public double getBaseSensitivityValue() {
            return baseCaseSensitivityValue;
        }

        @Override
        public void setBaseCaseSensitivityValue(double baseCaseSensitivityValue) {
            this.baseCaseSensitivityValue = baseCaseSensitivityValue;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public void setStatus(Status status) {
            this.status = status;
        }

        protected boolean areElementsDisconnected(LfElement functionElement, LfElement variableElement, GraphDecrementalConnectivity<LfBus> connectivity) {
            if (functionElement instanceof LfBus && variableElement instanceof LfBus) {
                return connectivity.getComponentNumber((LfBus) functionElement) != connectivity.getComponentNumber((LfBus) variableElement);
            } else if (functionElement instanceof LfBranch && variableElement instanceof LfBus) {
                LfBranch branch = (LfBranch) functionElement;
                return connectivity.getComponentNumber(branch.getBus1()) != connectivity.getComponentNumber((LfBus) variableElement)
                    || connectivity.getComponentNumber(branch.getBus2()) != connectivity.getComponentNumber((LfBus) variableElement);
            } else if (functionElement instanceof LfBranch && variableElement instanceof LfBranch) {
                LfBranch functionBranch = (LfBranch) functionElement;
                LfBranch variableBranch = (LfBranch) variableElement;
                return connectivity.getComponentNumber(variableBranch.getBus1()) != connectivity.getComponentNumber(functionBranch.getBus1())
                    || connectivity.getComponentNumber(variableBranch.getBus1()) != connectivity.getComponentNumber(functionBranch.getBus2());
            }
            throw new PowsyblException("Combination of function type and variable type is not implemented.");
        }

        protected boolean isElementConnectedToComponent(LfElement element, Set<LfBus> component) {
            if (element instanceof LfBus) {
                return component.contains(element);
            } else if (element instanceof LfBranch) {
                return component.contains(((LfBranch) element).getBus1()) && component.contains(((LfBranch) element).getBus2());
            }
            throw new PowsyblException("Cannot compute connectivity for variable element of class: " + element.getClass().getSimpleName());
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity) {
            throw new NotImplementedException("areVariableAndFunctionDisconnected should have an override");
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            throw new NotImplementedException("isConnectedToComponent should have an override");
        }
    }

    static class SingleVariableLfSensitivityFactor extends AbstractLfSensitivityFactor {
        private final LfElement variableElement;

        SingleVariableLfSensitivityFactor(Object context, String variableId,
                                          LfElement functionElement, SensitivityFunctionType functionType,
                                          LfElement variableElement, SensitivityVariableType variableType) {
            super(context, variableId, functionElement, functionType, variableType);
            this.variableElement = variableElement;
            if (variableElement == null) {
                status = Status.SKIP;
            }
        }

        public LfElement getVariableElement() {
            return variableElement;
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity) {
            return areElementsDisconnected(functionElement, variableElement, connectivity);
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            return isElementConnectedToComponent(variableElement, connectedComponent);
        }
    }

    static class MultiVariablesLfSensitivityFactor extends AbstractLfSensitivityFactor {
        private final Map<LfElement, Double> weightedVariableElements;

        MultiVariablesLfSensitivityFactor(Object context, String variableId,
                                          LfElement functionElement, SensitivityFunctionType functionType,
                                          Map<LfElement, Double> weightedVariableElements, SensitivityVariableType variableType) {
            super(context, variableId, functionElement, functionType, variableType);
            this.weightedVariableElements = weightedVariableElements;
            if (weightedVariableElements.isEmpty()) {
                status = Status.SKIP;
            }
        }

        public Map<LfElement, Double> getWeightedVariableElements() {
            return weightedVariableElements;
        }

        public Collection<LfElement> getVariableElements() {
            return weightedVariableElements.keySet();
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity) {
            for (LfElement variableElement : getVariableElements()) {
                if (!areElementsDisconnected(functionElement, variableElement, connectivity)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            if (!isElementConnectedToComponent(functionElement, connectedComponent)) {
                return false;
            }
            for (LfElement lfElement : getVariableElements()) {
                if (isElementConnectedToComponent(lfElement, connectedComponent)) {
                    return true;
                }
            }
            return false;
        }
    }

    interface SensitivityFactorGroup {
        List<AbstractSensitivityAnalysis.LfSensitivityFactor> getFactors();

        int getIndex();

        void setIndex(int index);

        void addFactor(AbstractSensitivityAnalysis.LfSensitivityFactor factor);

        void fillRhs(EquationSystem equationSystem, Matrix rhs, Map<LfBus, Double> participationByBus);
    }

    abstract static class AbstractSensitivityFactorGroup implements SensitivityFactorGroup {

        private final List<LfSensitivityFactor> factors = new ArrayList<>();

        SensitivityVariableType variableType;

        private int index = -1;

        AbstractSensitivityFactorGroup(SensitivityVariableType variableType) {
            this.variableType = variableType;
        }

        @Override
        public List<LfSensitivityFactor> getFactors() {
            return factors;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public void setIndex(int index) {
            this.index = index;
        }

        @Override
        public void addFactor(LfSensitivityFactor factor) {
            factors.add(factor);
        }

        protected void addBusInjection(Matrix rhs, LfBus lfBus, Double injection) {
            Equation p = (Equation) lfBus.getP();
            if (lfBus.isSlack() || !p.isActive()) {
                return;
            }
            int column = p.getColumn();
            rhs.add(column, getIndex(), injection / PerUnit.SB);
        }

        @Override
        public void fillRhs(EquationSystem equationSystem, Matrix rhs, Map<LfBus, Double> participationByBus) {
            throw new NotImplementedException("fillRhs should have an override");
        }
    }

    static class SingleVariableFactorGroup extends AbstractSensitivityFactorGroup {

        LfElement variableElement;

        SingleVariableFactorGroup(LfElement variableElement, SensitivityVariableType variableType) {
            super(variableType);
            this.variableElement = variableElement;
        }

        @Override
        public void fillRhs(EquationSystem equationSystem, Matrix rhs, Map<LfBus, Double> participationByBus) {
            switch (variableType) {
                case TRANSFORMER_PHASE:
                    LfBranch lfBranch = (LfBranch) variableElement;
                    Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
                    if (!a1.isActive()) {
                        return;
                    }
                    rhs.set(a1.getColumn(), getIndex(), Math.toRadians(1d));
                    break;
                case INJECTION_ACTIVE_POWER:
                    for (Map.Entry<LfBus, Double> lfBusAndParticipationFactor : participationByBus.entrySet()) {
                        LfBus lfBus = lfBusAndParticipationFactor.getKey();
                        Double injection = lfBusAndParticipationFactor.getValue();
                        addBusInjection(rhs, lfBus, injection);
                    }
                    addBusInjection(rhs, (LfBus) variableElement, 1d);
                    break;
                case BUS_TARGET_VOLTAGE:
                    Equation v = equationSystem.getEquation(variableElement.getNum(), EquationType.BUS_V).orElseThrow(IllegalStateException::new);
                    if (!v.isActive()) {
                        return;
                    }
                    rhs.set(v.getColumn(), getIndex(), 1d / ((LfBus) variableElement).getNominalV() / PerUnit.SB);
                    break;
                default:
                    throw new NotImplementedException("Variable type " + variableType + " is not implemented");
            }
        }
    }

    static class MultiVariablesFactorGroup extends AbstractSensitivityFactorGroup {

        Map<LfElement, Double> variableElements;
        Map<LfElement, Double> mainComponentWeights;

        MultiVariablesFactorGroup(Map<LfElement, Double> variableElements, SensitivityVariableType variableType) {
            super(variableType);
            this.variableElements = variableElements;
            this.mainComponentWeights = variableElements;
        }

        public Map<LfElement, Double> getVariableElements() {
            return variableElements;
        }

        @Override
        public void fillRhs(EquationSystem equationSystem, Matrix rhs, Map<LfBus, Double> participationByBus) {
            Double weightSum = mainComponentWeights.values().stream().mapToDouble(Math::abs).sum();
            switch (variableType) {
                case INJECTION_ACTIVE_POWER:
                    for (Map.Entry<LfBus, Double> lfBusAndParticipationFactor : participationByBus.entrySet()) {
                        LfBus lfBus = lfBusAndParticipationFactor.getKey();
                        Double injection = lfBusAndParticipationFactor.getValue();
                        addBusInjection(rhs, lfBus, injection);
                    }
                    for (Map.Entry<LfElement, Double> variableElementAndWeight : mainComponentWeights.entrySet()) {
                        LfElement variableElement = variableElementAndWeight.getKey();
                        Double weight = variableElementAndWeight.getValue();
                        addBusInjection(rhs, (LfBus) variableElement, weight / weightSum);
                    }
                    break;
                case HVDC_INJECTION:
                    assert mainComponentWeights.size() <= 2;
                    Double balanceDiff = mainComponentWeights.values().stream().mapToDouble(x -> x).sum();
                    for (Map.Entry<LfBus, Double> lfBusAndParticipationFactor : participationByBus.entrySet()) {
                        LfBus lfBus = lfBusAndParticipationFactor.getKey();
                        Double injection = lfBusAndParticipationFactor.getValue() * balanceDiff; // adapt the sign of the compensation depending on the injection
                        addBusInjection(rhs, lfBus, injection);
                    }
                    // add the injections on the side of the hvdc
                    for (Map.Entry<LfElement, Double> variableElementAndWeight : mainComponentWeights.entrySet()) {
                        LfElement variableElement = variableElementAndWeight.getKey();
                        Double weight = variableElementAndWeight.getValue();
                        addBusInjection(rhs, (LfBus) variableElement, weight);
                    }
            }
        }

        void updateConnectivityWeights(Set<LfBus> nonConnectedBuses) {
            mainComponentWeights = mainComponentWeights.entrySet().stream()
                .filter(entry -> !nonConnectedBuses.contains((LfBus) entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    protected List<SensitivityFactorGroup> createFactorGroups(List<LfSensitivityFactor> factors) {
        Map<Pair<SensitivityVariableType, String>, SensitivityFactorGroup> groupIndexedById = new HashMap<>(factors.size());
        // index factors by variable config
        for (LfSensitivityFactor factor : factors) {
            if (factor.getStatus() == LfSensitivityFactor.Status.SKIP) {
                continue;
            }
            Pair<SensitivityVariableType, String> id = Pair.of(factor.getVariableType(), factor.getVariableId());
            if (factor instanceof SingleVariableLfSensitivityFactor) {
                groupIndexedById.computeIfAbsent(id, bar -> new SingleVariableFactorGroup(((SingleVariableLfSensitivityFactor) factor).getVariableElement(), factor.getVariableType())).addFactor(factor);
            } else if (factor instanceof MultiVariablesLfSensitivityFactor) {
                groupIndexedById.computeIfAbsent(id, bar -> new MultiVariablesFactorGroup(((MultiVariablesLfSensitivityFactor) factor).getWeightedVariableElements(), factor.getVariableType())).addFactor(factor);
            }
        }

        // assign an index to each factor group
        int index = 0;
        for (SensitivityFactorGroup factorGroup : groupIndexedById.values()) {
            factorGroup.setIndex(index++);
        }

        return new ArrayList<>(groupIndexedById.values());
    }

    protected List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses, LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        ActivePowerDistribution.Step step = ActivePowerDistribution.getStep(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant());
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);
        ParticipatingElement.normalizeParticipationFactors(participatingElements, "bus");
        return participatingElements;
    }

    protected DenseMatrix initFactorsRhs(EquationSystem equationSystem, List<SensitivityFactorGroup> factorsGroups, Map<LfBus, Double> participationByBus) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsGroups.size());
        fillRhsSensitivityVariable(equationSystem, factorsGroups, rhs, participationByBus);
        return rhs;
    }

    protected void fillRhsSensitivityVariable(EquationSystem equationSystem, List<SensitivityFactorGroup> factorGroups, Matrix rhs,
                                              Map<LfBus, Double> participationByBus) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            factorGroup.fillRhs(equationSystem, rhs, participationByBus);
        }
    }

    public void cutConnectivity(LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity, Collection<String> breakingConnectivityCandidates) {
        breakingConnectivityCandidates.stream()
            .map(lfNetwork::getBranchById)
            .forEach(lfBranch -> connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2()));
    }

    protected void setPredefinedResults(Collection<LfSensitivityFactor> lfFactors, Set<LfBus> connectedComponent,
                                        GraphDecrementalConnectivity<LfBus> connectivity) {
        for (LfSensitivityFactor factor : lfFactors) {
            // check if the factor function and variable are in different connected components
            if (factor.areVariableAndFunctionDisconnected(connectivity)) {
                factor.setPredefinedResult(0d);
            } else if (!factor.isConnectedToComponent(connectedComponent)) {
                factor.setPredefinedResult(Double.NaN); // works for sensitivity and function reference
            }
        }
    }

    protected void rescaleGlsk(List<SensitivityFactorGroup> factorGroups, Set<LfBus> nonConnectedBuses) {
        // compute the corresponding injection (with participation) for each factor
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            if (!(factorGroup instanceof MultiVariablesFactorGroup)) {
                continue;
            }
            MultiVariablesFactorGroup multiVariablesFactorGroup = (MultiVariablesFactorGroup) factorGroup;
            multiVariablesFactorGroup.updateConnectivityWeights(nonConnectedBuses);
        }
    }

    protected void warnSkippedFactors(Collection<LfSensitivityFactor> lfFactors) {
        List<LfSensitivityFactor> skippedFactors = lfFactors.stream().filter(factor -> factor.getStatus().equals(LfSensitivityFactor.Status.SKIP)).collect(Collectors.toList());
        Set<String> skippedVariables = skippedFactors.stream().map(LfSensitivityFactor::getVariableId).collect(Collectors.toSet());
        if (!skippedVariables.isEmpty()) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Skipping all factors with variables: '{}', as they cannot be found in the network",
                        String.join(", ", skippedVariables));
            }
        }
    }

    public void checkContingencies(Network network, LfNetwork lfNetwork, List<PropagatedContingency> contingencies) {
        for (PropagatedContingency contingency : contingencies) {
            // Elements have already been checked and found in PropagatedContingency, so there is no need to
            // check them again
            Set<String> branchesToRemove = new HashSet<>(); // branches connected to one side, or switches
            for (String branchId : contingency.getBranchIdsToOpen()) {
                LfBranch lfBranch = lfNetwork.getBranchById(branchId);
                if (lfBranch == null) {
                    branchesToRemove.add(branchId); // disconnected branch
                    continue;
                }
                if (lfBranch.getBus2() == null || lfBranch.getBus1() == null) {
                    branchesToRemove.add(branchId); // branch connected only on one side
                }
            }
            contingency.getBranchIdsToOpen().removeAll(branchesToRemove);
            if (contingency.getBranchIdsToOpen().isEmpty() && contingency.getHvdcIdsToOpen().isEmpty()) {
                LOGGER.warn("Contingency {} has no impact", contingency.getContingency().getId());
            }
        }
    }

    public void checkLoadFlowParameters(LoadFlowParameters lfParameters) {
        if (!lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            && !lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)) {
            throw new UnsupportedOperationException("Unsupported balance type mode: " + lfParameters.getBalanceType());
        }
    }

    private static Injection<?> getInjection(Network network, String injectionId) {
        Injection<?> injection = network.getGenerator(injectionId);
        if (injection == null) {
            injection = network.getLoad(injectionId);
        }
        if (injection == null) {
            injection = network.getLccConverterStation(injectionId);
        }
        if (injection == null) {
            injection = network.getVscConverterStation(injectionId);
        }

        if (injection == null) {
            throw new PowsyblException("Injection '" + injectionId + "' not found");
        }

        return injection;
    }

    protected static Bus getInjectionBus(Network network, String injectionId) {
        Injection<?> injection = getInjection(network, injectionId);
        return injection.getTerminal().getBusView().getBus();
    }

    private static void checkBranch(Network network, String branchId) {
        Branch branch = network.getBranch(branchId);
        if (branch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
        }
    }

    private static void checkBus(Network network, String busId) {
        Bus bus = network.getBusView().getBus(busId);
        if (bus == null) {
            throw new PowsyblException("Bus '" + busId + "' not found");
        }
    }

    private static void checkPhaseShifter(Network network, String transformerId) {
        TwoWindingsTransformer twt = network.getTwoWindingsTransformer(transformerId);
        if (twt == null) {
            throw new PowsyblException("Two windings transformer '" + transformerId + "' not found");
        }
        if (twt.getPhaseTapChanger() == null) {
            throw new PowsyblException("Two windings transformer '" + transformerId + "' is not a phase shifter");
        }
    }

    private static void checkRegulatingTerminal(Network network, String equipmentId) {
        Terminal terminal = getEquipmentRegulatingTerminal(network, equipmentId);
        if (terminal == null) {
            throw new PowsyblException("Regulating terminal for '" + equipmentId + "' not found");
        }
    }

    class SensitivityFactorHolder {
        private final Map<String, List<LfSensitivityFactor>> additionalFactorsPerContingency = new HashMap<>();
        private final List<LfSensitivityFactor> additionalFactorsNoContingency = new ArrayList<>();
        private final List<LfSensitivityFactor> commonFactors = new ArrayList<>();

        public List<LfSensitivityFactor> getAllFactors() {
            List<LfSensitivityFactor> allFactors = new ArrayList<>(commonFactors);
            allFactors.addAll(additionalFactorsNoContingency);
            allFactors.addAll(additionalFactorsPerContingency.values().stream().flatMap(List::stream).collect(Collectors.toSet()));
            return allFactors;
        }

        public List<LfSensitivityFactor> getFactorsForContingency(String contingencyId) {
            return Stream.concat(commonFactors.stream(), additionalFactorsPerContingency.getOrDefault(contingencyId, Collections.emptyList()).stream())
                .collect(Collectors.toList());
        }

        public List<LfSensitivityFactor> getFactorsForBaseNetwork() {
            return Stream.concat(commonFactors.stream(), additionalFactorsNoContingency.stream())
                .collect(Collectors.toList());
        }

        public void addFactor(LfSensitivityFactor factor, ContingencyContext contingencyContext) {
            switch (contingencyContext.getContextType()) {
                case ALL:
                    commonFactors.add(factor);
                    break;
                case NONE:
                    additionalFactorsNoContingency.add(factor);
                    break;
                case SPECIFIC:
                    additionalFactorsPerContingency.computeIfAbsent(contingencyContext.getContingencyId(), k -> new ArrayList<>()).add(factor);
                    break;
            }
        }
    }

    public SensitivityFactorHolder readAndCheckFactors(Network network, Map<String, SensitivityVariableSet> variableSetsById,
                                                       SensitivityFactorReader factorReader, LfNetwork lfNetwork) {
        final SensitivityFactorHolder factorHolder = new SensitivityFactorHolder();

        factorReader.read((factorContext, functionType, functionId, variableType, variableId, variableSet, contingencyContext) -> {
            if (variableSet) {
                if (functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER) {
                    checkBranch(network, functionId);
                    LfBranch functionElement = lfNetwork.getBranchById(functionId);
                    if (variableType == SensitivityVariableType.INJECTION_ACTIVE_POWER) {
                        Map<LfElement, Double> injectionLfBuses = new HashMap<>();
                        SensitivityVariableSet set = variableSetsById.get(variableId);
                        if (set == null) {
                            throw new PowsyblException("Variable set '" + variableId + "' not found");
                        }
                        List<String> skippedInjection = new ArrayList<>(set.getVariables().size());
                        for (WeightedSensitivityVariable variable : set.getVariables()) {
                            Bus injectionBus = getInjectionBus(network, variable.getId());
                            LfBus injectionLfBus = injectionBus != null ? lfNetwork.getBusById(injectionBus.getId()) : null;
                            if (injectionLfBus == null) {
                                skippedInjection.add(variable.getId());
                                continue;
                            }
                            injectionLfBuses.put(injectionLfBus, injectionLfBuses.getOrDefault(injectionLfBus, 0d) + variable.getWeight());
                        }
                        if (!skippedInjection.isEmpty()) {
                            if (LOGGER.isWarnEnabled()) {
                                LOGGER.warn("Injections {} cannot be found for glsk {} and will be ignored", String.join(", ", skippedInjection), variableId);
                            }
                        }
                        factorHolder.addFactor(new MultiVariablesLfSensitivityFactor(factorContext, variableId,
                                functionElement, functionType,
                                injectionLfBuses, variableType), contingencyContext);
                    } else if (variableType == SensitivityVariableType.HVDC_INJECTION) {
                        // corresponds to an augmentation of 1 on the active power setpoint
                        Map<LfElement, Double> injectionLfBuses = new HashMap<>();
                        HvdcLine hvdcLine = network.getHvdcLine(variableId);
                        if (hvdcLine == null) {
                            throw new PowsyblException("HVDC line '" + variableId + "' cannot be found in the network.");
                        }
                        LfBus bus1 = lfNetwork.getBusById(hvdcLine.getConverterStation1().getTerminal().getBusView().getBus().getId());
                        LfBus bus2 = lfNetwork.getBusById(hvdcLine.getConverterStation2().getTerminal().getBusView().getBus().getId());

                        if (bus1 != null) {
                            // VSC injection follow here a load sign convention as LCC injection.
                            // FIXME: for LCC, Q changes when P changes
                            injectionLfBuses.put(bus1, (hvdcLine.getConverterStation1() instanceof VscConverterStation ? -1 : 1)
                                    * HvdcConverterStations.getActiveSetpointMultiplier(hvdcLine.getConverterStation1()));
                        }
                        if (bus2 != null) {
                            // VSC injection follow here a load sign convention as LCC injection.
                            // FIXME: for LCC, Q changes when P changes
                            injectionLfBuses.put(bus2, (hvdcLine.getConverterStation2() instanceof VscConverterStation ? -1 : 1)
                                    * HvdcConverterStations.getActiveSetpointMultiplier(hvdcLine.getConverterStation2()));
                        }

                        factorHolder.addFactor(new MultiVariablesLfSensitivityFactor(factorContext, variableId,
                                functionElement, functionType,
                                injectionLfBuses, variableType), contingencyContext);
                    } else {
                        throw new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
                    }
                } else {
                    throw new PowsyblException("Function type " + functionType + " not supported");
                }
            } else {
                LfElement functionElement;
                LfElement variableElement;
                if (functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER) {
                    checkBranch(network, functionId);
                    LfBranch branch = lfNetwork.getBranchById(functionId);
                    functionElement = branch != null && branch.getBus1() != null && branch.getBus2() != null ? branch : null;
                    if (variableType == SensitivityVariableType.INJECTION_ACTIVE_POWER) {
                        Bus injectionBus = getInjectionBus(network, variableId);
                        variableElement = injectionBus != null ? lfNetwork.getBusById(injectionBus.getId()) : null;
                    } else if (variableType == SensitivityVariableType.TRANSFORMER_PHASE) {
                        checkPhaseShifter(network, variableId);
                        variableElement = lfNetwork.getBranchById(variableId);
                    } else {
                        throw new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
                    }
                } else if (functionType == SensitivityFunctionType.BRANCH_CURRENT) {
                    checkBranch(network, functionId);
                    LfBranch branch = lfNetwork.getBranchById(functionId);
                    functionElement = branch != null && branch.getBus1() != null && branch.getBus2() != null ? branch : null;
                    if (variableType == SensitivityVariableType.TRANSFORMER_PHASE) {
                        checkPhaseShifter(network, variableId);
                        variableElement = lfNetwork.getBranchById(variableId);
                    } else {
                        throw new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
                    }
                } else if (functionType == SensitivityFunctionType.BUS_VOLTAGE) {
                    checkBus(network, functionId);
                    functionElement = lfNetwork.getBusById(functionId);
                    if (variableType == SensitivityVariableType.BUS_TARGET_VOLTAGE) {
                        checkRegulatingTerminal(network, variableId);
                        Terminal regulatingTerminal = getEquipmentRegulatingTerminal(network, variableId);
                        assert regulatingTerminal != null; // this cannot fail because it is checked in checkRegulatingTerminal
                        variableElement = lfNetwork.getBusById(regulatingTerminal.getBusView().getBus().getId());
                    } else {
                        throw new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
                    }
                } else {
                    throw new PowsyblException("Function type " + functionType + " not supported");
                }
                factorHolder.addFactor(new SingleVariableLfSensitivityFactor(factorContext, variableId,
                    functionElement, functionType,
                    variableElement, variableType), contingencyContext);
            }
        });
        return factorHolder;
    }
}
