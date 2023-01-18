/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public abstract class AbstractSensitivityAnalysis<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSensitivityAnalysis.class);

    protected final MatrixFactory matrixFactory;

    protected final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected AbstractSensitivityAnalysis(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
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
                return rtc.getRegulationTerminal();
            }
        }
        ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer(equipmentId);
        if (t3wt != null) {
            for (ThreeWindingsTransformer.Leg leg : t3wt.getLegs()) {
                RatioTapChanger rtc = leg.getRatioTapChanger();
                if (rtc != null && rtc.isRegulating()) {
                    return rtc.getRegulationTerminal();
                }
            }
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

    interface LfSensitivityFactor<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        enum Status {
            VALID,
            SKIP,
            VALID_ONLY_FOR_FUNCTION,
            ZERO
        }

        int getIndex();

        String getVariableId();

        SensitivityVariableType getVariableType();

        String getFunctionId();

        LfElement getFunctionElement();

        SensitivityFunctionType getFunctionType();

        ContingencyContext getContingencyContext();

        EquationTerm<V, E> getFunctionEquationTerm();

        Double getSensitivityValuePredefinedResult();

        Double getFunctionPredefinedResult();

        void setSensitivityValuePredefinedResult(Double predefinedResult);

        void setFunctionPredefinedResult(Double predefinedResult);

        double getFunctionReference();

        void setFunctionReference(double functionReference);

        double getBaseSensitivityValue();

        void setBaseCaseSensitivityValue(double baseCaseSensitivityValue);

        Status getStatus();

        void setStatus(Status status);

        boolean isVariableConnectedToSlackComponent(Set<LfBus> lostBuses, Set<LfBranch> lostBranches);

        boolean isFunctionConnectedToSlackComponent(Set<LfBus> lostBuses, Set<LfBranch> lostBranches);

        boolean isVariableInContingency(PropagatedContingency propagatedContingency);

        SensitivityFactorGroup<V, E> getGroup();

        void setGroup(SensitivityFactorGroup<V, E> group);
    }

    abstract static class AbstractLfSensitivityFactor<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements LfSensitivityFactor<V, E> {

        private final int index;

        protected final String variableId;

        private final String functionId;

        protected final LfElement functionElement;

        protected final SensitivityFunctionType functionType;

        protected final SensitivityVariableType variableType;

        protected final ContingencyContext contingencyContext;

        private Double sensitivityValuePredefinedResult = null;

        private Double functionPredefinedResult = null;

        private double functionReference = 0d;

        private double baseCaseSensitivityValue = Double.NaN; // the sensitivity value on pre contingency network, that needs to be recomputed if the stack distribution change

        protected Status status = Status.VALID;

        protected SensitivityFactorGroup<V, E> group;

        protected AbstractLfSensitivityFactor(int index, String variableId, String functionId,
                                           LfElement functionElement, SensitivityFunctionType functionType,
                                           SensitivityVariableType variableType, ContingencyContext contingencyContext) {
            this.index = index;
            this.variableId = Objects.requireNonNull(variableId);
            this.functionId = Objects.requireNonNull(functionId);
            this.functionElement = functionElement;
            this.functionType = Objects.requireNonNull(functionType);
            this.variableType = Objects.requireNonNull(variableType);
            this.contingencyContext = Objects.requireNonNull(contingencyContext);
            if (functionElement == null) {
                status = Status.ZERO;
            }
        }

        @Override
        public int getIndex() {
            return index;
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
        public String getFunctionId() {
            return functionId;
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
        public ContingencyContext getContingencyContext() {
            return contingencyContext;
        }

        @Override
        public EquationTerm<V, E> getFunctionEquationTerm() {
            LfBranch branch;
            switch (functionType) {
                case BRANCH_ACTIVE_POWER:
                case BRANCH_ACTIVE_POWER_1:
                case BRANCH_ACTIVE_POWER_3:
                    return (EquationTerm<V, E>) ((LfBranch) functionElement).getP1();
                case BRANCH_ACTIVE_POWER_2:
                    branch = (LfBranch) functionElement;
                    return branch instanceof LfLegBranch ? (EquationTerm<V, E>) ((LfBranch) functionElement).getP1() : (EquationTerm<V, E>) ((LfBranch) functionElement).getP2();
                case BRANCH_CURRENT:
                case BRANCH_CURRENT_1:
                case BRANCH_CURRENT_3:
                    return (EquationTerm<V, E>) ((LfBranch) functionElement).getI1();
                case BRANCH_CURRENT_2:
                    branch = (LfBranch) functionElement;
                    return branch instanceof LfLegBranch ? (EquationTerm<V, E>) ((LfBranch) functionElement).getI1() : (EquationTerm<V, E>) ((LfBranch) functionElement).getI2();
                case BUS_VOLTAGE:
                    return (EquationTerm<V, E>) ((LfBus) functionElement).getCalculatedV();
                default:
                    throw createFunctionTypeNotSupportedException(functionType);
            }
        }

        @Override
        public Double getSensitivityValuePredefinedResult() {
            return sensitivityValuePredefinedResult;
        }

        @Override
        public Double getFunctionPredefinedResult() {
            return functionPredefinedResult;
        }

        @Override
        public void setSensitivityValuePredefinedResult(Double predefinedResult) {
            this.sensitivityValuePredefinedResult = predefinedResult;
        }

        @Override
        public void setFunctionPredefinedResult(Double predefinedResult) {
            this.functionPredefinedResult = predefinedResult;
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

        protected boolean isElementConnectedToSlackComponent(LfElement element, Set<LfBus> disabledBuses, Set<LfBranch> disabledBranches) {
            if (element instanceof LfBus) {
                return !disabledBuses.contains(element);
            } else if (element instanceof LfBranch) {
                return !disabledBranches.contains(element);
            }
            throw new PowsyblException("Cannot compute connectivity for variable element of class: " + element.getClass().getSimpleName());
        }

        @Override
        public SensitivityFactorGroup<V, E> getGroup() {
            return group;
        }

        @Override
        public void setGroup(SensitivityFactorGroup<V, E> group) {
            this.group = Objects.requireNonNull(group);
        }
    }

    static class SingleVariableLfSensitivityFactor<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractLfSensitivityFactor<V, E> {

        private final LfElement variableElement;

        SingleVariableLfSensitivityFactor(int index, String variableId, String functionId,
                                          LfElement functionElement, SensitivityFunctionType functionType,
                                          LfElement variableElement, SensitivityVariableType variableType,
                                          ContingencyContext contingencyContext) {
            super(index, variableId, functionId, functionElement, functionType, variableType, contingencyContext);
            this.variableElement = variableElement;
            if (variableElement == null) {
                status = functionElement == null ? Status.SKIP : Status.VALID_ONLY_FOR_FUNCTION;
            }
        }

        public LfElement getVariableElement() {
            return variableElement;
        }

        public Equation<V, E> getVariableEquation() {
            switch (variableType) {
                case TRANSFORMER_PHASE:
                case TRANSFORMER_PHASE_1:
                case TRANSFORMER_PHASE_2:
                case TRANSFORMER_PHASE_3:
                    LfBranch lfBranch = (LfBranch) variableElement;
                    return ((EquationTerm<V, E>) lfBranch.getA1()).getEquation();
                case BUS_TARGET_VOLTAGE:
                    LfBus lfBus = (LfBus) variableElement;
                    return ((EquationTerm<V, E>) lfBus.getCalculatedV()).getEquation();
                default:
                    return null;
            }
        }

        @Override
        public boolean isVariableConnectedToSlackComponent(Set<LfBus> disabledBuses, Set<LfBranch> disabledBranches) {
            return isElementConnectedToSlackComponent(variableElement, disabledBuses, disabledBranches);
        }

        @Override
        public boolean isFunctionConnectedToSlackComponent(Set<LfBus> disabledBuses, Set<LfBranch> disabledBranches) {
            return isElementConnectedToSlackComponent(functionElement, disabledBuses, disabledBranches);
        }

        @Override
        public boolean isVariableInContingency(PropagatedContingency contingency) {
            if (contingency != null) {
                switch (variableType) {
                    case INJECTION_ACTIVE_POWER:
                    case HVDC_LINE_ACTIVE_POWER:
                        // a load, a generator, a dangling line, an LCC or a VSC converter station.
                        return contingency.getGeneratorIdsToLose().contains(variableId) || contingency.getOriginalPowerShiftIds().contains(variableId);
                    case BUS_TARGET_VOLTAGE:
                        // a generator or a two windings transformer.
                        // shunt contingency not supported yet.
                        // ratio tap changer in a three windings transformer not supported yet.
                        return contingency.getGeneratorIdsToLose().contains(variableId) || contingency.getBranchIdsToOpen().contains(variableId);
                    case TRANSFORMER_PHASE:
                        // a phase shifter on a two windings transformer.
                        return contingency.getBranchIdsToOpen().contains(variableId);
                    default:
                        return false;
                }
            } else {
                return false;
            }
        }
    }

    static class MultiVariablesLfSensitivityFactor<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractLfSensitivityFactor<V, E> {

        private final Map<LfElement, Double> weightedVariableElements;

        private final Set<String> originalVariableSetIds;

        MultiVariablesLfSensitivityFactor(int index, String variableId, String functionId,
                                          LfElement functionElement, SensitivityFunctionType functionType,
                                          Map<LfElement, Double> weightedVariableElements, SensitivityVariableType variableType,
                                          ContingencyContext contingencyContext, Set<String> originalVariableSetIds) {
            super(index, variableId, functionId, functionElement, functionType, variableType, contingencyContext);
            this.weightedVariableElements = weightedVariableElements;
            if (weightedVariableElements.isEmpty()) {
                status = functionElement == null ? Status.SKIP : Status.VALID_ONLY_FOR_FUNCTION;
            }
            this.originalVariableSetIds = originalVariableSetIds;
        }

        public Map<LfElement, Double> getWeightedVariableElements() {
            return weightedVariableElements;
        }

        public Collection<LfElement> getVariableElements() {
            return weightedVariableElements.keySet();
        }

        @Override
        public boolean isVariableConnectedToSlackComponent(Set<LfBus> disabledBuses, Set<LfBranch> disabledBranches) {
            for (LfElement lfElement : getVariableElements()) {
                if (isElementConnectedToSlackComponent(lfElement, disabledBuses, disabledBranches)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isFunctionConnectedToSlackComponent(Set<LfBus> disabledBuses, Set<LfBranch> disabledBranches) {
            return isElementConnectedToSlackComponent(functionElement, disabledBuses, disabledBranches);
        }

        @Override
        public boolean isVariableInContingency(PropagatedContingency contingency) {
            if (contingency != null) {
                int sizeCommonIds = (int) Stream.concat(contingency.getGeneratorIdsToLose().stream(), contingency.getOriginalPowerShiftIds().stream())
                        .distinct()
                        .filter(originalVariableSetIds::contains)
                        .count();
                return sizeCommonIds == originalVariableSetIds.size();
            } else {
                return false;
            }
        }
    }

    interface SensitivityFactorGroup<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        List<LfSensitivityFactor<V, E>> getFactors();

        int getIndex();

        void setIndex(int index);

        void addFactor(LfSensitivityFactor<V, E> factor);

        void fillRhs(Matrix rhs, Map<LfBus, Double> participationByBus);
    }

    abstract static class AbstractSensitivityFactorGroup<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements SensitivityFactorGroup<V, E> {

        protected final List<LfSensitivityFactor<V, E>> factors = new ArrayList<>();

        protected final SensitivityVariableType variableType;

        private int index = -1;

        AbstractSensitivityFactorGroup(SensitivityVariableType variableType) {
            this.variableType = variableType;
        }

        @Override
        public List<LfSensitivityFactor<V, E>> getFactors() {
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
        public void addFactor(LfSensitivityFactor<V, E> factor) {
            factors.add(factor);
        }

        protected void addBusInjection(Matrix rhs, LfBus lfBus, double injection) {
            Equation<V, E> p = (Equation<V, E>) lfBus.getP();
            if (lfBus.isSlack() || !p.isActive()) {
                return;
            }
            int column = p.getColumn();
            rhs.add(column, getIndex(), injection);
        }
    }

    private static NotImplementedException createVariableTypeNotImplementedException(SensitivityVariableType variableType) {
        return new NotImplementedException("Variable type " + variableType + " is not implemented");
    }

    static class SingleVariableFactorGroup<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractSensitivityFactorGroup<V, E> {

        private final LfElement variableElement;

        private final Equation<V, E> variableEquation;

        SingleVariableFactorGroup(LfElement variableElement, Equation<V, E> variableEquation, SensitivityVariableType variableType) {
            super(variableType);
            this.variableElement = Objects.requireNonNull(variableElement);
            this.variableEquation = variableEquation;
        }

        @Override
        public void fillRhs(Matrix rhs, Map<LfBus, Double> participationByBus) {
            switch (variableType) {
                case TRANSFORMER_PHASE:
                case TRANSFORMER_PHASE_1:
                case TRANSFORMER_PHASE_2:
                case TRANSFORMER_PHASE_3:
                    if (variableEquation.isActive()) {
                        rhs.set(variableEquation.getColumn(), getIndex(), Math.toRadians(1d));
                    }
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
                    if (variableEquation.isActive()) {
                        rhs.set(variableEquation.getColumn(), getIndex(), 1d);
                    }
                    break;
                default:
                    throw createVariableTypeNotImplementedException(variableType);
            }
        }
    }

    static class MultiVariablesFactorGroup<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractSensitivityFactorGroup<V, E> {

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
        public void fillRhs(Matrix rhs, Map<LfBus, Double> participationByBus) {
            double weightSum = mainComponentWeights.values().stream().mapToDouble(Math::abs).sum();
            switch (variableType) {
                case INJECTION_ACTIVE_POWER:
                    for (Map.Entry<LfBus, Double> lfBusAndParticipationFactor : participationByBus.entrySet()) {
                        LfBus lfBus = lfBusAndParticipationFactor.getKey();
                        double injection = lfBusAndParticipationFactor.getValue();
                        addBusInjection(rhs, lfBus, injection);
                    }
                    for (Map.Entry<LfElement, Double> variableElementAndWeight : mainComponentWeights.entrySet()) {
                        LfElement variableElement = variableElementAndWeight.getKey();
                        double weight = variableElementAndWeight.getValue();
                        addBusInjection(rhs, (LfBus) variableElement, weight / weightSum);
                    }
                    break;
                case HVDC_LINE_ACTIVE_POWER:
                    assert mainComponentWeights.size() <= 2;
                    double balanceDiff = mainComponentWeights.values().stream().mapToDouble(x -> x).sum();
                    for (Map.Entry<LfBus, Double> lfBusAndParticipationFactor : participationByBus.entrySet()) {
                        LfBus lfBus = lfBusAndParticipationFactor.getKey();
                        double injection = lfBusAndParticipationFactor.getValue() * balanceDiff; // adapt the sign of the compensation depending on the injection
                        addBusInjection(rhs, lfBus, injection);
                    }
                    // add the injections on the side of the hvdc
                    for (Map.Entry<LfElement, Double> variableElementAndWeight : mainComponentWeights.entrySet()) {
                        LfElement variableElement = variableElementAndWeight.getKey();
                        double weight = variableElementAndWeight.getValue();
                        addBusInjection(rhs, (LfBus) variableElement, weight);
                    }
                    break;
                default:
                    throw createVariableTypeNotImplementedException(variableType);
            }
        }

        boolean updateConnectivityWeights(Set<LfBus> nonConnectedBuses) {
            mainComponentWeights = variableElements.entrySet().stream()
                .filter(entry -> !nonConnectedBuses.contains((LfBus) entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return mainComponentWeights.size() != variableElements.size();
        }
    }

    protected static class SensitivityFactorGroupList<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        private final List<SensitivityFactorGroup<V, E>> list;

        private final boolean multiVariables;

        public SensitivityFactorGroupList(List<SensitivityFactorGroup<V, E>> list) {
            this.list = Objects.requireNonNull(list);
            multiVariables = list.stream().anyMatch(MultiVariablesFactorGroup.class::isInstance);
        }

        public List<SensitivityFactorGroup<V, E>> getList() {
            return list;
        }

        public boolean hasMultiVariables() {
            return multiVariables;
        }
    }

    protected SensitivityFactorGroupList<V, E> createFactorGroups(List<LfSensitivityFactor<V, E>> factors) {
        Map<Pair<SensitivityVariableType, String>, SensitivityFactorGroup<V, E>> groupIndexedById = new LinkedHashMap<>(factors.size());
        // index factors by variable config
        for (LfSensitivityFactor<V, E> factor : factors) {
            Pair<SensitivityVariableType, String> id = Pair.of(factor.getVariableType(), factor.getVariableId());
            if (factor instanceof SingleVariableLfSensitivityFactor) {
                SingleVariableLfSensitivityFactor<V, E> singleVarFactor = (SingleVariableLfSensitivityFactor<V, E>) factor;
                SensitivityFactorGroup<V, E> factorGroup = groupIndexedById.computeIfAbsent(id, k -> new SingleVariableFactorGroup<>(singleVarFactor.getVariableElement(), singleVarFactor.getVariableEquation(), factor.getVariableType()));
                factorGroup.addFactor(factor);
                factor.setGroup(factorGroup);
            } else if (factor instanceof MultiVariablesLfSensitivityFactor) {
                SensitivityFactorGroup<V, E> factorGroup = groupIndexedById.computeIfAbsent(id, k -> new MultiVariablesFactorGroup<>(((MultiVariablesLfSensitivityFactor<V, E>) factor).getWeightedVariableElements(), factor.getVariableType()));
                factorGroup.addFactor(factor);
                factor.setGroup(factorGroup);
            }
        }

        // assign an index to each factor group
        int index = 0;
        for (SensitivityFactorGroup<V, E> factorGroup : groupIndexedById.values()) {
            factorGroup.setIndex(index++);
        }

        return new SensitivityFactorGroupList<>(new ArrayList<>(groupIndexedById.values()));
    }

    protected List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType, OpenLoadFlowParameters openLoadFlowParameters) {
        ActivePowerDistribution.Step step = ActivePowerDistribution.getStep(balanceType, openLoadFlowParameters.isLoadPowerFactorConstant());
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);
        ParticipatingElement.normalizeParticipationFactors(participatingElements, "bus");
        return participatingElements;
    }

    protected DenseMatrix initFactorsRhs(EquationSystem<V, E> equationSystem, SensitivityFactorGroupList<V, E> factorsGroups, Map<LfBus, Double> participationByBus) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), factorsGroups.getList().size());
        fillRhsSensitivityVariable(factorsGroups, rhs, participationByBus);
        return rhs;
    }

    protected void fillRhsSensitivityVariable(SensitivityFactorGroupList<V, E> factorGroups, Matrix rhs, Map<LfBus, Double> participationByBus) {
        for (SensitivityFactorGroup<V, E> factorGroup : factorGroups.getList()) {
            factorGroup.fillRhs(rhs, participationByBus);
        }
    }

    protected void setPredefinedResults(Collection<LfSensitivityFactor<V, E>> lfFactors, Set<LfBus> disabledBuses,
                                        Set<LfBranch> disabledBranches, PropagatedContingency propagatedContingency) {
        for (LfSensitivityFactor<V, E> factor : lfFactors) {
            Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, disabledBuses, disabledBranches, propagatedContingency);
            predefinedResults.getLeft().ifPresent(factor::setSensitivityValuePredefinedResult);
            predefinedResults.getRight().ifPresent(factor::setFunctionPredefinedResult);
        }
    }

    protected Pair<Optional<Double>, Optional<Double>> getPredefinedResults(LfSensitivityFactor<V, E> factor, Set<LfBus> disabledBuses,
                                                                            Set<LfBranch> disabledBranches, PropagatedContingency propagatedContingency) {
        Double sensitivityValuePredefinedResult = null;
        Double functionPredefinedResult = null;
        if (factor.getStatus() == LfSensitivityFactor.Status.VALID) {
            // after a contingency, we check if the factor function and the variable are in different connected components
            // or if the variable is in contingency. Note that a branch in contingency is considered as not connected to the slack component.
            boolean variableConnected = factor.isVariableConnectedToSlackComponent(disabledBuses, disabledBranches) && !factor.isVariableInContingency(propagatedContingency);
            boolean functionConnectedToSlackComponent = factor.isFunctionConnectedToSlackComponent(disabledBuses, disabledBranches);
            if (variableConnected) {
                if (!functionConnectedToSlackComponent) {
                    // ZERO status
                    sensitivityValuePredefinedResult = 0d;
                    functionPredefinedResult = Double.NaN;
                }
            } else {
                if (functionConnectedToSlackComponent) {
                    // VALID_ONLY_FOR_FUNCTION status
                    sensitivityValuePredefinedResult = 0d;
                } else {
                    // SKIP status
                    sensitivityValuePredefinedResult = Double.NaN;
                    functionPredefinedResult = Double.NaN;
                }
            }
        } else if (factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION) {
            sensitivityValuePredefinedResult = 0d;
            if (!factor.isFunctionConnectedToSlackComponent(disabledBuses, disabledBranches)) {
                functionPredefinedResult = Double.NaN;
            }
        } else {
            throw new IllegalStateException("Unexpected factor status: " + factor.getStatus());
        }
        return Pair.of(Optional.ofNullable(sensitivityValuePredefinedResult), Optional.ofNullable(functionPredefinedResult));
    }

    protected boolean rescaleGlsk(SensitivityFactorGroupList<V, E> factorGroups, Set<LfBus> nonConnectedBuses) {
        boolean rescaled = false;
        // compute the corresponding injection (with participation) for each factor
        for (SensitivityFactorGroup<V, E> factorGroup : factorGroups.getList()) {
            if (factorGroup instanceof MultiVariablesFactorGroup) {
                MultiVariablesFactorGroup<V, E> multiVariablesFactorGroup = (MultiVariablesFactorGroup<V, E>) factorGroup;
                rescaled |= multiVariablesFactorGroup.updateConnectivityWeights(nonConnectedBuses);
            }
        }
        return rescaled;
    }

    /**
     * Write zero or skip factors to output and send a new factor holder containing only other valid ones.
     * IMPORTANT: this is only a base case test (factor status only deal with base case). We do not output anything
     * on post contingency if factor is already invalid (skip o zero) on base case.
     */
    protected SensitivityFactorHolder<V, E> writeInvalidFactors(SensitivityFactorHolder<V, E> factorHolder, SensitivityResultWriter resultWriter) {
        Set<String> skippedVariables = new LinkedHashSet<>();
        SensitivityFactorHolder<V, E> validFactorHolder = new SensitivityFactorHolder<>();
        for (var factor : factorHolder.getAllFactors()) {
            // directly write output for zero and invalid factors
            if (factor.getStatus() == LfSensitivityFactor.Status.ZERO) {
                // ZERO status is for factors where variable element is in the main connected component and reference element is not.
                // Therefore, the sensitivity is known to value 0, but the reference cannot be known and is set to NaN.
                resultWriter.writeSensitivityValue(factor.getIndex(), -1, 0, Double.NaN);
            } else if (factor.getStatus() == LfSensitivityFactor.Status.SKIP) {
                resultWriter.writeSensitivityValue(factor.getIndex(), -1, Double.NaN, Double.NaN);
                skippedVariables.add(factor.getVariableId());
            } else {
                validFactorHolder.addFactor(factor);
            }
        }
        if (!skippedVariables.isEmpty() && LOGGER.isWarnEnabled()) {
            LOGGER.warn("Skipping all factors with variables: '{}', as they cannot be found in the network",
                    String.join(", ", skippedVariables));
        }
        return validFactorHolder;
    }

    private static void cleanBranchIdsToOpen(LfNetwork lfNetwork, PropagatedContingency contingency) {
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
    }

    public void checkContingencies(LfNetwork lfNetwork, List<PropagatedContingency> contingencies) {
        Set<String> contingenciesIds = new HashSet<>();
        for (PropagatedContingency contingency : contingencies) {
            // check ID are unique because, later contingency are indexed by their IDs
            String contingencyId = contingency.getContingency().getId();
            if (contingenciesIds.contains(contingencyId)) {
                throw new PowsyblException("Contingency '" + contingencyId + "' already exists");
            }
            contingenciesIds.add(contingencyId);

            cleanBranchIdsToOpen(lfNetwork, contingency);

            if (contingency.getBranchIdsToOpen().isEmpty()
                    && contingency.getHvdcIdsToOpen().isEmpty()
                    && contingency.getGeneratorIdsToLose().isEmpty()
                    && contingency.getBusIdsToShift().isEmpty()) {
                LOGGER.warn("Contingency '{}' has no impact", contingency.getContingency().getId());
            }
        }
    }

    public void checkLoadFlowParameters(LoadFlowParameters lfParameters) {
        if (!lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                && !lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
                && !lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P)) {
            throw new UnsupportedOperationException("Unsupported balance type mode: " + lfParameters.getBalanceType());
        }
    }

    private static Injection<?> getInjection(Network network, String injectionId) {
        Injection<?> injection = network.getGenerator(injectionId);
        if (injection == null) {
            injection = network.getLoad(injectionId);
        }
        if (injection == null) {
            injection = network.getDanglingLine(injectionId);
        }
        if (injection == null) {
            injection = network.getLccConverterStation(injectionId);
        }
        if (injection == null) {
            injection = network.getVscConverterStation(injectionId);
        }
        return injection;
    }

    protected static String getInjectionBusId(Network network, String injectionId, boolean breakers) {
        // try with an injection
        Injection<?> injection = getInjection(network, injectionId);
        if (injection != null) {
            Bus bus = breakers ? injection.getTerminal().getBusBreakerView().getBus() : injection.getTerminal().getBusView().getBus();
            if (bus == null) {
                return null;
            }
            if (injection instanceof DanglingLine) {
                return LfDanglingLineBus.getId((DanglingLine) injection);
            } else {
                return bus.getId();
            }
        }

        // try with a configured bus
        Bus configuredBus = network.getBusBreakerView().getBus(injectionId);
        if (configuredBus != null) {
            // find a bus from bus view corresponding to this configured bus
            List<Terminal> terminals = new ArrayList<>();
            configuredBus.visitConnectedEquipments(new AbstractTerminalTopologyVisitor() {
                @Override
                public void visitTerminal(Terminal terminal) {
                    terminals.add(terminal);
                }
            });
            for (Terminal terminal : terminals) {
                Bus bus = breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
                if (bus != null) {
                    return bus.getId();
                }
            }
            return null;
        }

        // try with a busbar section
        BusbarSection busbarSection = network.getBusbarSection(injectionId);
        if (busbarSection != null) {
            Bus bus = breakers ? busbarSection.getTerminal().getBusBreakerView().getBus() : busbarSection.getTerminal().getBusView().getBus();
            if (bus == null) {
                return null;
            }
            return bus.getId();
        }

        throw new PowsyblException("Injection '" + injectionId + "' not found");
    }

    private static LfBranch checkAndGetBranchOrLeg(Network network, String branchId, SensitivityFunctionType fType, LfNetwork lfNetwork) {
        Branch<?> branch = network.getBranch(branchId);
        if (branch != null) {
            return lfNetwork.getBranchById(branchId);
        }
        DanglingLine danglingLine = network.getDanglingLine(branchId);
        if (danglingLine != null) {
            return lfNetwork.getBranchById(branchId);
        }
        ThreeWindingsTransformer twt = network.getThreeWindingsTransformer(branchId);
        if (twt != null) {
            return lfNetwork.getBranchById(LfLegBranch.getId(branchId, getLegNumber(fType)));
        }
        throw new PowsyblException("Branch, dangling line or leg of '" + branchId + "' not found");
    }

    private static void checkBus(Network network, String busId, Map<String, Bus> busCache, boolean breakers) {
        if (busCache.isEmpty()) {
            Networks.getBuses(network, breakers).forEach(bus -> busCache.put(bus.getId(), bus));
        }
        Bus bus = busCache.get(busId);
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

    private static void checkThreeWindingsTransformerPhaseShifter(Network network, String transformerId, SensitivityVariableType type) {
        ThreeWindingsTransformer twt = network.getThreeWindingsTransformer(transformerId);
        if (twt == null) {
            throw new PowsyblException("Three windings transformer '" + transformerId + "' not found");
        }
        ThreeWindingsTransformer.Leg l;
        switch (type) {
            case TRANSFORMER_PHASE_1:
                l = twt.getLeg1();
                break;
            case TRANSFORMER_PHASE_2:
                l = twt.getLeg2();
                break;
            case TRANSFORMER_PHASE_3:
                l = twt.getLeg3();
                break;
            default:
                throw new PowsyblException("Three transformer variable type " + type + " cannot be converted to a leg");
        }
        if (l.getPhaseTapChanger() == null) {
            throw new PowsyblException("Three windings transformer '" + transformerId + "' leg on side '" + type + "' has no phase tap changer");
        }
    }

    private static void checkRegulatingTerminal(Network network, String equipmentId) {
        Terminal terminal = getEquipmentRegulatingTerminal(network, equipmentId);
        if (terminal == null) {
            throw new PowsyblException("Regulating terminal for '" + equipmentId + "' not found");
        }
    }

    static class SensitivityFactorHolder<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

        private final Map<String, List<LfSensitivityFactor<V, E>>> additionalFactorsPerContingency = new LinkedHashMap<>();
        private final List<LfSensitivityFactor<V, E>> additionalFactorsNoContingency = new ArrayList<>();
        private final List<LfSensitivityFactor<V, E>> commonFactors = new ArrayList<>();

        public List<LfSensitivityFactor<V, E>> getAllFactors() {
            List<LfSensitivityFactor<V, E>> allFactors = new ArrayList<>(commonFactors);
            allFactors.addAll(additionalFactorsNoContingency);
            allFactors.addAll(additionalFactorsPerContingency.values().stream().flatMap(List::stream).collect(Collectors.toCollection(LinkedHashSet::new)));
            return allFactors;
        }

        public List<LfSensitivityFactor<V, E>> getFactorsForContingency(String contingencyId) {
            return Stream.concat(commonFactors.stream(), additionalFactorsPerContingency.getOrDefault(contingencyId, Collections.emptyList()).stream())
                .collect(Collectors.toList());
        }

        public List<LfSensitivityFactor<V, E>> getFactorsForContingencies(List<String> contingenciesIds) {
            return Stream.concat(commonFactors.stream(),
                                 contingenciesIds.stream().flatMap(contingencyId -> additionalFactorsPerContingency.getOrDefault(contingencyId, Collections.emptyList()).stream()))
                    .collect(Collectors.toList());
        }

        public List<LfSensitivityFactor<V, E>> getFactorsForBaseNetwork() {
            return Stream.concat(commonFactors.stream(), additionalFactorsNoContingency.stream())
                .collect(Collectors.toList());
        }

        public void addFactor(LfSensitivityFactor<V, E> factor) {
            switch (factor.getContingencyContext().getContextType()) {
                case ALL:
                    commonFactors.add(factor);
                    break;
                case NONE:
                    additionalFactorsNoContingency.add(factor);
                    break;
                case SPECIFIC:
                    additionalFactorsPerContingency.computeIfAbsent(factor.getContingencyContext().getContingencyId(), k -> new ArrayList<>()).add(factor);
                    break;
            }
        }
    }

    private static PowsyblException createFunctionTypeNotSupportedException(SensitivityFunctionType functionType) {
        return new PowsyblException("Function type " + functionType + " not supported");
    }

    private static PowsyblException createVariableTypeNotSupportedWithFunctionTypeException(SensitivityVariableType variableType, SensitivityFunctionType functionType) {
        return new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
    }

    public SensitivityFactorHolder<V, E> readAndCheckFactors(Network network, Map<String, SensitivityVariableSet> variableSetsById,
                                                             SensitivityFactorReader factorReader, LfNetwork lfNetwork, boolean breakers) {
        final SensitivityFactorHolder<V, E> factorHolder = new SensitivityFactorHolder<>();

        final Map<String, Map<LfElement, Double>> injectionBusesByVariableId = new LinkedHashMap<>();
        final Map<String, Set<String>> originalVariableSetIdsByVariableId = new LinkedHashMap<>();
        final Map<String, Bus> busCache = new HashMap<>();
        int[] factorIndex = new int[1];
        factorReader.read((functionType, functionId, variableType, variableId, variableSet, contingencyContext) -> {
            if (variableSet) {
                if (functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER
                    || functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                    || functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_2
                    || functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_3) {
                    LfBranch branch = checkAndGetBranchOrLeg(network, functionId, functionType, lfNetwork);
                    LfElement functionElement = branch != null && branch.getBus1() != null && branch.getBus2() != null ? branch : null;
                    if (variableType == SensitivityVariableType.INJECTION_ACTIVE_POWER) {
                        Map<LfElement, Double> injectionLfBuses = injectionBusesByVariableId.get(variableId);
                        Set<String> originalVariableSetIds = originalVariableSetIdsByVariableId.get(variableId);
                        if (injectionLfBuses == null && originalVariableSetIds == null) {
                            injectionLfBuses = new LinkedHashMap<>();
                            originalVariableSetIds = new HashSet<>();
                            injectionBusesByVariableId.put(variableId, injectionLfBuses);
                            originalVariableSetIdsByVariableId.put(variableId, originalVariableSetIds);
                            SensitivityVariableSet set = variableSetsById.get(variableId);
                            if (set == null) {
                                throw new PowsyblException("Variable set '" + variableId + "' not found");
                            }
                            List<String> skippedInjection = new ArrayList<>(set.getVariables().size());
                            for (WeightedSensitivityVariable variable : set.getVariables()) {
                                String injectionBusId = getInjectionBusId(network, variable.getId(), breakers);
                                LfBus injectionLfBus = injectionBusId != null ? lfNetwork.getBusById(injectionBusId) : null;
                                if (injectionLfBus == null) {
                                    skippedInjection.add(variable.getId());
                                    continue;
                                }
                                injectionLfBuses.put(injectionLfBus, injectionLfBuses.getOrDefault(injectionLfBus, 0d) + variable.getWeight());
                                originalVariableSetIds.add(variable.getId());
                            }
                            if (!skippedInjection.isEmpty() && LOGGER.isWarnEnabled()) {
                                LOGGER.warn("Injections {} cannot be found for glsk {} and will be ignored", String.join(", ", skippedInjection), variableId);
                            }
                        }
                        factorHolder.addFactor(new MultiVariablesLfSensitivityFactor<>(factorIndex[0], variableId,
                                    functionId, functionElement, functionType,
                                    injectionLfBuses, variableType, contingencyContext, originalVariableSetIds));
                    } else {
                        throw createVariableTypeNotSupportedWithFunctionTypeException(variableType, functionType);
                    }
                } else {
                    throw createFunctionTypeNotSupportedException(functionType);
                }
            } else {
                if ((functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER ||
                      functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 ||
                      functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_2 ||
                      functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_3)
                     && variableType == SensitivityVariableType.HVDC_LINE_ACTIVE_POWER) {
                    LfBranch branch = checkAndGetBranchOrLeg(network, functionId, functionType, lfNetwork);
                    LfElement functionElement = branch != null && branch.getBus1() != null && branch.getBus2() != null ? branch : null;

                    HvdcLine hvdcLine = network.getHvdcLine(variableId);
                    if (hvdcLine == null) {
                        throw new PowsyblException("HVDC line '" + variableId + "' cannot be found in the network.");
                    }
                    LfBus bus1 = lfNetwork.getBusById(breakers ? hvdcLine.getConverterStation1().getTerminal().getBusBreakerView().getBus().getId() :
                            hvdcLine.getConverterStation1().getTerminal().getBusView().getBus().getId());
                    LfBus bus2 = lfNetwork.getBusById(breakers ? hvdcLine.getConverterStation2().getTerminal().getBusBreakerView().getBus().getId() :
                            hvdcLine.getConverterStation2().getTerminal().getBusView().getBus().getId());

                    // corresponds to an augmentation of +1 on the active power setpoint on each side on the HVDC line
                    // => we create a multi (bi) variables factor
                    Map<LfElement, Double> injectionLfBuses = new HashMap<>(2);
                    Set<String> originalVariableSetIds = new HashSet<>(2);
                    if (bus1 != null) {
                        // FIXME: for LCC, Q changes when P changes
                        injectionLfBuses.put(bus1, HvdcConverterStations.getActivePowerSetpointMultiplier(hvdcLine.getConverterStation1()));
                        originalVariableSetIds.add(hvdcLine.getConverterStation1().getId());
                    }
                    if (bus2 != null) {
                        // FIXME: for LCC, Q changes when P changes
                        injectionLfBuses.put(bus2, HvdcConverterStations.getActivePowerSetpointMultiplier(hvdcLine.getConverterStation2()));
                        originalVariableSetIds.add(hvdcLine.getConverterStation2().getId());
                    }

                    factorHolder.addFactor(new MultiVariablesLfSensitivityFactor<>(factorIndex[0], variableId,
                            functionId, functionElement, functionType, injectionLfBuses, variableType, contingencyContext, originalVariableSetIds));
                } else {
                    LfElement functionElement;
                    LfElement variableElement;
                    if (functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER
                        || functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                        || functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_2
                        || functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER_3
                        || functionType == SensitivityFunctionType.BRANCH_CURRENT
                        || functionType == SensitivityFunctionType.BRANCH_CURRENT_1
                        || functionType == SensitivityFunctionType.BRANCH_CURRENT_2
                        || functionType == SensitivityFunctionType.BRANCH_CURRENT_3) {
                        LfBranch branch = checkAndGetBranchOrLeg(network, functionId, functionType, lfNetwork);
                        functionElement = branch != null && branch.getBus1() != null && branch.getBus2() != null ? branch : null;
                        switch (variableType) {
                            case INJECTION_ACTIVE_POWER:
                                String injectionBusId = getInjectionBusId(network, variableId, breakers);
                                variableElement = injectionBusId != null ? lfNetwork.getBusById(injectionBusId) : null;
                                break;
                            case TRANSFORMER_PHASE:
                                checkPhaseShifter(network, variableId);
                                LfBranch twt = lfNetwork.getBranchById(variableId);
                                variableElement = twt != null && twt.getBus1() != null && twt.getBus2() != null ? twt : null;
                                break;
                            case TRANSFORMER_PHASE_1:
                            case TRANSFORMER_PHASE_2:
                            case TRANSFORMER_PHASE_3:
                                checkThreeWindingsTransformerPhaseShifter(network, variableId, variableType);
                                LfBranch leg = lfNetwork.getBranchById(LfLegBranch.getId(variableId, getLegNumber(variableType)));
                                variableElement = leg != null && leg.getBus1() != null && leg.getBus2() != null ? leg : null;
                                break;
                            default:
                                throw createVariableTypeNotSupportedWithFunctionTypeException(variableType, functionType);
                        }
                    } else if (functionType == SensitivityFunctionType.BUS_VOLTAGE) {
                        checkBus(network, functionId, busCache, breakers);
                        functionElement = lfNetwork.getBusById(functionId);
                        if (variableType == SensitivityVariableType.BUS_TARGET_VOLTAGE) {
                            checkRegulatingTerminal(network, variableId);
                            Terminal regulatingTerminal = getEquipmentRegulatingTerminal(network, variableId);
                            assert regulatingTerminal != null; // this cannot fail because it is checked in checkRegulatingTerminal
                            Bus regulatedBus = breakers ? regulatingTerminal.getBusBreakerView().getBus() : regulatingTerminal.getBusView().getBus();
                            variableElement = regulatedBus != null ? lfNetwork.getBusById(regulatedBus.getId()) : null;
                        } else {
                            throw createVariableTypeNotSupportedWithFunctionTypeException(variableType, functionType);
                        }
                    } else {
                        throw createFunctionTypeNotSupportedException(functionType);
                    }
                    factorHolder.addFactor(new SingleVariableLfSensitivityFactor<>(factorIndex[0], variableId,
                            functionId, functionElement, functionType, variableElement, variableType, contingencyContext));
                }
            }
            factorIndex[0]++;
        });
        return factorHolder;
    }

    public Pair<Boolean, Boolean> hasBusTargetVoltage(SensitivityFactorReader factorReader, Network network) {
        // Left value if we find a BUS_TARGET_VOLTAGE factor and right value if it is linked to a transformer.
        AtomicBoolean hasBusTargetVoltage = new AtomicBoolean(false);
        AtomicBoolean hasTransformerBusTargetVoltage = new AtomicBoolean(false);
        factorReader.read((functionType, functionId, variableType, variableId, variableSet, contingencyContext) -> {
            if (variableType == SensitivityVariableType.BUS_TARGET_VOLTAGE) {
                hasBusTargetVoltage.set(true);
                Identifiable<?> equipment = network.getIdentifiable(variableId);
                if (equipment instanceof TwoWindingsTransformer || equipment instanceof ThreeWindingsTransformer) {
                    hasTransformerBusTargetVoltage.set(true);
                }
            }
        });
        return Pair.of(hasBusTargetVoltage.get(), hasTransformerBusTargetVoltage.get());
    }

    public static boolean isDistributedSlackOnGenerators(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
    }

    public static boolean isDistributedSlackOnLoads(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
    }

    /**
     * Base value for per-uniting, depending on the function type
     */
    private static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double getFunctionBaseValue(LfSensitivityFactor<V, E> factor) {
        switch (factor.getFunctionType()) {
            case BRANCH_ACTIVE_POWER:
            case BRANCH_ACTIVE_POWER_1:
            case BRANCH_ACTIVE_POWER_2:
            case BRANCH_ACTIVE_POWER_3:
                return PerUnit.SB;
            case BRANCH_CURRENT:
            case BRANCH_CURRENT_1:
            case BRANCH_CURRENT_3:
                LfBranch branch = (LfBranch) factor.getFunctionElement();
                return PerUnit.ib(branch.getBus1().getNominalV());
            case BRANCH_CURRENT_2:
                LfBranch branch2 = (LfBranch) factor.getFunctionElement();
                return branch2 instanceof LfLegBranch ? PerUnit.ib(branch2.getBus1().getNominalV()) : PerUnit.ib(branch2.getBus2().getNominalV());
            case BUS_VOLTAGE:
                LfBus bus = (LfBus) factor.getFunctionElement();
                return bus.getNominalV();
            default:
                throw new IllegalArgumentException("Unknown function type " + factor.getFunctionType());
        }
    }

    /**
     * Base value for per-uniting, depending on the variable type
     */
    private static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double getVariableBaseValue(LfSensitivityFactor<V, E> factor) {
        switch (factor.getVariableType()) {
            case HVDC_LINE_ACTIVE_POWER:
            case INJECTION_ACTIVE_POWER:
                return PerUnit.SB;
            case TRANSFORMER_PHASE:
            case TRANSFORMER_PHASE_1:
            case TRANSFORMER_PHASE_2:
            case TRANSFORMER_PHASE_3:
                return 1; //TODO: radians ?
            case BUS_TARGET_VOLTAGE:
                LfBus bus = (LfBus) ((SingleVariableLfSensitivityFactor<V, E>) factor).getVariableElement();
                return bus.getNominalV();
            default:
                throw new IllegalArgumentException("Unknown function type " + factor.getVariableType());
        }
    }

    /**
     * Unscales sensitivity value from per-unit, according to its type.
     */
    protected static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double unscaleSensitivity(LfSensitivityFactor<V, E> factor, double sensitivity) {
        return sensitivity * getFunctionBaseValue(factor) / getVariableBaseValue(factor);
    }

    /**
     * Unscales function value from per-unit, according to its type.
     */
    protected static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double unscaleFunction(LfSensitivityFactor<V, E> factor, double value) {
        return value * getFunctionBaseValue(factor);
    }

    protected static int getLegNumber(SensitivityFunctionType type) {
        switch (type) {
            case BRANCH_ACTIVE_POWER_1:
            case BRANCH_CURRENT_1:
                return 1;
            case BRANCH_ACTIVE_POWER_2:
            case BRANCH_CURRENT_2:
                return 2;
            case BRANCH_ACTIVE_POWER_3:
            case BRANCH_CURRENT_3:
                return 3;
            default:
                throw new PowsyblException("Cannot convert function type " + type + " to a leg number");
        }
    }

    protected static int getLegNumber(SensitivityVariableType type) {
        switch (type) {
            case TRANSFORMER_PHASE_1:
                return 1;
            case TRANSFORMER_PHASE_2:
                return 2;
            case TRANSFORMER_PHASE_3:
                return 3;
            default:
                throw new PowsyblException("Cannot convert variable type " + type + " to a leg number");
        }
    }
}
