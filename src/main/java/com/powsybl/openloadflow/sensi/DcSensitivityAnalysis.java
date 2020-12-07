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
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AbstractDistributedSlackOuterLoop;
import com.powsybl.openloadflow.ac.DistributedSlackOnGenerationOuterLoop;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    static class SensitivityVariableConfiguration {

        private final Set<String> busIds;

        private final Set<String> phaseTapChangerHolderIds;

        SensitivityVariableConfiguration(Set<String> busIds, Set<String> phaseTapChangerHolderIds) {
            this.busIds = Objects.requireNonNull(busIds);
            this.phaseTapChangerHolderIds = Objects.requireNonNull(phaseTapChangerHolderIds);
        }

        Set<String> getBusIds() {
            return busIds;
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
            return busIds.equals(that.busIds) && phaseTapChangerHolderIds.equals(that.phaseTapChangerHolderIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(busIds);
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
                                                          DenseMatrix states) {
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        if (lfBranch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
        }
        ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        double value = p1.calculate(states, factorGroup.getIndex()) * PerUnit.SB;
        return new SensitivityValue(factor, value, 0, 0);
    }

    private List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                              Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig,
                                                              DenseMatrix states) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>();

        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityFactorGroup factorGroup = e.getValue();
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, injectionFactor.getFunction().getBranchId(),
                                                                       factor, factorGroup, states));
                }  else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork, equationSystem, pstAngleFactor.getFunction().getBranchId(),
                                                                       factor, factorGroup, states));
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
            for (String busId : configuration.getBusIds()) {
                LfBus lfBus = lfNetwork.getBusById(busId);
                if (lfBus.isSlack()) {
                    throw new PowsyblException("Cannot analyse sensitivity of slack bus");
                }
                Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), factorGroup.getIndex(), 1d / PerUnit.SB);
            }
            for (String phaseTapChangerHolderId : configuration.getPhaseTapChangerHolderIds()) {
                LfBranch lfBranch = lfNetwork.getBranchById(phaseTapChangerHolderId);
                Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
                rhs.set(a1.getColumn(), factorGroup.getIndex(), Math.toRadians(1d));
            }
        }
        return rhs;
    }

    private Map<SensitivityVariableConfiguration, SensitivityFactorGroup> indexFactorsByVariableConfig(Network network, List<SensitivityFactor> factors) {
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = new LinkedHashMap<>(factors.size());

        // index factors by variable config
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                Bus bus = injection.getTerminal().getBusView().getBus();
                // skip disconnected injections
                if (bus != null) {
                    SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(Collections.singleton(bus.getId()), Collections.emptySet());
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
                SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(Collections.emptySet(), Collections.singleton(phaseTapChangerHolderId));
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
     * If the slack is distributed, we need to create a factor for every generator
     * @param lfNetwork
     * @param oldFactors
     * @return
     */
    private List<SensitivityFactor> getDistributedFactors(LfNetwork lfNetwork, List<SensitivityFactor> oldFactors) {
        // todo: load ?

        List<SensitivityFactor> factors = new ArrayList<>();

        Set<String> monitoredLines = new HashSet<>();
        for (SensitivityFactor oldFactor : oldFactors) {
            // if the factor if an injection then we will compute it with distribution, otherwise we just keep it as it is
            if (oldFactor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease branchFactor = (BranchFlowPerInjectionIncrease) oldFactor;
                monitoredLines.add(branchFactor.getFunction().getBranchId());
            } else {
                factors.add(oldFactor);
            }
        }

        monitoredLines.forEach(
            lineId -> {
                // todo: check that the line is still in the connected component ?
                BranchFlow branchFlow = new BranchFlow(lineId, lineId, lineId);
                lfNetwork.getBuses().forEach(// todo: not all generators, only those who have an impact in compensation
                    bus -> bus.getGenerators().forEach(generator -> {
                        String genId = generator.getId();
                        factors.add(new BranchFlowPerInjectionIncrease(branchFlow,
                                new InjectionIncrease(genId, genId, genId)));
                    })
                );
            }
        );
        return factors;
    }

    /**
     * Return a list of id from participating element, and its participation value
     * @param lfNetwork
     * @param loadFlowParameters
     * @param openLoadFlowParameters
     * @return
     */
    private List<Pair<String, Number>> getParticipatingElements(LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        List<Pair<String, Number>> contributionList = new ArrayList<>();

        if (loadFlowParameters.isDistributedSlack()) {
            switch (loadFlowParameters.getBalanceType()) {
                case PROPORTIONAL_TO_GENERATION_P_MAX:
                    DistributedSlackOnGenerationOuterLoop outerLoop = new DistributedSlackOnGenerationOuterLoop(openLoadFlowParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());
                    List<AbstractDistributedSlackOuterLoop.ParticipatingElement<LfGenerator>> participatingElements = outerLoop.getParticipatingElements(lfNetwork);
                    outerLoop.normalizeParticipationFactors(participatingElements, "generator");
                    participatingElements.forEach(participatingElement -> contributionList.add(Pair.of(participatingElement.element.getId(), participatingElement.factor)));
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown balance type mode: " + loadFlowParameters.getBalanceType());
            }
        }
        return contributionList;
    }

    /**
     * Create a map containing sensibility values, indexed first by FunctionId, then by factorId
     * @param sensitivityValues
     * @param remainder
     * @return
     */
    public static Map<String, Map<String, SensitivityValue>> convertSensitivityValuesToMap(List<SensitivityValue> sensitivityValues, List<SensitivityValue> remainder) {
        Map<String, Map<String, SensitivityValue>> sensitivityValuesMap = new HashMap<>();
        for (SensitivityValue sensitivityValue : sensitivityValues) {
            SensitivityFactor factor = sensitivityValue.getFactor();
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                Map<String, SensitivityValue> branchSentivityMap = sensitivityValuesMap.computeIfAbsent(factor.getFunction().getId(), branch -> new HashMap<>());
                branchSentivityMap.put(factor.getVariable().getId(), sensitivityValue);
            } else {
                remainder.add(sensitivityValue); // todo: if this is not an injection, I do not know how to do
            }
        }
        return sensitivityValuesMap;
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

        // create DC equation system
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(), new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = lfParameters.isDistributedSlack()
                ? indexFactorsByVariableConfig(network, getDistributedFactors(lfNetwork, factors)) // if the slack is distributed we need to compute sensi for all generators
                : indexFactorsByVariableConfig(network, factors);

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
        List<SensitivityValue> sensitivityValues = calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states);

        if (!lfParameters.isDistributedSlack()) {
            return sensitivityValues;
        }

        // If the slack is distributed, we need to compute compensated sensitivities
        // Our sensitivity Values contains data for all generators and all monitored lines
        List<SensitivityValue> compensedSensitivities = new ArrayList<>();

        // map indexed by functionid, then variableid
        Map<String, Map<String, SensitivityValue>> sensitivityValuesMap = convertSensitivityValuesToMap(sensitivityValues, compensedSensitivities);

        List<Pair<String, Number>> participatingElements = getParticipatingElements(lfNetwork, lfParameters, lfParametersExt);

        Map<String, Number> compensation = new HashMap<>();
        for (String lfBranchId : sensitivityValuesMap.keySet()) {
            Map<String, SensitivityValue> sensitivitiesForBranch = sensitivityValuesMap.get(lfBranchId);
            double sum = participatingElements.stream()
                .mapToDouble(element -> element.getRight().doubleValue() * sensitivitiesForBranch.get(element.getLeft()).getValue())
                .sum();
            compensation.put(lfBranchId, sum);
        }

        // compute distributed sensitivities
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;

                SensitivityValue uncompensedValue = sensitivityValuesMap.get(injectionFactor.getFunction().getBranchId())
                        .get(injectionFactor.getVariable().getId());
                compensedSensitivities.add(new SensitivityValue(
                        uncompensedValue.getFactor(),
                        uncompensedValue.getValue() - compensation.get(injectionFactor.getFunction().getBranchId()).doubleValue(),
                        uncompensedValue.getVariableReference(),
                        uncompensedValue.getFunctionReference()
                ));
            }
        }

        return compensedSensitivities;
    }
}
