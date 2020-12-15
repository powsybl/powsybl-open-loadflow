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
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
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

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    static class SensitivityVariableConfiguration {

        private final Map<String, Number> busInjectionById;

        private final Set<String> phaseTapChangerHolderIds;

        SensitivityVariableConfiguration(Map<String, Number> busInjectionById, Set<String> phaseTapChangerHolderIds) {
            this.busInjectionById = Objects.requireNonNull(busInjectionById);
            this.phaseTapChangerHolderIds = Objects.requireNonNull(phaseTapChangerHolderIds);
        }

        Map<String, Number> busInjectionById() {
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
                                                          DenseMatrix states, Map<String, Double> functionReferenceByBranch) {
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        if (lfBranch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
        }
        ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        double value = p1.calculate(states, factorGroup.getIndex()) * PerUnit.SB;
        return new SensitivityValue(factor, value, functionReferenceByBranch.get(branchId).doubleValue(), 0);
    }

    private List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                              Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                                              DenseMatrix states, Map<String, Double> functionReferenceByBranch) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>();

        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityFactorGroup factorGroup = e.getValue();
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, injectionFactor.getFunction().getBranchId(),
                            factor, factorGroup, states, functionReferenceByBranch));
                } else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, pstAngleFactor.getFunction().getBranchId(),
                            factor, factorGroup, states, functionReferenceByBranch));
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }
        }

        return sensitivityValues;
    }

    private DenseMatrix initRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsByVarConfig.size());
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityVariableConfiguration configuration = e.getKey();
            SensitivityFactorGroup factorGroup = e.getValue();
            for (Map.Entry<String, Number> busAndInjection : configuration.busInjectionById().entrySet()) {
                LfBus lfBus = lfNetwork.getBusById(busAndInjection.getKey());
                if (lfBus.isSlack()) {
                    throw new PowsyblException("Cannot set injection of slack bus");
                }
                Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), factorGroup.getIndex(), busAndInjection.getValue().doubleValue() / PerUnit.SB);
            }
            for (String phaseTapChangerHolderId : configuration.getPhaseTapChangerHolderIds()) {
                LfBranch lfBranch = lfNetwork.getBranchById(phaseTapChangerHolderId);
                Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
                rhs.set(a1.getColumn(), factorGroup.getIndex(), Math.toRadians(1d));
            }
        }
        return rhs;
    }

    private Map<SensitivityVariableConfiguration, SensitivityFactorGroup> indexFactorsByVariableConfig(Network network, List<SensitivityFactor> factors, LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters) {
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = new LinkedHashMap<>(factors.size());
        Map<String, Number> participationFactorByBus = getParticipationFactorByBus(network, lfNetwork, loadFlowParameters); // empty if slack is not distributed
        participationFactorByBus.remove(lfNetwork.getSlackBus().getId()); // the injection on the slack bus will not appear in the rhs
        participationFactorByBus.replaceAll((key, value) -> -value.doubleValue()); // the slack distribution on a bus will be the opposite of its participation

        // index factors by variable config
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                Bus bus = injection.getTerminal().getBusView().getBus();
                // skip disconnected injections
                if (bus != null) {
                    Map<String, Number> busInjectionById = new HashMap<>(participationFactorByBus);
                    // add 1 where we are making the injection
                    // when the slack is not distributed, then bus compensation is a singleton {bus -> 1}
                    if (lfNetwork.getBusById(bus.getId()).isSlack()) {
                        if (!loadFlowParameters.isDistributedSlack()) {
                            throw new PowsyblException("Cannot analyse sensitivity of slack bus");
                        }
                    } else {
                        busInjectionById.put(bus.getId(), busInjectionById.getOrDefault(bus.getId(), 0).doubleValue() + 1);
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

    /**
     * Return a mapping between the participating element id through its connected bus, and its participation value in the slack distribution
     * @param lfNetwork
     * @param loadFlowParameters
     * @return
     */
    private Map<String, Number> getParticipationFactorByBus(Network network, LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters) {

        Map<String, Number> participationFactorByBusMap = new HashMap<>();

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

            participatingElements.forEach(participatingElement -> {
                String busId;
                if (participatingElement.getElement() instanceof LfGenerator) {
                    busId = network.getGenerator(((LfGenerator) participatingElement.getElement()).getId()).getTerminal().getBusView().getBus().getId();
                } else if (participatingElement.getElement() instanceof LfBus) {
                    busId = ((LfBus) participatingElement.getElement()).getId();
                } else {
                    throw new UnsupportedOperationException("Unsupported participating element");
                }
                participationFactorByBusMap.put(busId, participationFactorByBusMap.getOrDefault(busId, 0).doubleValue() + participatingElement.getFactor());
            });
        }
        return participationFactorByBusMap;
    }

    public List<SensitivityValue> analyse(Network network, List<SensitivityFactor> factors, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
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
        DcLoadFlowParameters dcLoadFlowParameters = new DcLoadFlowParameters(lfParametersExt.getSlackBusSelector(),
                new SparseMatrixFactory(), true, lfParametersExt.isDcUseTransformerRatio(), lfParameters.isDistributedSlack(),  lfParameters.getBalanceType());
        DcLoadFlowResult dcLoadFlowResult = new DcLoadFlowEngine(lfNetworks, dcLoadFlowParameters).run();
        for (LfBranch branch : dcLoadFlowResult.getNetwork().getBranches()) {
            functionReferenceByBranch.put(branch.getId(), branch.getP1());
        }

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, factors, lfNetwork, lfParameters);

        if (factorsByVarConfig.isEmpty()) {
            return Collections.emptyList();
        }

        // initialize right hand side
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig);

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // solve system
        DenseMatrix states = solveTransposed(rhs, j);

        // calculate sensitivity values
        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states, functionReferenceByBranch);
    }
}
