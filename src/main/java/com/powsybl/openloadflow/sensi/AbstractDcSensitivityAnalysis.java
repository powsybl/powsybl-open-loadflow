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
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;

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

    static class SensitivityFactorWrapped {
        // Wrap factors in another class to have instant access to their branch, and their equation term
        private final SensitivityFactor factor;

        private final LfBranch functionBranch;

        private final ClosedBranchSide1DcFlowEquationTerm equationTerm;

        public SensitivityFactorWrapped(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem) {
            this.factor = factor;
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                functionBranch = lfNetwork.getBranchById(((BranchFlowPerInjectionIncrease) factor).getFunction().getBranchId());

            } else if (factor instanceof  BranchFlowPerPSTAngle) {
                functionBranch = lfNetwork.getBranchById(((BranchFlowPerPSTAngle) factor).getFunction().getBranchId());
            } else {
                throw new UnsupportedOperationException("Only factors of type BranchFlowPerInjectionIncrease and BranchFlowPerPSTAngle are supported");
            }
            equationTerm = equationSystem.getEquationTerm(SubjectType.BRANCH, functionBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        public SensitivityFactor getFactor() {
            return factor;
        }

        public LfBranch getFunctionBranch() {
            return functionBranch;
        }

        public ClosedBranchSide1DcFlowEquationTerm getEquationTerm() {
            return equationTerm;
        }
    }

    static class SensitivityFactorGroup {

        private final String id;

        private final List<SensitivityFactorWrapped> factors = new ArrayList<>();

        private int index = -1;

        SensitivityFactorGroup(String id) {
            this.id = Objects.requireNonNull(id);
        }

        String getId() {
            return id;
        }

        List<SensitivityFactorWrapped> getFactors() {
            return factors;
        }

        int getIndex() {
            return index;
        }

        void setIndex(int index) {
            this.index = index;
        }

        void addFactor(SensitivityFactorWrapped factor) {
            factors.add(factor);
        }

        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            throw new NotImplementedException("fillRhs method must be implemented in subclasses");
        }
    }

    static class PhaseTapFactorGroup extends SensitivityFactorGroup {
        PhaseTapFactorGroup(final String id) {
            super(id);
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            LfBranch lfBranch = lfNetwork.getBranchById(getId());
            Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
            rhs.set(a1.getColumn(), getIndex(), Math.toRadians(1d));
        }
    }

    static class InjectionFactorGroup extends SensitivityFactorGroup {
        Map<String, Double> busInjectionById;

        InjectionFactorGroup(final String id) {
            super(id);
        }

        public void setBusInjectionById(final Map<String, Double> busInjectionById) {
            this.busInjectionById = busInjectionById;
        }

        @Override
        void fillRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Matrix rhs) {
            for (Map.Entry<String, Double> busAndInjection : busInjectionById.entrySet()) {
                LfBus lfBus = lfNetwork.getBusById(busAndInjection.getKey());
                int column;
                if (lfBus.isSlack()) {
                    Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_PHI).orElseThrow(IllegalStateException::new);
                    column = p.getColumn();
                } else {
                    Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                    column = p.getColumn();
                }
                rhs.set(column, getIndex(), busAndInjection.getValue() / PerUnit.SB);
            }
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

    protected void fillRhsSensitivityVariable(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorGroups, Matrix rhs) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            factorGroup.fillRhs(lfNetwork, equationSystem, rhs);
        }
    }

    protected List<SensitivityFactorGroup> createFactorGroups(Network network, List<SensitivityFactorWrapped> factors) {
        Map<String, SensitivityFactorGroup> groupIndexedById = new HashMap<>(factors.size());
        // index factors by variable config
        for (SensitivityFactorWrapped factor : factors) {
            if (factor.getFactor() instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor.getFactor();
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                Bus bus = injection.getTerminal().getBusView().getBus();
                // skip disconnected injections
                if (bus != null) {
                    groupIndexedById.computeIfAbsent(bus.getId(), id -> new InjectionFactorGroup(bus.getId())).addFactor(factor);
                }
            } else if (factor.getFactor() instanceof BranchFlowPerPSTAngle) {
                BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor.getFactor();
                String phaseTapChangerHolderId = pstAngleFactor.getVariable().getPhaseTapChangerHolderId();
                TwoWindingsTransformer twt = network.getTwoWindingsTransformer(phaseTapChangerHolderId);
                if (twt == null) {
                    throw new PowsyblException("Phase shifter '" + phaseTapChangerHolderId + "' not found");
                }
                groupIndexedById.computeIfAbsent(phaseTapChangerHolderId, k -> new PhaseTapFactorGroup(phaseTapChangerHolderId)).addFactor(factor);
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

    protected void computeFactorsInjection(Function<String, Map<String, Double>> getParticipationForBus, List<SensitivityFactorGroup> factorsGroups, Map<SensitivityFactorWrapped, Double> predefinedResult) {
        // compute the corresponding injection (with participation) for each factor
        for (SensitivityFactorGroup factorGroup : factorsGroups) {
            if (factorGroup instanceof InjectionFactorGroup) {
                InjectionFactorGroup injectionGroup = (InjectionFactorGroup) factorGroup;
                Map<String, Double> participationFactorByBus = getParticipationForBus.apply(factorGroup.getId());
                if (participationFactorByBus == null) {
                    factorGroup.getFactors().forEach(factor -> predefinedResult.put(factor, Double.NaN));
                    participationFactorByBus = new HashMap<>();
                }

                Map<String, Double> busInjectionById = new HashMap<>(participationFactorByBus);

                // add 1 where we are making the injection
                busInjectionById.put(factorGroup.getId(), busInjectionById.getOrDefault(factorGroup.getId(), 0d) + 1);

                injectionGroup.setBusInjectionById(busInjectionById);
            }
        }
    }

    protected void computeFactorsInjection(Map<String, Double> participationMap, List<SensitivityFactorGroup> factorsGroups, Map<SensitivityFactorWrapped, Double> predefinedResult) {
        computeFactorsInjection(x -> participationMap, factorsGroups, predefinedResult);
    }

    protected LfBus getParticipatingElementBus(ParticipatingElement participatingElement) {
        if (participatingElement.getElement() instanceof LfGenerator) {
            return ((LfGenerator) participatingElement.getElement()).getBus();
        } else if (participatingElement.getElement() instanceof LfBus) {
            return (LfBus) participatingElement.getElement();
        } else {
            throw new UnsupportedOperationException("Unsupported participating element");
        }
    }

    protected List<ParticipatingElement> getParticipatingElements(LfNetwork lfNetwork, LoadFlowParameters loadFlowParameters) {
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
        return participatingElements;
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

    public void checkContingencies(LfNetwork lfNetwork, List<Contingency> contingencies) {
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
