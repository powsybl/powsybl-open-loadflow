/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
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
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public abstract class AbstractDcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    protected boolean throwExceptionIfNullInjection() {
        return true;
    }

    protected boolean computeSensitivityOnContingency() {
        return false;
    }

    protected AbstractDcSensitivityAnalysis(final MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    protected static class SensitivityVariableConfiguration {

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

    protected Map<String, Double> getFunctionReferenceByBranch(List<LfNetwork> lfNetworks, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        Map<String, Double> functionReferenceByBranch = new HashMap<>();
        DcLoadFlowParameters dcLoadFlowParameters = new DcLoadFlowParameters(lfParametersExt.getSlackBusSelector(), matrixFactory,
                true, lfParametersExt.isDcUseTransformerRatio(), lfParameters.isDistributedSlack(),  lfParameters.getBalanceType());
        DcLoadFlowResult dcLoadFlowResult = new DcLoadFlowEngine(lfNetworks, dcLoadFlowParameters).run();
        for (LfBranch branch : dcLoadFlowResult.getNetwork().getBranches()) {
            functionReferenceByBranch.put(branch.getId(), branch.getP1() * PerUnit.SB);
        }
        return functionReferenceByBranch;
    }

    protected SensitivityValue createBranchSensitivityValue(LfNetwork lfNetwork, EquationSystem equationSystem, String branchId,
                                                          SensitivityFactor<?, ?> factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, Map<String, Double> functionReferenceByBranch) {
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        if (lfBranch == null) {
            if (computeSensitivityOnContingency()) {
                return new SensitivityValue(factor, 0d, functionReferenceByBranch.get(branchId), 0);
            } else {
                throw new PowsyblException("Branch '" + branchId + "' not found");
            }
        }

        ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        double value = p1.isActive()
                ? p1.calculate(states, factorGroup.getIndex())
                : 0d;

        return new SensitivityValue(factor, value * PerUnit.SB, functionReferenceByBranch.get(branchId), 0);
    }

    protected List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
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

    protected DenseMatrix initRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsByVarConfig.size());
        fillRhs(lfNetwork, equationSystem, factorsByVarConfig, rhs);
        return rhs;
    }

    protected void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig, Matrix rhs) {
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityVariableConfiguration configuration = e.getKey();
            SensitivityFactorGroup factorGroup = e.getValue();
            for (Map.Entry<String, Double> busAndInjection : configuration.busInjectionById().entrySet()) {
                LfBus lfBus = lfNetwork.getBusById(busAndInjection.getKey());
                int column = 0;
                if (lfBus.isSlack()) {
                    Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_PHI).orElseThrow(IllegalStateException::new);
                    column = p.getColumn();
                } else {
                    Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    column = p.getColumn();
                }
                rhs.set(column, factorGroup.getIndex(), busAndInjection.getValue() / PerUnit.SB);
            }
            for (String phaseTapChangerHolderId : configuration.getPhaseTapChangerHolderIds()) {
                LfBranch lfBranch = lfNetwork.getBranchById(phaseTapChangerHolderId);
                Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
                rhs.set(a1.getColumn(), factorGroup.getIndex(), Math.toRadians(1d));
            }
        }
    }

    // todo: I think we just need to rethink the configuration now
    protected Map<SensitivityVariableConfiguration, SensitivityFactorGroup> indexFactorsByVariableConfig(Network network, GraphDecrementalConnectivity<LfBus> connectivity, List<SensitivityFactor> factors, LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters) {
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = new LinkedHashMap<>(factors.size());

        // index factors by variable config
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId(), throwExceptionIfNullInjection());
                if (injection == null) {
                    LOGGER.debug("Injection {} not found in the network", injectionFactor.getVariable().getInjectionId());
                    // FIXME
                    continue;
                }
                Bus bus = injection.getTerminal().getBusView().getBus();
                // skip disconnected injections
                if (bus != null) {
                    // todo: the participation factor only changes when the connectivity is lost, we can optimize this line
                    Map<String, Double> participationFactorByBus = getParticipationFactorByBus(lfNetwork, bus.getId(), connectivity, loadFlowParameters); // empty if slack is not distributed
                    participationFactorByBus.replaceAll((key, value) -> -value); // the slack distribution on a bus is the opposite of its participation factor

                    Map<String, Double> busInjectionById = new HashMap<>(participationFactorByBus);
                    // add 1 where we are making the injection
                    busInjectionById.put(bus.getId(), busInjectionById.getOrDefault(bus.getId(), 0d) + 1);
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
    private Map<String, Double> getParticipationFactorByBus(LfNetwork lfNetwork, String busId, GraphDecrementalConnectivity<LfBus> connectivity, LoadFlowParameters loadFlowParameters) {

        Map<String, Double> participationFactorByBusMap = new HashMap<>();
        int connectedComponentNumber = connectivity.getComponentNumber(lfNetwork.getBusById(busId));

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
            participatingElements = participatingElements.stream().filter(
                participatingElement -> connectivity.getComponentNumber(lfNetwork.getBusById(getParticipatingElementBusId(participatingElement))) == connectedComponentNumber
            ).collect(Collectors.toList());
            ParticipatingElement.normalizeParticipationFactors(participatingElements, "bus");

            participationFactorByBusMap = participatingElements.stream().collect(Collectors.toMap(
                this::getParticipatingElementBusId,
                ParticipatingElement::getFactor,
                Double::sum
            ));
        } else {
            participationFactorByBusMap.putIfAbsent(lfNetwork.getSlackBus().getId(), 1d);
        }
        return participationFactorByBusMap;
    }

    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, List<Contingency> contingencies,
                                                                                     LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                                                     OpenSensitivityAnalysisParameters sensiParametersExt) {
        throw new UnsupportedOperationException("Cannot analyse with abstract analysis, choose between slow or fast !");
        //FIXME
    }
}
