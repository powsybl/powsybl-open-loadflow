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
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.GenerationActionPowerDistributionStep;
import com.powsybl.openloadflow.network.util.LoadActivePowerDistributionStep;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public abstract class AbstractDcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    protected AbstractDcSensitivityAnalysis(final MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    protected static class LazyConnectivity {
        private GraphDecrementalConnectivity<LfBus> connectivity = null;
        private LfNetwork network;

        public LazyConnectivity(LfNetwork lfNetwork) {
            network = lfNetwork;
        }

        public GraphDecrementalConnectivity<LfBus> getConnectivity() {
            if (connectivity != null) {
                return connectivity;
            }
            connectivity = new NaiveGraphDecrementalConnectivity<>(LfBus::getNum); // FIXME: use EvenShiloach
            for (LfBus bus : network.getBuses()) {
                connectivity.addVertex(bus);
            }
            for (LfBranch branch : network.getBranches()) {
                connectivity.addEdge(branch.getBus1(), branch.getBus2());
            }
            return connectivity;
        }
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

    protected SensitivityValue createBranchSensitivityValue(LfBranch lfBranch, EquationSystem equationSystem,
                                                          SensitivityFactor<?, ?> factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix states, Double functionReference) {
        ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        double value = p1.isActive() ? p1.calculate(states, factorGroup.getIndex()) : 0d;
        return new SensitivityValue(factor, value * PerUnit.SB, functionReference, 0);
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
                    String branchId = injectionFactor.getFunction().getBranchId();
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork.getBranchById(branchId), equationSystem,
                            factor, factorGroup, states, functionReferenceByBranch.get(branchId)));
                } else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    String branchId = pstAngleFactor.getFunction().getBranchId();
                    sensitivityValues.add(createBranchSensitivityValue(lfNetwork.getBranchById(branchId), equationSystem,
                            factor, factorGroup, states, functionReferenceByBranch.get(branchId)));
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
                int column;
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
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
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

    private Map<String, Double> getParticipationFactorByBus(LfNetwork lfNetwork, String busId, GraphDecrementalConnectivity<LfBus> connectivity, LoadFlowParameters loadFlowParameters) {

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
            if (connectivity != null) {
                int connectedComponentNumber = connectivity.getComponentNumber(lfNetwork.getBusById(busId));
                participatingElements = participatingElements.stream().filter(
                    participatingElement -> connectivity.getComponentNumber(lfNetwork.getBusById(getParticipatingElementBusId(participatingElement)))
                            == connectedComponentNumber).collect(Collectors.toList());
            }
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
            } else {
                throw new PowsyblException("Only sensitivity factors of BranchFlowPerInjectionIncrease and BranchFlowPerPSTAngle are yet supported");
            }
            if (monitoredBranch == null) {
                throw new PowsyblException("Monitored branch " + factor.getFunction().getId() + " not found in the network");
            }
        }
    }

    public void checkContingencies(Network network, LfNetwork lfNetwork, List<Contingency> contingencies) {
        for (Contingency contingency : contingencies) {
            for (ContingencyElement contingencyElement : contingency.getElements()) {
                if (!contingencyElement.getType().equals(ContingencyElementType.BRANCH)) {
                    throw new UnsupportedOperationException("The only admitted contingencies are the one on branches");
                }
                LfBranch lfBranch = lfNetwork.getBranchById(contingencyElement.getId());
                if (lfBranch == null) {
                    throw new PowsyblException("The contingency on the branch " + contingencyElement.getId() + " cannot be found in the network");
                }
            }
        }
    }

    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, List<Contingency> contingencies,
                                                                                     LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        throw new UnsupportedOperationException("Cannot analyse with abstract analysis, choose between slow or fast !");
    }
}
