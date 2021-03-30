/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunction;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.SensitivityVariable;
import com.powsybl.sensitivity.factors.*;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import com.powsybl.sensitivity.factors.functions.BusVoltage;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import com.powsybl.sensitivity.factors.variables.TargetV;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
            return rtc != null ? rtc.getRegulationTerminal() : null;
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
            return regulatingTerminal;
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

    abstract static class AbstractLfSensitivityFunction {
        private EquationTerm equationTerm;

        public AbstractLfSensitivityFunction(EquationTerm equationTerm) {
            this.equationTerm = equationTerm;
        }

        public EquationTerm getEquationTerm() {
            return equationTerm;
        }
    }

    abstract static class AbstractLfBranchFunction extends AbstractLfSensitivityFunction {
        private LfBranch lfBranch;
        private String lfBranchId;

        public AbstractLfBranchFunction(LfBranch branch, EquationTerm equationTerm) {
            super(equationTerm);
            this.lfBranch = branch;
            this.lfBranchId = branch.getId();
        }

        public LfBranch getLfBranch() {
            return lfBranch;
        }

        public String getLfBranchId() {
            return lfBranchId;
        }
    }

    static class LfBranchFlow extends AbstractLfBranchFunction {
        public LfBranchFlow(LfBranch branch) {
            super(branch, (EquationTerm) branch.getP1());
        }
    }

    static class LfBranchIntensity extends AbstractLfBranchFunction {
        public LfBranchIntensity(LfBranch branch) {
            super(branch, (EquationTerm) branch.getI1());
        }
    }

    abstract static class AbstractLfBusFunction extends AbstractLfSensitivityFunction {
        private LfBus lfBus;

        public AbstractLfBusFunction(LfBus bus, EquationTerm equationTerm) {
            super(equationTerm);
            this.lfBus = bus;
        }

        public LfBus getLfBus() {
            return lfBus;
        }
    }

    static class LfBusVoltage extends AbstractLfBusFunction {
        public LfBusVoltage(LfBus bus) {
            super(bus, (EquationTerm) bus.getV());
        }
    }

    static class LfSensitivityFactor {

        enum Status {
            VALID,
            SKIP,
            ZERO
        }

        // Wrap factors in specific class to have instant access to their branch and their equation term
        private final Object context;

        private final String functionId;

        private final String variableId;

        AbstractLfSensitivityFunction function;

        private Double predefinedResult = null;

        private double functionReference = 0d;

        private double baseCaseSensitivityValue = Double.NaN; // the sensitivity value on pre contingency network, that needs to be recomputed if the stack distribution change

        private Status status = Status.VALID;

        public LfSensitivityFactor(Object context, String functionId, String variableId, AbstractLfSensitivityFunction function) {
            this.context = context;
            this.functionId = Objects.requireNonNull(functionId);
            this.variableId = Objects.requireNonNull(variableId);
            this.function = function;
            if (function == null) {
                status = Status.ZERO;
            }
        }

        public Object getContext() {
            return context;
        }

        public String getFunctionId() {
            return functionId;
        }

        public String getVariableId() {
            return variableId;
        }


        public AbstractLfSensitivityFunction getFunction() {
            return function;
        }

        public Double getPredefinedResult() {
            return predefinedResult;
        }

        public void setPredefinedResult(Double predefinedResult) {
            this.predefinedResult = predefinedResult;
        }

        public double getFunctionReference() {
            return functionReference;
        }

        public void setFunctionReference(double functionReference) {
            this.functionReference = functionReference;
        }

        public double getBaseSensitivityValue() {
            return baseCaseSensitivityValue;
        }

        public void setBaseCaseSensitivityValue(double baseCaseSensitivityValue) {
            this.baseCaseSensitivityValue = baseCaseSensitivityValue;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity) {
            throw new NotImplementedException("areVariableAndFunctionDisconnected should have an override");
        }

        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            throw new NotImplementedException("isConnectedToComponent should have an override");
        }
    }

    static class LfBranchFlowPerInjectionIncrease extends LfSensitivityFactor {

        private final LfBus injectionLfBus;

        LfBranchFlowPerInjectionIncrease(Object context, String functionId, String variableId, LfBranchFlow function, LfBus injectionLfBus) {
            super(context, functionId, variableId, function);
            this.injectionLfBus = injectionLfBus;
            if (injectionLfBus == null) {
                setStatus(Status.SKIP);
            }
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            LfBranch functionBranch = ((LfBranchFlow) getFunction()).getLfBranch();
            return connectivity.getComponentNumber(injectionLfBus) != connectivity.getComponentNumber(functionBranch.getBus1())
                || connectivity.getComponentNumber(injectionLfBus) != connectivity.getComponentNumber(functionBranch.getBus2());
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            return connectedComponent.contains(injectionLfBus);
        }

        public LfBus getInjectionLfBus() {
            return injectionLfBus;
        }
    }

    private static class LfBranchPerPSTAngle extends LfSensitivityFactor {

        private final LfBranch phaseTapChangerLfBranch;

        LfBranchPerPSTAngle(Object context, String functionId, String variableId, AbstractLfBranchFunction function, LfBranch phaseTapChangerLfBranch) {
            super(context, functionId, variableId, function);
            this.phaseTapChangerLfBranch = phaseTapChangerLfBranch;
            if (phaseTapChangerLfBranch == null) {
                setStatus(Status.SKIP);
            }
        }

        public LfBranch getPhaseTapChangerLfBranch() {
            return phaseTapChangerLfBranch;
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            LfBranch functionBranch = ((AbstractLfBranchFunction) getFunction()).getLfBranch();
            return connectivity.getComponentNumber(phaseTapChangerLfBranch.getBus1()) != connectivity.getComponentNumber(functionBranch.getBus1())
                || connectivity.getComponentNumber(phaseTapChangerLfBranch.getBus1()) != connectivity.getComponentNumber(functionBranch.getBus2());
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            return connectedComponent.contains(phaseTapChangerLfBranch.getBus1());
        }

    }

    static class LfBranchFlowPerPSTAngle extends LfBranchPerPSTAngle {

        LfBranchFlowPerPSTAngle(Object context, String functionId, String variableId, LfBranchFlow function, LfBranch phaseTapChangerLfBranch) {
            super(context, functionId, variableId, function, phaseTapChangerLfBranch);
        }
    }

    static class LfBranchIntensityPerPSTAngle extends LfBranchPerPSTAngle {

        LfBranchIntensityPerPSTAngle(Object context, String functionId, String variableId, LfBranchIntensity function, LfBranch phaseTapChangerLfBranch) {
            super(context, functionId, variableId, function, phaseTapChangerLfBranch);
        }
    }

    static class LfBranchFlowPerLinearGlsk extends LfSensitivityFactor {

        private final Map<LfBus, Double> injectionLfBuses;

        LfBranchFlowPerLinearGlsk(Object context, String functionId, String variableId, LfBranchFlow function,
                                  Map<LfBus, Double> injectionLfBuses) {
            super(context, functionId, variableId, function);
            this.injectionLfBuses = injectionLfBuses;
            if (injectionLfBuses.isEmpty()) {
                setStatus(Status.SKIP);
            }
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            LfBranch functionBranch = ((LfBranchFlow) getFunction()).getLfBranch();
            for (LfBus lfBus : injectionLfBuses.keySet()) {
                if (connectivity.getComponentNumber(lfBus) == connectivity.getComponentNumber(functionBranch.getBus1())
                    && connectivity.getComponentNumber(lfBus) == connectivity.getComponentNumber(functionBranch.getBus2())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            LfBranch functionBranch = ((LfBranchFlow) getFunction()).getLfBranch();
            if (!connectedComponent.contains(functionBranch.getBus1())
                || !connectedComponent.contains(functionBranch.getBus2())) {
                return false;
            }
            for (LfBus lfBus : injectionLfBuses.keySet()) {
                if (connectedComponent.contains(lfBus)) {
                    return true;
                }
            }
            return false;
        }

        public Map<LfBus, Double> getInjectionLfBuses() {
            return injectionLfBuses;
        }
    }

    static class LfBusVoltagePerTargetV extends LfSensitivityFactor {

        private final LfBus targetVBus;

        LfBusVoltagePerTargetV(SensitivityFactor factor, Network network, LfNetwork lfNetwork) {
            super(factor, network, lfNetwork);
            String targetVEquipmentId = ((TargetV) factor.getVariable()).getEquipmentId();
            Terminal regulatingTerminal = getEquipmentRegulatingTerminal(network, targetVEquipmentId);
            if (regulatingTerminal == null) {
                setStatus(Status.SKIP);
                targetVBus = null;
                return;
            }
            targetVBus = lfNetwork.getBusById(regulatingTerminal.getBusView().getBus().getId());
            if (targetVBus == null) {
                setStatus(Status.SKIP);
            }
        }

        @Override
        public Double getFunctionReference() {
            return super.getFunctionReference() / PerUnit.SB;
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            LfBus functionBus = ((LfBusVoltage) getFunction()).getLfBus();
            return connectivity.getComponentNumber(targetVBus) != connectivity.getComponentNumber(functionBus);
        }

        @Override
        public boolean isConnectedToComponent(Set<LfBus> connectedComponent) {
            return connectedComponent.contains(targetVBus);
        }

        public LfBus getTargetVBus() {
            return targetVBus;
        }
    }

    static class SensitivityFactorGroup {

        private final String id;

        private final List<LfSensitivityFactor> factors = new ArrayList<>();

        private int index = -1;

        SensitivityFactorGroup(String id) {
            this.id = Objects.requireNonNull(id);
        }

        String getId() {
            return id;
        }

        List<LfSensitivityFactor> getFactors() {
            return factors;
        }

        int getIndex() {
            return index;
        }

        void setIndex(int index) {
            this.index = index;
        }

        void addFactor(LfSensitivityFactor factor) {
            factors.add(factor);
        }

        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            throw new NotImplementedException("fillRhs method must be implemented in subclasses");
        }
    }

    static class PhaseTapChangerFactorGroup extends SensitivityFactorGroup {

        private final LfBranch lfBranch;

        PhaseTapChangerFactorGroup(LfBranch lfBranch) {
            super(lfBranch.getId());
            this.lfBranch = Objects.requireNonNull(lfBranch);
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
            if (!a1.isActive()) {
                return;
            }
            rhs.set(a1.getColumn(), getIndex(), Math.toRadians(1d));
        }
    }

    abstract static class AbstractInjectionFactorGroup extends SensitivityFactorGroup {

        private Map<LfBus, Double> participationByBus;

        AbstractInjectionFactorGroup(String id) {
            super(id);
        }

        public void setParticipationByBus(Map<LfBus, Double> participationByBus) {
            this.participationByBus = participationByBus;
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            for (Map.Entry<LfBus, Double> lfBusAndParticipationFactor : participationByBus.entrySet()) {
                LfBus lfBus = lfBusAndParticipationFactor.getKey();
                Equation p = (Equation) lfBus.getP();
                Double participationFactor = lfBusAndParticipationFactor.getValue();
                if (lfBus.isSlack() || !p.isActive()) {
                    continue;
                }
                int column = p.getColumn();
                rhs.set(column, getIndex(), participationFactor / PerUnit.SB);
            }
        }
    }

    static class SingleInjectionFactorGroup extends AbstractInjectionFactorGroup {

        private final LfBus lfBus;

        SingleInjectionFactorGroup(LfBus lfBus) {
            super(lfBus.getId());
            this.lfBus = Objects.requireNonNull(lfBus);
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            super.fillRhs(lfNetwork, equationSystem, rhs);
            if (!lfBus.isSlack() && ((Equation) lfBus.getP()).isActive()) {
                rhs.add(((Equation) lfBus.getP()).getColumn(), getIndex(), 1d / PerUnit.SB);
            }
        }
    }

    static class LinearGlskGroup extends AbstractInjectionFactorGroup {
        // This group makes sense because we are only computing sensitivities in the main connected component
        // otherwise, we wouldn't be able to group different branches within the same group
        private final Map<LfBus, Double> glskMap;
        private Map<LfBus, Double> glskMapInMainComponent;

        LinearGlskGroup(String id, Map<LfBus, Double> glskMap) {
            super(id);
            this.glskMap = glskMap;
            this.glskMapInMainComponent = glskMap;
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            super.fillRhs(lfNetwork, equationSystem, rhs);
            Double glskWeightSum = glskMapInMainComponent.values().stream().mapToDouble(Math::abs).sum();
            glskMapInMainComponent.forEach((bus, weight) -> {
                Equation p = (Equation) bus.getP();
                if (bus.isSlack() || !p.isActive()) {
                    return;
                }
                rhs.add(p.getColumn(), getIndex(), weight / glskWeightSum / PerUnit.SB);
            });
        }

        public void setGlskMapInMainComponent(final Map<LfBus, Double> glskMapInMainComponent) {
            this.glskMapInMainComponent = glskMapInMainComponent;
        }

        public Map<LfBus, Double> getGlskMap() {
            return glskMap;
        }
    }

    static class TargetVGroup extends SensitivityFactorGroup {

        TargetVGroup(String id) {
            super(id);
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            LfBus lfBus = lfNetwork.getBusById(getId());
            Equation v = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_V).orElseThrow(IllegalStateException::new);
            if (!v.isActive()) {
                return;
            }
            rhs.set(v.getColumn(), getIndex(), 1d / PerUnit.SB);
        }
    }

    protected List<SensitivityFactorGroup> createFactorGroups(List<LfSensitivityFactor> factors) {
        Map<String, SensitivityFactorGroup> groupIndexedById = new HashMap<>(factors.size());
        // index factors by variable config
        for (LfSensitivityFactor factor : factors) {
            if (factor.getStatus() == LfSensitivityFactor.Status.SKIP) {
                continue;
            }
            if (factor instanceof LfBranchFlowPerInjectionIncrease) {
                LfBus lfBus = ((LfBranchFlowPerInjectionIncrease) factor).getInjectionLfBus();
                groupIndexedById.computeIfAbsent(lfBus.getId(), id -> new SingleInjectionFactorGroup(lfBus)).addFactor(factor);
            } else if (factor instanceof LfBranchPerPSTAngle) {
                LfBranch lfBranch = ((LfBranchPerPSTAngle) factor).getPhaseTapChangerLfBranch();
                groupIndexedById.computeIfAbsent(lfBranch.getId(), k -> new PhaseTapChangerFactorGroup(lfBranch)).addFactor(factor);
            } else if (factor instanceof LfBranchFlowPerLinearGlsk) {
                LfBranchFlowPerLinearGlsk lfFactor = (LfBranchFlowPerLinearGlsk) factor;
                String glskId = factor.getVariableId();
                groupIndexedById.computeIfAbsent(glskId, id -> new LinearGlskGroup(glskId, lfFactor.getInjectionLfBuses())).addFactor(factor);
            } else if (factor instanceof LfBusVoltagePerTargetV) {
                String targetVId = ((LfBusVoltagePerTargetV) factor).getTargetVBus().getId();
                groupIndexedById.computeIfAbsent(targetVId, id -> new TargetVGroup(targetVId)).addFactor(factor);
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getContext().getClass().getSimpleName() + "' not yet supported");
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

    protected void computeInjectionFactors(Map<LfBus, Double> participationFactorByBus, List<SensitivityFactorGroup> factorGroups) {
        // compute the corresponding injection (including participation) for each factor
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            if (factorGroup instanceof AbstractInjectionFactorGroup) {
                AbstractInjectionFactorGroup injectionGroup = (AbstractInjectionFactorGroup) factorGroup;
                injectionGroup.setParticipationByBus(participationFactorByBus);
            }
        }
    }

    protected DenseMatrix initFactorsRhs(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorsGroups) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsGroups.size());
        fillRhsSensitivityVariable(lfNetwork, equationSystem, factorsGroups, rhs);
        return rhs;
    }

    protected void fillRhsSensitivityVariable(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorGroups, Matrix rhs) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            factorGroup.fillRhs(lfNetwork, equationSystem, rhs);
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
            if (!(factorGroup instanceof LinearGlskGroup)) {
                continue;
            }
            LinearGlskGroup glskGroup = (LinearGlskGroup) factorGroup;
            Map<LfBus, Double> remainingGlskInjections = glskGroup.getGlskMap().entrySet().stream()
                .filter(entry -> !nonConnectedBuses.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            glskGroup.setGlskMapInMainComponent(remainingGlskInjections);
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

    public void checkContingencies(LfNetwork lfNetwork, List<PropagatedContingency> contingencies) {
        for (PropagatedContingency contingency : contingencies) {
            for (ContingencyElement contingencyElement : contingency.getContingency().getElements()) {
                if (!contingencyElement.getType().equals(ContingencyElementType.BRANCH)) {
                    throw new UnsupportedOperationException("Only contingencies on a branch are yet supported");
                }
                LfBranch lfBranch = lfNetwork.getBranchById(contingencyElement.getId());
                if (lfBranch == null) {
                    throw new PowsyblException("The contingency on the branch " + contingencyElement.getId() + " not found in the network");
                }

            }
            Set<String> branchesToRemove = new HashSet<>(); // branches connected to one side, or switches
            for (String branchId : contingency.getBranchIdsToOpen()) {
                LfBranch lfBranch = lfNetwork.getBranchById(branchId);
                if (lfBranch == null) {
                    branchesToRemove.add(branchId); // this is certainly a switch
                    continue;
                }
                if (lfBranch.getBus2() == null || lfBranch.getBus1() == null) {
                    branchesToRemove.add(branchId); // contains the branches that are connected only on one side
                }
            }
            contingency.getBranchIdsToOpen().removeAll(branchesToRemove);
            if (contingency.getBranchIdsToOpen().isEmpty()) {
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

    private static void checkPhaseShifter(Network network, String transformerId) {
        TwoWindingsTransformer twt = network.getTwoWindingsTransformer(transformerId);
        if (twt == null) {
            throw new PowsyblException("Two windings transformer '" + transformerId + "' not found");
        }
        if (twt.getPhaseTapChanger() == null) {
            throw new PowsyblException("Two windings transformer '" + transformerId + "' is not a phase shifter");
        }
    }

    public List<LfSensitivityFactor> readAndCheckFactors(Network network, SensitivityFactorReader factorReader, LfNetwork lfNetwork) {
        final List<LfSensitivityFactor> lfFactors = new ArrayList<>();

        factorReader.read(new SensitivityFactorReader.Handler() {
            @Override
            public void onSimpleFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId) {
                // todo: mergefix, and check voltage
                if (functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER) {
                    checkBranch(network, functionId);
                    LfBranch functionLfBranch = lfNetwork.getBranchById(functionId);
                    if (variableType == SensitivityVariableType.INJECTION_ACTIVE_POWER) {
                        Bus injectionBus = getInjectionBus(network, variableId);
                        LfBus injectionLfBus = injectionBus != null ? lfNetwork.getBusById(injectionBus.getId()) : null;
                        lfFactors.add(new LfBranchFlowPerInjectionIncrease(factorContext, functionId, variableId, functionLfBranch, injectionLfBus));
                    } else if (variableType == SensitivityVariableType.TRANSFORMER_PHASE) {
                        checkPhaseShifter(network, variableId);
                        LfBranch phaseTapChangerLfBranch = lfNetwork.getBranchById(variableId);
                        lfFactors.add(new LfBranchFlowPerPSTAngle(factorContext, functionId, variableId, functionLfBranch, phaseTapChangerLfBranch));
                    } else {
                        throw new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
                    }
                } else if (functionType == SensitivityFunctionType.BRANCH_CURRENT) {
                    checkBranch(network, functionId);
                    LfBranch functionLfBranch = lfNetwork.getBranchById(functionId);
                    if (variableType == SensitivityVariableType.TRANSFORMER_PHASE) {
                        checkPhaseShifter(network, variableId);
                        LfBranch phaseTapChangerLfBranch = lfNetwork.getBranchById(variableId);
                        lfFactors.add(new LfBranchIntensityPerPSTAngle(factorContext, functionId, variableId, functionLfBranch, phaseTapChangerLfBranch));
                    } else {
                        throw new PowsyblException("Variable type " + variableType + " not supported with function type " + functionType);
                    }
                } else {
                    throw new PowsyblException("Function type " + functionType + " not supported");
                }
            }

            @Override
            public void onMultipleVariablesFactor(Object factorContext, SensitivityFunctionType functionType, String functionId,
                                                  SensitivityVariableType variableType, String variableId, List<WeightedSensitivityVariable> variables) {
                if (functionType == SensitivityFunctionType.BRANCH_ACTIVE_POWER
                        && variableType == SensitivityVariableType.INJECTION_ACTIVE_POWER) {
                    checkBranch(network, functionId);
                    LfBranch functionLfBranch = lfNetwork.getBranchById(functionId);
                    Map<LfBus, Double> injectionLfBuses = new HashMap<>();
                    List<String> skippedInjection = new ArrayList<>(variables.size());
                    for (WeightedSensitivityVariable variable : variables) {
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
                    lfFactors.add(new LfBranchFlowPerLinearGlsk(factorContext, functionId, variableId, functionLfBranch, injectionLfBuses));
                } else {
                    throw new PowsyblException("Function type " + functionType + " and variable type " + variableType + " not supported");
                }
            }
        });
        return lfFactors;
    }
}
