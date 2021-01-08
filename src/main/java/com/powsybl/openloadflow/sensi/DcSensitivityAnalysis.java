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
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
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
import java.util.stream.Collectors;


/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-6;

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

        private final String functionBranchId;

        private final ClosedBranchSide1DcFlowEquationTerm equationTerm;

        private Double predefinedResult = null;

        private Double functionReference;

        public SensitivityFactorWrapped(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem) {
            this.factor = factor;
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                functionBranch = lfNetwork.getBranchById(((BranchFlowPerInjectionIncrease) factor).getFunction().getBranchId());
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                functionBranch = lfNetwork.getBranchById(((BranchFlowPerPSTAngle) factor).getFunction().getBranchId());
            } else {
                throw new UnsupportedOperationException("Only factors of type BranchFlowPerInjectionIncrease and BranchFlowPerPSTAngle are supported");
            }
            functionBranchId = functionBranch.getId();
            equationTerm = equationSystem.getEquationTerm(SubjectType.BRANCH, functionBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
            functionReference = 0d;
        }

        public static SensitivityFactorWrapped create(SensitivityFactor factor, Network network, LfNetwork lfNetwork, EquationSystem equationSystem) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                return new BranchFlowPerInjectionIncreaseWrapped(factor, network, lfNetwork, equationSystem);
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                return new BranchFlowPerPSTAngleWrapped(factor, lfNetwork, equationSystem);
            }
            return null;
        }

        public SensitivityFactor getFactor() {
            return factor;
        }

        public LfBranch getFunctionBranch() {
            return functionBranch;
        }

        public String getFunctionBranchId() {
            return functionBranchId;
        }

        public ClosedBranchSide1DcFlowEquationTerm getEquationTerm() {
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

        public boolean areVariableAndFunctionDisconnected(GraphDecrementalConnectivity<LfBus> connectivity) {
            throw new NotImplementedException("areVariableAndFunctionDisconnected should have an override");
        }
    }

    static class BranchFlowPerInjectionIncreaseWrapped extends SensitivityFactorWrapped {
        private final LfBus injectionLfBus;

        BranchFlowPerInjectionIncreaseWrapped(SensitivityFactor factor, Network network, LfNetwork lfNetwork, EquationSystem equationSystem) {
            super(factor, lfNetwork, equationSystem);
            injectionLfBus = getInjectionBus(network, lfNetwork, (BranchFlowPerInjectionIncrease) factor);
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            return connectivity.getComponentNumber(injectionLfBus) != connectivity.getComponentNumber(getFunctionBranch().getBus1())
                   || connectivity.getComponentNumber(injectionLfBus) != connectivity.getComponentNumber(getFunctionBranch().getBus2());
        }

        public LfBus getInjectionLfBus() {
            return injectionLfBus;
        }
    }

    static class BranchFlowPerPSTAngleWrapped extends SensitivityFactorWrapped {
        private final LfBranch transformerBranch;

        BranchFlowPerPSTAngleWrapped(SensitivityFactor factor, LfNetwork lfNetwork, EquationSystem equationSystem) {
            super(factor, lfNetwork, equationSystem);
            transformerBranch = getPhaseChangerBranch(lfNetwork, (BranchFlowPerPSTAngle) factor);
        }

        @Override
        public boolean areVariableAndFunctionDisconnected(final GraphDecrementalConnectivity<LfBus> connectivity) {
            return connectivity.getComponentNumber(transformerBranch.getBus1()) != connectivity.getComponentNumber(getFunctionBranch().getBus1())
                   || connectivity.getComponentNumber(transformerBranch.getBus1()) != connectivity.getComponentNumber(getFunctionBranch().getBus2());
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

    static class ComputedContingencyElement {
        private int contingencyIndex = -1; // local index of the element inside of a contingency : index of the element in the matrix used in the setAlphas method
        private double alpha = Double.NaN;
        private final ContingencyElement element;
        private final LfBranch lfBranch;
        private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

        public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem equationSystem) {
            this.element = element;
            lfBranch = lfNetwork.getBranchById(element.getId());
            branchEquation = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        public int getContingencyIndex() {
            return contingencyIndex;
        }

        public void setContingencyIndex(final int index) {
            this.contingencyIndex = index;
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

        public LfBranch getLfBranch() {
            return lfBranch;
        }

        public ClosedBranchSide1DcFlowEquationTerm getBranchEquation() {
            return branchEquation;
        }

        public static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index++);
            }
        }

    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    protected void setFunctionReferenceOnFactors(List<LfNetwork> lfNetworks, List<SensitivityFactorWrapped> factors, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        DcLoadFlowParameters dcLoadFlowParameters = new DcLoadFlowParameters(lfParametersExt.getSlackBusSelector(), matrixFactory,
                true, lfParametersExt.isDcUseTransformerRatio(), lfParameters.isDistributedSlack(),  lfParameters.getBalanceType());
        DcLoadFlowResult dcLoadFlowResult = new DcLoadFlowEngine(lfNetworks, dcLoadFlowParameters).run();
        for (SensitivityFactorWrapped factor : factors) {
            factor.setFunctionReference(dcLoadFlowResult.getNetwork().getBranchById(factor.getFunctionBranchId()).getP1() * PerUnit.SB);
        }
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
            if (factor instanceof BranchFlowPerInjectionIncreaseWrapped) {
                LfBus lfBus = ((BranchFlowPerInjectionIncreaseWrapped) factor).getInjectionLfBus();
                // skip disconnected injections
                if (lfBus != null) {
                    groupIndexedById.computeIfAbsent(lfBus.getId(), id -> new InjectionFactorGroup(lfBus.getId())).addFactor(factor);
                }
            } else if (factor instanceof BranchFlowPerPSTAngleWrapped) {
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

    protected void computeFactorsInjection(Function<String, Map<String, Double>> getParticipationForBus, List<SensitivityFactorGroup> factorsGroups) {
        // compute the corresponding injection (with participation) for each factor
        for (SensitivityFactorGroup factorGroup : factorsGroups) {
            if (factorGroup instanceof InjectionFactorGroup) {
                InjectionFactorGroup injectionGroup = (InjectionFactorGroup) factorGroup;
                Map<String, Double> participationFactorByBus = getParticipationForBus.apply(factorGroup.getId());
                if (participationFactorByBus == null) {
                    factorGroup.getFactors().forEach(factor -> factor.setPredefinedResult(Double.NaN));
                    participationFactorByBus = new HashMap<>();
                }

                Map<String, Double> busInjectionById = new HashMap<>(participationFactorByBus);

                // add 1 where we are making the injection
                busInjectionById.put(factorGroup.getId(), busInjectionById.getOrDefault(factorGroup.getId(), 0d) + 1);

                injectionGroup.setBusInjectionById(busInjectionById);
            }
        }
    }

    protected void computeFactorsInjection(Map<String, Double> participationMap, List<SensitivityFactorGroup> factorsGroups) {
        computeFactorsInjection(x -> participationMap, factorsGroups);
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

    private SensitivityValue createBranchSensitivityValue(SensitivityFactorWrapped factor, SensitivityFactorGroup factorGroup,
                                                          DenseMatrix factorStates, DenseMatrix contingenciesStates, List<ComputedContingencyElement> contingencyElements) {
        double value;
        ClosedBranchSide1DcFlowEquationTerm p1 = factor.getEquationTerm();
        if (factor.getPredefinedResult() != null) {
            value = factor.getPredefinedResult();
        } else {
            value = p1.calculate(factorStates, factorGroup.getIndex());
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                if (contingencyElement.getElement().getId().equals(factor.getFunctionBranchId()) || contingencyElement.getElement().getId().equals(factor.getFactor().getVariable().getId())) {
                    // the sensitivity on a removed branch is 0, the sensitivity if the variable was a removed branch is 0
                    value = 0d;
                    break;
                }
                value = value + contingencyElement.getAlpha() * p1.calculate(contingenciesStates, contingencyElement.getContingencyIndex());
            }
        }
        return new SensitivityValue(factor.getFactor(), value * PerUnit.SB, factor.getFunctionReference(), 0);
    }

    protected List<SensitivityValue> calculateSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorStates,
                                                                DenseMatrix contingenciesStates, List<ComputedContingencyElement> contingencyElements) {
        List<SensitivityValue> sensitivityValuesContingencies = new ArrayList<>(factorGroups.stream().mapToInt(group -> group.getFactors().size()).sum());
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            setAlphas(contingencyElements, factorGroup, factorStates, contingenciesStates);
            for (SensitivityFactorWrapped factor : factorGroup.getFactors()) {
                sensitivityValuesContingencies.add(createBranchSensitivityValue(factor, factorGroup, factorStates, contingenciesStates, contingencyElements));
            }
        }
        return sensitivityValuesContingencies;
    }

    private void setAlphas(List<ComputedContingencyElement> contingencyElements, SensitivityFactorGroup sensitivityFactorGroup, DenseMatrix factorStates, DenseMatrix contingencyStates) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.get(0);
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getBranchEquation();
            // we solve a*alpha = b
            double a = lfBranch.getPiModel().getX() / PerUnit.SB - (contingencyStates.get(p1.getVariables().get(0).getRow(), element.getContingencyIndex())
                             - contingencyStates.get(p1.getVariables().get(1).getRow(), element.getContingencyIndex()));
            double b = factorStates.get(p1.getVariables().get(0).getRow(), sensitivityFactorGroup.getIndex())
                       - factorStates.get(p1.getVariables().get(1).getRow(), sensitivityFactorGroup.getIndex());
            element.setAlpha(b / a);
        } else {
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getBranchEquation();
                rhs.set(element.getContingencyIndex(), 0, factorStates.get(p1.getVariables().get(0).getRow(), sensitivityFactorGroup.getIndex())
                                                          - factorStates.get(p1.getVariables().get(1).getRow(), sensitivityFactorGroup.getIndex())
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = lfBranch.getPiModel().getX() / PerUnit.SB;
                    }
                    value = value - (contingencyStates.get(p1.getVariables().get(0).getRow(), element2.getContingencyIndex())
                                     - contingencyStates.get(p1.getVariables().get(1).getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getContingencyIndex(), element2.getContingencyIndex(), value);
                }
            }
            LUDecomposition lu = matrix.decomposeLU();
            lu.solve(rhs); // rhs now contains state matrix
            contingencyElements.forEach(element -> element.setAlpha(rhs.get(element.getContingencyIndex(), 0)));
        }
    }

    private List<Set<ComputedContingencyElement>> getElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingencyStates,
                                                                             List<ComputedContingencyElement> contingencyElements,
                                                                             EquationSystem equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity
        // We consider a +1 -1 on a line, and we observe the sensivity of this injection on the other contingency elements
        // If the sum of the sentivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // Thus, losing these lines will lose the connectivity
        List<Set<ComputedContingencyElement>> responsibleAssociations = new LinkedList<>();
        for (ComputedContingencyElement element : contingencyElements) {
            Set<ComputedContingencyElement> responsibleElements = new HashSet<>();
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculate(contingencyStates, element.getContingencyIndex()));
                if (value > CONNECTIVITY_LOSS_THRESHOLD) {
                    responsibleElements.add(element2);
                }
                sum += value;
            }
            if (Math.abs(sum * PerUnit.SB - 1d) < CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                responsibleAssociations.add(responsibleElements);
            }
        }
        return responsibleAssociations;
    }

    protected void fillRhsContingency(final LfNetwork lfNetwork, final EquationSystem equationSystem, final Map<String, ComputedContingencyElement> contingencyElements, final Matrix rhs) {
        for (ComputedContingencyElement element : contingencyElements.values()) {
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                continue;
            }
            LfBus bus1 = lfBranch.getBus1();
            LfBus bus2 = lfBranch.getBus2();
            if (bus1.isSlack()) {
                Equation p = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), -1 / PerUnit.SB);
            } else if (bus2.isSlack()) {
                Equation p = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), 1 / PerUnit.SB);
            } else {
                Equation p1 = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                Equation p2 = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p1.getColumn(), element.getContingencyIndex(), 1 / PerUnit.SB);
                rhs.set(p2.getColumn(), element.getContingencyIndex(), -1 / PerUnit.SB);
            }
        }
    }

    protected DenseMatrix initRhs(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorsGroups, Map<String, ComputedContingencyElement> contingencyElements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsGroups.size() + contingencyElements.values().size());
        fillRhsSensitivityVariable(lfNetwork, equationSystem, factorsGroups, rhs);
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    protected DenseMatrix initFactorsRhs(LfNetwork lfNetwork, EquationSystem equationSystem, List<SensitivityFactorGroup> factorsGroups) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsGroups.size());
        fillRhsSensitivityVariable(lfNetwork, equationSystem, factorsGroups, rhs);
        return rhs;
    }

    protected DenseMatrix initContingencyRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<String, ComputedContingencyElement> contingencyElements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), contingencyElements.size());
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    private void lookForConnectivityLoss(LfNetwork lfNetwork, DenseMatrix states, List<Contingency> contingencies, Map<String, ComputedContingencyElement> contingenciesElements, EquationSystem equationSystem, Collection<Contingency> straightforwardContingencies,  Map<List<Set<ComputedContingencyElement>>, List<Contingency>> losingConnectivityContingency) {
        for (Contingency contingency : contingencies) {
            List<Set<ComputedContingencyElement>> groupOfElementsBreakingConnectivity = getElementsBreakingConnectivity(lfNetwork, states, contingency.getElements().stream().map(element -> contingenciesElements.get(element.getId())).collect(Collectors.toList()), equationSystem);
            if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
                straightforwardContingencies.add(contingency);
            } else if (groupOfElementsBreakingConnectivity.stream().mapToInt(Set::size).max().getAsInt() > 1) {
                throw new PowsyblException("The contingency " + contingency.getId() + " breaks the connectivity on more than 1 branch.");
            } else {
                losingConnectivityContingency.computeIfAbsent(groupOfElementsBreakingConnectivity, key -> new LinkedList<>()).add(contingency);
            }
        }
    }

    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors,
                                                                                     List<Contingency> contingencies, LoadFlowParameters lfParameters,
                                                                                     OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);

        // create the network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkSensitivities(network, lfNetwork, factors);
        LazyConnectivity connectivity = new LazyConnectivity(lfNetwork);

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // we wrap the factor into a class that allows us to have access to their branch and EquationTerm instantly
        List<SensitivityFactorWrapped> factorsWrapped = factors.stream().map(factor -> SensitivityFactorWrapped.create(factor, network, lfNetwork, equationSystem)).collect(Collectors.toList());

        // run DC load
        setFunctionReferenceOnFactors(lfNetworks, factorsWrapped, lfParameters, lfParametersExt);

        // index factors by variable configuration to compute minimal number of states
        List<SensitivityFactorGroup> factorsGroups = createFactorGroups(network, factorsWrapped);

        if (factorsGroups.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        // Compute the partipation for every factor
        List<ParticipatingElement> participatingElements = null;
        Map<String, Double> participationPerBus;
        if (lfParameters.isDistributedSlack()) {
            participatingElements = getParticipatingElements(lfNetwork, lfParameters);
            participationPerBus = participatingElements.stream().collect(Collectors.toMap(
                element -> getParticipatingElementBus(element).getId(),
                element -> -element.getFactor(),
                Double::sum
            ));
        } else {
            participationPerBus = Collections.singletonMap(lfNetwork.getSlackBus().getId(), -1d);
        }
        // compute the participation for each injection factor (+1 on the injection, and then -participation on all
        // elements participating to load balancing)
        computeFactorsInjection(participationPerBus, factorsGroups);

        Map<String, ComputedContingencyElement> contingenciesElements = contingencies.stream()
                                                                                    .flatMap(contingency -> contingency.getElements().stream())
                                                                                    .map(contingencyElement -> new ComputedContingencyElement(contingencyElement, lfNetwork, equationSystem))
                                                                                    .collect(Collectors.toMap(
                                                                                        computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                                                                        computedContingencyElement -> computedContingencyElement
                                                                                    ));

        ComputedContingencyElement.setContingencyIndexes(contingenciesElements.values());

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);
        LUDecomposition jlu = j.decomposeLU();
        // compute pre-contingency sensitivity values + the states for making +1-1 on the contingencies
        DenseMatrix factorsStates = initFactorsRhs(lfNetwork, equationSystem, factorsGroups); // this is the rhs for the moment
        DenseMatrix contingenciesStates = initContingencyRhs(lfNetwork, equationSystem, contingenciesElements);
        jlu.solveTransposed(factorsStates);
        jlu.solveTransposed(contingenciesStates);

        //jlu.solveTransposed(factorsStates); // states contains angles for the sensitivity factors, but also for the +1-1 related to contingencies
        //jlu.solveTransposed(contingenciesStates);
        // sensitivities without contingency
        List<SensitivityValue> sensitivityValues = calculateSensitivityValues(factorsGroups,
                factorsStates, contingenciesStates, Collections.emptyList());

        Collection<Contingency> straightforwardContingencies = new LinkedList<>();
        Map<List<Set<ComputedContingencyElement>>, List<Contingency>> losingConnectivityContingency = new HashMap<>();
        // We will index contingencies by the list of "problems" contained in this contingency
        // For example, For example, if in the network, loosing line L1 breaks connectivity, and loosing L2 and L3 together breaks connectivity,
        // the responsible associations would be: [{L1}, {L2, L3}]

        lookForConnectivityLoss(lfNetwork, contingenciesStates, contingencies, contingenciesElements, equationSystem, straightforwardContingencies, losingConnectivityContingency);

        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();
        // compute the contingencies with no loss of connectivity
        for (Contingency contingency : straightforwardContingencies) {
            contingenciesValue.put(contingency.getId(), calculateSensitivityValues(factorsGroups,
                    factorsStates, contingenciesStates, contingency.getElements().stream().map(element -> contingenciesElements.get(element.getId())).collect(Collectors.toList())
            ));
        }

        for (Map.Entry<List<Set<ComputedContingencyElement>>, List<Contingency>> contingencyEntry : losingConnectivityContingency.entrySet()) {
            factorsWrapped.forEach(factor -> factor.setPredefinedResult(null));
            contingencyEntry.getKey().stream().flatMap(Set::stream)
                            .map(ComputedContingencyElement::getElement)
                            .map(element -> lfNetwork.getBranchById(element.getId()))
                            .forEach(lfBranch -> connectivity.getConnectivity().cut(lfBranch.getBus1(), lfBranch.getBus2()));

            if (lfParameters.isDistributedSlack()) {
                Map<Integer, List<ParticipatingElement>> participatingElementsPerCc = new HashMap<>();
                participatingElements.forEach(element -> participatingElementsPerCc.computeIfAbsent(connectivity.getConnectivity().getComponentNumber(getParticipatingElementBus(element)), key -> new LinkedList<>())
                                                                                   .add(element));
                participatingElementsPerCc.values().forEach(ccElements -> ParticipatingElement.normalizeParticipationFactors(ccElements, "bus"));
                Map<Integer, Map<String, Double>> participationPerCc = participatingElementsPerCc.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream().collect(Collectors.toMap(
                        element -> getParticipatingElementBus(element).getId(),
                        element -> -element.getFactor(),
                        Double::sum
                    ))
                ));

                // compute the participation for each injection factor (+1 on the injection, and then -participation on all
                // elements participating to load balancing, that are still in the connected component of the factor's variable)
                Function<String, Map<String, Double>> getParticipationForBus = busId -> participationPerCc.get(connectivity.getConnectivity().getComponentNumber(lfNetwork.getBusById(busId)));
                computeFactorsInjection(getParticipationForBus, factorsGroups);
                factorsStates.reset();
                fillRhsSensitivityVariable(lfNetwork, equationSystem, factorsGroups, factorsStates);
                jlu.solveTransposed(factorsStates);
            }

            for (SensitivityFactorWrapped factor : factorsWrapped) {
                // Check if the factor function and variable are in different connected components
                if (factor.areVariableAndFunctionDisconnected(connectivity.getConnectivity()))  {
                    factor.setPredefinedResult(0d);
                }
            }

            Set<String> elementIdLosingConnectivity = contingencyEntry.getKey().stream().flatMap(Set::stream).map(contingencyElement -> contingencyElement.getElement().getId()).collect(Collectors.toSet());

            for (Contingency contingency : contingencyEntry.getValue()) {
                contingenciesValue.put(contingency.getId(), calculateSensitivityValues(factorsGroups,
                        factorsStates, contingenciesStates, contingency.getElements().stream().filter(element -> !elementIdLosingConnectivity.contains(element.getId())).map(element -> contingenciesElements.get(element.getId())).collect(Collectors.toList())
                ));
            }

            connectivity.getConnectivity().reset();
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
