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
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
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
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSensitivityAnalysis {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSensitivityAnalysis.class);

    protected final MatrixFactory matrixFactory;

    protected AbstractSensitivityAnalysis(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    protected static Injection<?> getInjection(Network network, String injectionId) {
        return getInjection(network, injectionId, true);
    }

    protected static Injection<?> getInjection(Network network, String injectionId, boolean failIfAbsent) {
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

        if (failIfAbsent && injection == null) {
            throw new PowsyblException("Injection '" + injectionId + "' not found");
        }

        return injection;
    }

    protected static LfBus getInjectionLfBus(Network network, LfNetwork lfNetwork, BranchFlowPerInjectionIncrease injectionFactor) {
        return getInjectionLfBus(network, lfNetwork, injectionFactor.getVariable().getInjectionId());
    }

    protected static LfBus getInjectionLfBus(Network network, LfNetwork lfNetwork, String injectionId) {
        Injection<?> injection = getInjection(network, injectionId);
        Bus bus = injection.getTerminal().getBusView().getBus();
        return lfNetwork.getBusById(bus.getId());
    }

    protected static LfBranch getPhaseTapChangerLfBranch(LfNetwork lfNetwork, PhaseTapChangerAngle pstVariable) {
        return lfNetwork.getBranchById(pstVariable.getPhaseTapChangerHolderId());
    }

    protected JacobianMatrix createJacobianMatrix(EquationSystem equationSystem, VoltageInitializer voltageInitializer) {
        double[] x = equationSystem.createStateVector(voltageInitializer);
        equationSystem.updateEquations(x);
        return new JacobianMatrix(equationSystem, matrixFactory);
    }

    protected void fillRhsSensitivityVariable(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorGroups, Matrix rhs) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            factorGroup.fillRhs(lfNetwork, equationSystem, rhs);
        }
    }

    protected List<ParticipatingElement> getParticipatingElements(LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters, Function<ParticipatingElement, Boolean> filter) {
        ActivePowerDistribution.Step step = ActivePowerDistribution.getStep(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant());
        List<ParticipatingElement> participatingElements =  step.getParticipatingElements(lfNetwork).stream().filter(filter::apply).collect(Collectors.toList());
        ParticipatingElement.normalizeParticipationFactors(participatingElements, "bus");
        return participatingElements;
    }

    protected List<ParticipatingElement> getParticipatingElements(LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        return getParticipatingElements(lfNetwork, loadFlowParameters, openLoadFlowParameters, element -> true);
    }

    static class LfSensitivityFactor<T extends EquationTerm> {
        // Wrap factors in specific class to have instant access to their branch, and their equation term
        private final SensitivityFactor factor;

        private final LfBranch functionLfBranch;

        private final String functionLfBranchId;

        private final T equationTerm;

        private Double predefinedResult = null;

        private Double functionReference;

        private Double baseCaseSensitivityValue = Double.NaN; // the sensitivity value without any +1-1 (needs to be recomputed if the stack distribution changes)

        public LfSensitivityFactor(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            this.factor = factor;
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                functionLfBranch = lfNetwork.getBranchById(((BranchFlowPerInjectionIncrease) factor).getFunction().getBranchId());
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                functionLfBranch = lfNetwork.getBranchById(((BranchFlowPerPSTAngle) factor).getFunction().getBranchId());
            } else if (factor instanceof BranchIntensityPerPSTAngle) {
                functionLfBranch = lfNetwork.getBranchById(((BranchIntensityPerPSTAngle) factor).getFunction().getBranchId());
            } else if (factor instanceof BranchFlowPerLinearGlsk) {
                functionLfBranch = lfNetwork.getBranchById(((BranchFlowPerLinearGlsk) factor).getFunction().getBranchId());
            } else {
                throw new UnsupportedOperationException("Only factors of type BranchFlow are supported");
            }
            functionLfBranchId = functionLfBranch.getId();
            equationTerm = equationSystem.getEquationTerm(SubjectType.BRANCH, functionLfBranch.getNum(), clazz);
            functionReference = 0d;
        }

        public static <T extends EquationTerm> LfSensitivityFactor<T> create(SensitivityFactor factor, Network network, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                return new LfBranchFlowPerInjectionIncrease<T>(factor, network, lfNetwork, equationSystem, clazz);
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                return new LfBranchFlowPerPSTAngle<T>(factor, lfNetwork, equationSystem, clazz);
            } else if (factor instanceof BranchIntensityPerPSTAngle) {
                return new LfBranchIntensityPerPSTAngle<>(factor, lfNetwork, equationSystem, clazz);
            }  else if (factor instanceof BranchFlowPerLinearGlsk) {
                return new LfBranchFlowPerLinearGlsk<T>(factor, network, lfNetwork, equationSystem, clazz);
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
        }

        public SensitivityFactor getFactor() {
            return factor;
        }

        public LfBranch getFunctionLfBranch() {
            return functionLfBranch;
        }

        public String getFunctionLfBranchId() {
            return functionLfBranchId;
        }

        public T getEquationTerm() {
            return equationTerm;
        }

        public Double getPredefinedResult() {
            return predefinedResult;
        }

        public void setPredefinedResult(Double predefinedResult) {
            this.predefinedResult = predefinedResult;
        }

        public Double getFunctionReference() {
            return functionReference;
        }

        public void setFunctionReference(Double functionReference) {
            this.functionReference = functionReference;
        }

        public Double getBaseSensitivityValue() {
            return baseCaseSensitivityValue;
        }

        public void setBaseCaseSensitivityValue(Double baseCaseSensitivityValue) {
            this.baseCaseSensitivityValue = baseCaseSensitivityValue;
        }

        public boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity) {
            throw new NotImplementedException("areVariableAndFunctionDisconnected should have an override");
        }

        public boolean isConnectedToComponent(Integer componentNumber, GraphDecrementalConnectivity<LfBus> connectivity) {
            throw new NotImplementedException("isConnectedToComponent should have an override");
        }
    }

    static class LfBranchFlowPerInjectionIncrease<T extends EquationTerm> extends LfSensitivityFactor<T> {

        private final LfBus injectionLfBus;

        LfBranchFlowPerInjectionIncrease(SensitivityFactor factor, Network network, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            super(factor, lfNetwork, equationSystem, clazz);
            injectionLfBus = AbstractSensitivityAnalysis.getInjectionLfBus(network, lfNetwork, (BranchFlowPerInjectionIncrease) factor);
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            return connectivity.getComponentNumber(injectionLfBus) != connectivity.getComponentNumber(getFunctionLfBranch().getBus1())
                || connectivity.getComponentNumber(injectionLfBus) != connectivity.getComponentNumber(getFunctionLfBranch().getBus2());
        }

        @Override
        public boolean isConnectedToComponent(Integer componentNumber, GraphDecrementalConnectivity<LfBus> connectivity) {
            return connectivity.getComponentNumber(injectionLfBus) == componentNumber;
        }

        public LfBus getInjectionLfBus() {
            return injectionLfBus;
        }
    }

    private static class LfBranchPerPSTAngle<T extends EquationTerm> extends LfSensitivityFactor<T> {

        private final LfBranch phaseTapChangerLfBranch;

        LfBranchPerPSTAngle(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            super(factor, lfNetwork, equationSystem, clazz);
            phaseTapChangerLfBranch = getPhaseTapChangerLfBranch(lfNetwork, (PhaseTapChangerAngle) factor.getVariable());
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            return connectivity.getComponentNumber(phaseTapChangerLfBranch.getBus1()) != connectivity.getComponentNumber(getFunctionLfBranch().getBus1())
                || connectivity.getComponentNumber(phaseTapChangerLfBranch.getBus1()) != connectivity.getComponentNumber(getFunctionLfBranch().getBus2());
        }

        @Override
        public boolean isConnectedToComponent(Integer componentNumber, GraphDecrementalConnectivity<LfBus> connectivity) {
            return componentNumber == connectivity.getComponentNumber(phaseTapChangerLfBranch.getBus1());
        }

    }

    static class LfBranchFlowPerPSTAngle<T extends EquationTerm> extends LfBranchPerPSTAngle<T> {
        LfBranchFlowPerPSTAngle(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            super(factor, lfNetwork, equationSystem, clazz);
        }
    }

    static class LfBranchIntensityPerPSTAngle<T extends EquationTerm> extends LfBranchPerPSTAngle<T> {
        LfBranchIntensityPerPSTAngle(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            super(factor, lfNetwork, equationSystem, clazz);
        }
    }

    static class LfBranchFlowPerLinearGlsk<T extends EquationTerm> extends LfSensitivityFactor<T> {

        private final Map<LfBus, Double> injectionBuses;

        LfBranchFlowPerLinearGlsk(SensitivityFactor factor, Network network, LfNetwork lfNetwork, EquationSystem equationSystem, Class<T> clazz) {
            super(factor, lfNetwork, equationSystem, clazz);
            injectionBuses = ((LinearGlsk) factor.getVariable()).getGLSKs()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                    entry -> AbstractSensitivityAnalysis.getInjectionLfBus(network, lfNetwork, entry.getKey()),
                    entry -> entry.getValue().doubleValue(),
                    Double::sum
                ));
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            for (LfBus lfBus : injectionBuses.keySet()) {
                if (connectivity.getComponentNumber(lfBus) == connectivity.getComponentNumber(getFunctionLfBranch().getBus1())
                    && connectivity.getComponentNumber(lfBus) == connectivity.getComponentNumber(getFunctionLfBranch().getBus2())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isConnectedToComponent(Integer componentNumber, GraphDecrementalConnectivity<LfBus> connectivity) {
            if (connectivity.getComponentNumber(getFunctionLfBranch().getBus1()) != componentNumber
                || connectivity.getComponentNumber(getFunctionLfBranch().getBus2()) != componentNumber) {
                return false;
            }
            for (LfBus lfBus : injectionBuses.keySet()) {
                if (connectivity.getComponentNumber(lfBus) == componentNumber) {
                    return true;
                }
            }
            return false;
        }

        public Map<LfBus, Double> getInjectionBuses() {
            return injectionBuses;
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

        PhaseTapChangerFactorGroup(final String id) {
            super(id);
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            LfBranch lfBranch = lfNetwork.getBranchById(getId());
            Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
            if (!a1.isActive()) {
                return;
            }
            rhs.set(a1.getColumn(), getIndex(), Math.toRadians(1d));
        }
    }

    static class InjectionFactorGroup extends SensitivityFactorGroup {

        Map<String, Double> injectionByBus;

        InjectionFactorGroup(final String id) {
            super(id);
        }

        public void setInjectionByBus(final Map<String, Double> slackParticipationByBus) {
            slackParticipationByBus.put(getId(), slackParticipationByBus.getOrDefault(getId(), 0d) + 1);
            injectionByBus = slackParticipationByBus;
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            for (Map.Entry<String, Double> injectionByBus : injectionByBus.entrySet()) {
                LfBus lfBus = lfNetwork.getBusById(injectionByBus.getKey());
                if (lfBus.isSlack()) {
                    continue;
                }
                Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                if (!p.isActive()) {
                    continue;
                }
                int column = p.getColumn();
                rhs.set(column, getIndex(), injectionByBus.getValue() / PerUnit.SB);
            }
        }
    }

    static class LinearGlskGroup extends InjectionFactorGroup {
        // This group makes sense because we are only computing sensitivities in the main connected component
        // otherwise, we wouldn't be able to group different branches within the same group
        private final Map<LfBus, Double> glskMap;
        private Map<String, Double> glskMapInMainComponent;

        LinearGlskGroup(String id, Map<LfBus, Double> glskMap) {
            super(id);
            this.glskMap = glskMap;
            glskMapInMainComponent = glskMap.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().getId(),
                Map.Entry::getValue
            ));
        }

        @Override
        public void setInjectionByBus(final Map<String, Double> participationToSlackByBus) {
            Double glskWeightSum = glskMapInMainComponent.values().stream().mapToDouble(Math::abs).sum();
            glskMapInMainComponent.forEach((busId, weight) -> participationToSlackByBus.merge(busId, weight / glskWeightSum, Double::sum));
            injectionByBus = participationToSlackByBus;
        }

        public void setGlskMapInMainComponent(final Map<String, Double> glskMapInMainComponent) {
            this.glskMapInMainComponent = glskMapInMainComponent;
        }

        public Map<LfBus, Double> getGlskMap() {
            return glskMap;
        }
    }

    protected <T extends EquationTerm> List<SensitivityFactorGroup> createFactorGroups(Network network, List<LfSensitivityFactor<T>> factors) {
        Map<String, SensitivityFactorGroup> groupIndexedById = new HashMap<>(factors.size());
        // index factors by variable config
        for (LfSensitivityFactor<?> factor : factors) {
            if (factor instanceof LfBranchFlowPerInjectionIncrease) {
                LfBus lfBus = ((LfBranchFlowPerInjectionIncrease) factor).getInjectionLfBus();
                // skip disconnected injections
                if (lfBus != null) {
                    groupIndexedById.computeIfAbsent(lfBus.getId(), id -> new InjectionFactorGroup(lfBus.getId())).addFactor(factor);
                }
            } else if (factor instanceof LfBranchPerPSTAngle) {
                PhaseTapChangerAngle pstAngleVariable = (PhaseTapChangerAngle) factor.getFactor().getVariable();
                String phaseTapChangerHolderId = pstAngleVariable.getPhaseTapChangerHolderId();
                TwoWindingsTransformer twt = network.getTwoWindingsTransformer(phaseTapChangerHolderId);
                if (twt == null) {
                    throw new PowsyblException("Phase shifter '" + phaseTapChangerHolderId + "' not found");
                }
                groupIndexedById.computeIfAbsent(phaseTapChangerHolderId, k -> new PhaseTapChangerFactorGroup(phaseTapChangerHolderId)).addFactor(factor);
            } else if (factor instanceof LfBranchFlowPerLinearGlsk) {
                LfBranchFlowPerLinearGlsk lfFactor = (LfBranchFlowPerLinearGlsk) factor;
                LinearGlsk glsk = (LinearGlsk) factor.getFactor().getVariable();
                String glskId = glsk.getId();
                groupIndexedById.computeIfAbsent(glskId, id -> new LinearGlskGroup(glskId, lfFactor.getInjectionBuses())).addFactor(factor);
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getFactor().getClass().getSimpleName() + "' not yet supported");
            }
        }

        // assign an index to each factor group
        int index = 0;
        for (SensitivityFactorGroup factorGroup : groupIndexedById.values()) {
            factorGroup.setIndex(index++);
        }

        return new ArrayList<>(groupIndexedById.values());
    }

    protected void computeInjectionFactors(Map<String, Double> participationFactorByBus, List<SensitivityFactorGroup> factorGroups) {
        // compute the corresponding injection (including participation) for each factor
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            if (factorGroup instanceof InjectionFactorGroup) {
                InjectionFactorGroup injectionGroup = (InjectionFactorGroup) factorGroup;
                injectionGroup.setInjectionByBus(new HashMap<>(participationFactorByBus));
            }
        }
    }

    protected void rescaleGlsk(List<SensitivityFactorGroup> factorGroups, GraphDecrementalConnectivity<LfBus> connectivity, Integer mainComponentNumber) {
        // compute the corresponding injection (with participation) for each factor
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            if (!(factorGroup instanceof LinearGlskGroup)) {
                continue;
            }
            LinearGlskGroup glskGroup = (LinearGlskGroup) factorGroup;
            Map<String, Double> remainingGlskInjections = glskGroup.getGlskMap().entrySet().stream()
                .filter(entry -> connectivity.getComponentNumber(entry.getKey()) == mainComponentNumber)
                .collect(Collectors.toMap(entry -> entry.getKey().getId(), Map.Entry::getValue));
            glskGroup.setGlskMapInMainComponent(remainingGlskInjections);
        }
    }

    protected DenseMatrix initFactorsRhs(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorsGroups) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsGroups.size());
        fillRhsSensitivityVariable(lfNetwork, equationSystem, factorsGroups, rhs);
        return rhs;
    }

    protected <T extends EquationTerm> void setPredefinedResults(Collection<LfSensitivityFactor<T>> lfFactors, GraphDecrementalConnectivity<LfBus> connectivity, int mainComponent) {
        for (LfSensitivityFactor<T> factor : lfFactors) {
            // check if the factor function and variable are in different connected components
            if (factor.areVariableAndFunctionDisconnected(connectivity)) {
                factor.setPredefinedResult(0d);
            } else if (!factor.isConnectedToComponent(mainComponent, connectivity)) {
                factor.setPredefinedResult(Double.NaN); // works for sensitivity and function reference
            }
        }
    }

    public void checkSensitivities(Network network, LfNetwork lfNetwork, List<SensitivityFactor> factors) {
        for (SensitivityFactor<?, ?> factor : factors) {
            LfBranch monitoredBranch;
            if (factor instanceof  BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                if (injection == null) {
                    throw new PowsyblException("Injection " + injectionFactor.getVariable().getInjectionId() + " not found in the network");
                }
                if (lfNetwork.getBranchById(factor.getFunction().getId()) == null) {
                    throw new PowsyblException("Branch '" + factor.getFunction().getId() + "' not found");
                }
                monitoredBranch = lfNetwork.getBranchById(injectionFactor.getFunction().getBranchId());
            } else if (factor instanceof BranchIntensityPerPSTAngle) {
                BranchIntensityPerPSTAngle pstAngleFactor = (BranchIntensityPerPSTAngle) factor;
                String phaseTapChangerHolderId = pstAngleFactor.getVariable().getPhaseTapChangerHolderId();
                TwoWindingsTransformer twt = network.getTwoWindingsTransformer(phaseTapChangerHolderId);
                if (twt == null) {
                    throw new PowsyblException("Phase shifter '" + phaseTapChangerHolderId + "' not found in the network");
                }
                if (lfNetwork.getBranchById(factor.getFunction().getId()) == null) {
                    throw new PowsyblException("Branch '" + factor.getFunction().getId() + "' not found");
                }
                monitoredBranch = lfNetwork.getBranchById(pstAngleFactor.getFunction().getBranchId());
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                String phaseTapChangerHolderId = pstAngleFactor.getVariable().getPhaseTapChangerHolderId();
                TwoWindingsTransformer twt = network.getTwoWindingsTransformer(phaseTapChangerHolderId);
                if (twt == null) {
                    throw new PowsyblException("Phase shifter '" + phaseTapChangerHolderId + "' not found in the network");
                }
                if (lfNetwork.getBranchById(factor.getFunction().getId()) == null) {
                    throw new PowsyblException("Branch '" + factor.getFunction().getId() + "' not found");
                }
                monitoredBranch = lfNetwork.getBranchById(pstAngleFactor.getFunction().getBranchId());
            } else if (factor instanceof BranchFlowPerLinearGlsk) {
                BranchFlowPerLinearGlsk glskFactor = (BranchFlowPerLinearGlsk) factor;
                if (glskFactor.getVariable().getGLSKs().isEmpty()) {
                    throw new PowsyblException("The glsk '" + factor.getVariable().getId() + "' cannot be empty");
                }
                for (Map.Entry<String, Float> injectionEntry : glskFactor.getVariable().getGLSKs().entrySet()) {
                    Injection<?> injection = getInjection(network, injectionEntry.getKey());
                    if (injection == null) {
                        throw new PowsyblException("Injection " + injectionEntry.getKey() + " not found in the network");
                    }
                }
                if (lfNetwork.getBranchById(factor.getFunction().getId()) == null) {
                    throw new PowsyblException("Branch '" + factor.getFunction().getId() + "' not found");
                }
                monitoredBranch = lfNetwork.getBranchById(glskFactor.getFunction().getBranchId());
            } else {
                throw new PowsyblException("Only sensitivity factors of type BranchFlowPerInjectionIncrease and BranchFlowPerPSTAngle are yet supported");
            }
            if (monitoredBranch == null) {
                throw new PowsyblException("Monitored branch " + factor.getFunction().getId() + " not found in the network");
            }
        }
    }

    public void cutConnectivity(LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity, Collection<String> elementsPotentiallyBreakingConnectivity) {
        elementsPotentiallyBreakingConnectivity.stream()
            .map(lfNetwork::getBranchById)
            .forEach(lfBranch -> connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2()));
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
                LOGGER.warn("Contingency " + contingency.getContingency().getId() + " has no impact.");
            }
        }
    }

    public void checkLoadFlowParameters(LoadFlowParameters lfParameters) {
        if (!lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            && !lfParameters.getBalanceType().equals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)) {
            throw new UnsupportedOperationException("Unsupported balance type mode: " + lfParameters.getBalanceType());
        }
    }
}
