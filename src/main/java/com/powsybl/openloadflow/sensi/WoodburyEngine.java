package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityResultWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ObjDoubleConsumer;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;

public class WoodburyEngine {

    protected static final Logger LOGGER = LoggerFactory.getLogger(WoodburyEngine.class);
    private static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-7;

    // input of the engine
    DcLoadFlowContext loadFlowContext;
    DenseMatrix injectionVectors;
    List<PropagatedContingency> contingencies;

    // output of the engine
    private DenseMatrix preContingencyStates;
    private List<DenseMatrix> postContingenciesStates;

    public WoodburyEngine(DcLoadFlowContext loadFlowContext, DenseMatrix injectionVectors,
                          List<PropagatedContingency> contingencies) {
        this.loadFlowContext = loadFlowContext;
        this.injectionVectors = injectionVectors;
        this.contingencies = contingencies;
    }

    static final class ComputedContingencyElement {

        private int contingencyIndex = -1; // index of the element in the rhs for +1-1
        private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
        private double alphaForStateValue = Double.NaN;
        private double alphaForFunctionReference = Double.NaN;
        private final ContingencyElement element;
        private final LfBranch lfBranch;
        private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

        private ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
            this.element = element;
            lfBranch = lfNetwork.getBranchById(element.getId());
            branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        private int getContingencyIndex() {
            return contingencyIndex;
        }

        private void setContingencyIndex(final int index) {
            this.contingencyIndex = index;
        }

        private int getLocalIndex() {
            return localIndex;
        }

        private void setLocalIndex(final int index) {
            this.localIndex = index;
        }

        private double getAlphaForStateValue() {
            return alphaForStateValue;
        }

        private void setAlphaForStateValue(final double alpha) {
            this.alphaForStateValue = alpha;
        }

        private double getAlphaForFunctionReference() {
            return alphaForFunctionReference;
        }

        private void setAlphaForFunctionReference(final double alpha) {
            this.alphaForFunctionReference = alpha;
        }

        private ContingencyElement getElement() {
            return element;
        }

        private LfBranch getLfBranch() {
            return lfBranch;
        }

        private ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
            return branchEquation;
        }

        private static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index++);
            }
        }

        private static void setLocalIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setLocalIndex(index++);
            }
        }

    }

    private static final class PhaseTapChangerContingenciesIndexing {

        private final List<PropagatedContingency> contingenciesWithoutTransformers = new ArrayList<>();
        private final Map<Set<LfBranch>, Collection<PropagatedContingency>> contingenciesIndexedByPhaseTapChangers = new LinkedHashMap<>();

        private PhaseTapChangerContingenciesIndexing(Collection<PropagatedContingency> contingencies,
                                                     Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                     Collection<String> elementIdsToSkip) {
            for (PropagatedContingency contingency : contingencies) {
                Set<LfBranch> lostTransformers = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementIdsToSkip.contains(element))
                        .map(contingencyElementByBranch::get)
                        .map(ComputedContingencyElement::getLfBranch)
                        .filter(LfBranch::hasPhaseControllerCapability)
                        .collect(Collectors.toSet());
                if (lostTransformers.isEmpty()) {
                    contingenciesWithoutTransformers.add(contingency);
                } else {
                    contingenciesIndexedByPhaseTapChangers.computeIfAbsent(lostTransformers, key -> new ArrayList<>()).add(contingency);
                }
            }
        }

        private Collection<PropagatedContingency> getContingenciesWithoutPhaseTapChangerLoss() {
            return contingenciesWithoutTransformers;
        }

        private Map<Set<LfBranch>, Collection<PropagatedContingency>> getContingenciesIndexedByPhaseTapChangers() {
            return contingenciesIndexedByPhaseTapChangers;
        }
    }

    private static final class ConnectivityAnalysisResult {

        private final Collection<PropagatedContingency> contingencies = new HashSet<>();

        private final Set<String> elementsToReconnect;

        private final Set<LfBus> disabledBuses;

        private final Set<LfBus> slackConnectedComponent;

        private final Set<LfBranch> partialDisabledBranches; // branches disabled because of connectivity loss.

        private ConnectivityAnalysisResult(Set<String> elementsToReconnect,
                                           GraphConnectivity<LfBus, LfBranch> connectivity,
                                           LfNetwork lfNetwork) {
            this.elementsToReconnect = elementsToReconnect;
            slackConnectedComponent = connectivity.getConnectedComponent(lfNetwork.getSlackBus());
            disabledBuses = connectivity.getVerticesRemovedFromMainComponent();
            partialDisabledBranches = connectivity.getEdgesRemovedFromMainComponent();
        }

        private Collection<PropagatedContingency> getContingencies() {
            return contingencies;
        }

        private Set<String> getElementsToReconnect() {
            return elementsToReconnect;
        }

        private Set<LfBus> getDisabledBuses() {
            return disabledBuses;
        }

        private Set<LfBus> getSlackConnectedComponent() {
            return slackConnectedComponent;
        }

        private Set<LfBranch> getPartialDisabledBranches() {
            return partialDisabledBranches;
        }
    }

    /**
     * Calculate the active power flows for pre-contingency or a post-contingency state and set the factor function reference.
     * The interesting disabled branches are only phase shifters.
     */
    private DenseMatrix calculateActivePowerFlows(DcLoadFlowContext loadFlowContext,
                                                  List<ParticipatingElement> participatingElements,
                                                  DisabledNetwork disabledNetwork,
                                                  Reporter reporter) {
        List<BusState> busStates = Collections.emptyList();
        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                    .map(ParticipatingElement::getLfBus)
                    .collect(Collectors.toSet()), BusState::save);
        }

        double[] dx = runDcLoadFlow(loadFlowContext, disabledNetwork, reporter);

        // TODO : remove the following. Just to keep it in mind
//        StateVector sv = new StateVector(dx);
//        for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
//            factor.setFunctionReference(factor.getFunctionEquationTerm().eval(sv)); // pass explicitly the previously calculated state vector
//        }

        if (parameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }

        return new DenseMatrix(dx.length, 1, dx);
    }
    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling and that do not
     * update the state vector and the network at the end (because we don't need it to just evaluate a few equations)
     */
    public double[] runDcLoadFlow(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork,
                                  Reporter reporter) {
        Collection<LfBus> remainingBuses;
        if (disabledNetwork.getBuses().isEmpty()) {
            remainingBuses = loadFlowContext.getNetwork().getBuses();
        } else {
            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
            remainingBuses.removeAll(disabledNetwork.getBuses());
        }

        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            DcLoadFlowEngine.distributeSlack(remainingBuses, parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
        }

        // we need to copy the target array because:
        //  - in case of disabled buses or branches some elements could be overwritten to zero
        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
        //    to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = loadFlowContext.getTargetVector().getArray().clone();

        if (!disabledNetwork.getBuses().isEmpty()) {
            // set buses injections and transformers to 0
            disabledNetwork.getBuses().stream()
                    .flatMap(lfBus -> loadFlowContext.getEquationSystem().getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!disabledNetwork.getBranches().isEmpty()) {
            // set transformer phase shift to 0
            disabledNetwork.getBranches().stream()
                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        boolean succeeded = DcLoadFlowEngine.solve(targetVectorArray, loadFlowContext.getJacobianMatrix(), reporter);
        if (!succeeded) {
            throw new PowsyblException("DC solver failed");
        }

        return targetVectorArray; // now contains dx
    }

    private static double calculatePower(DcLoadFlowContext loadFlowContext, LfBranch lfBranch) {
        PiModel piModel = lfBranch.getPiModel();
        DcEquationSystemCreationParameters creationParameters = loadFlowContext.getParameters().getEquationSystemCreationParameters();
        return AbstractClosedBranchDcFlowEquationTerm.calculatePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    private static void setAlphas(DcLoadFlowContext loadFlowContext, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix states,
                                  DenseMatrix contingenciesStates, int columnState, ObjDoubleConsumer<ComputedContingencyElement> setValue) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = 1d / calculatePower(loadFlowContext, lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            setValue.accept(element, b / a);
        } else {
            // FIXME: direct resolution if contingencyElements.size() == 2
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState)
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = 1d / calculatePower(loadFlowContext, lfBranch);
                    }
                    value = value - (contingenciesStates.get(p1.getPh1Var().getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> setValue.accept(element, rhs.get(element.getLocalIndex(), 0)));
        }
    }

    static DenseMatrix initContingencyRhs(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<ComputedContingencyElement> contingencyElements) {
        // otherwise, defining the rhs matrix will result in integer overflow
        int equationCount = equationSystem.getIndex().getSortedEquationsToSolve().size();
        int maxContingencyElements = Integer.MAX_VALUE / (equationCount * Double.BYTES);
        if (contingencyElements.size() > maxContingencyElements) {
            throw new PowsyblException("Too many contingency elements " + contingencyElements.size()
                    + ", maximum is " + maxContingencyElements + " for a system with " + equationCount + " equations");
        }

        DenseMatrix rhs = new DenseMatrix(equationCount, contingencyElements.size());
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    /**
     * Fills the right hand side with +1/-1 to model a branch contingency.
     */
    private static void fillRhsContingency(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                           Collection<ComputedContingencyElement> contingencyElements, Matrix rhs) {
        for (ComputedContingencyElement element : contingencyElements) {
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                continue;
            }
            LfBus bus1 = lfBranch.getBus1();
            LfBus bus2 = lfBranch.getBus2();
            if (bus1.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), -1);
            } else if (bus2.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), 1);
            } else {
                Equation<DcVariableType, DcEquationType> p1 = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                Equation<DcVariableType, DcEquationType> p2 = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p1.getColumn(), element.getContingencyIndex(), 1);
                rhs.set(p2.getColumn(), element.getContingencyIndex(), -1);
            }
        }
    }

    private static DenseMatrix calculateContingenciesStates(DcLoadFlowContext loadFlowContext, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
        DenseMatrix contingenciesStates = initContingencyRhs(loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem(), contingencyElementByBranch.values()); // rhs with +1 -1 on contingency elements
        loadFlowContext.getJacobianMatrix().solveTransposed(contingenciesStates);
        return contingenciesStates;
    }

    private static void detectPotentialConnectivityBreak(LfNetwork lfNetwork, DenseMatrix states, List<PropagatedContingency> contingencies,
                                                         Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                         EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                         Collection<PropagatedContingency> nonLosingConnectivityContingencies,
                                                         Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity) {
        for (PropagatedContingency contingency : contingencies) {
            List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());
            Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = getGroupOfElementsBreakingConnectivity(lfNetwork, states, contingencyElements, equationSystem);
            if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
                nonLosingConnectivityContingencies.add(contingency);
            } else {
                contingenciesByGroupOfElementsBreakingConnectivity.computeIfAbsent(groupOfElementsBreakingConnectivity, key -> new LinkedList<>()).add(contingency);
            }
        }
    }

    private static Set<ComputedContingencyElement> getGroupOfElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingenciesStates,
                                                                                          Collection<ComputedContingencyElement> contingencyElements,
                                                                                          EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity after a contingency
        // we consider a +1 -1 on a line, and we observe the sensitivity of these injections on the other contingency elements
        // if the sum of the sensitivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // thus, losing these lines will lose the connectivity
        Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = new LinkedHashSet<>();
        for (ComputedContingencyElement element : contingencyElements) {
            Set<ComputedContingencyElement> responsibleElements = new LinkedHashSet<>();
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(ElementType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculateSensi(contingenciesStates, element.getContingencyIndex()));
                if (value > CONNECTIVITY_LOSS_THRESHOLD) {
                    responsibleElements.add(element2);
                }
                sum += value;
            }
            if (sum > 1d - CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                groupOfElementsBreakingConnectivity.addAll(responsibleElements);
            }
        }
        return groupOfElementsBreakingConnectivity;
    }

    private static List<ConnectivityAnalysisResult> computeConnectivityData(LfNetwork lfNetwork, AbstractSensitivityAnalysis.SensitivityFactorHolder<DcVariableType, DcEquationType> factorHolder,
                                                                            Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity,
                                                                            List<PropagatedContingency> nonLosingConnectivityContingencies, SensitivityResultWriter resultWriter) {
        if (contingenciesByGroupOfElementsBreakingConnectivity.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> connectivityAnalysisResults = new LinkedHashMap<>();

        GraphConnectivity<LfBus, LfBranch> connectivity = lfNetwork.getConnectivity();
        for (Map.Entry<Set<ComputedContingencyElement>, List<PropagatedContingency>> e : contingenciesByGroupOfElementsBreakingConnectivity.entrySet()) {
            Set<ComputedContingencyElement> breakingConnectivityCandidates = e.getKey();
            List<PropagatedContingency> contingencyList = e.getValue();
            connectivity.startTemporaryChanges();
            breakingConnectivityCandidates.stream()
                    .map(ComputedContingencyElement::getElement)
                    .map(ContingencyElement::getId)
                    .distinct()
                    .map(lfNetwork::getBranchById)
                    .filter(b -> b.getBus1() != null && b.getBus2() != null)
                    .forEach(connectivity::removeEdge);

            // filter the branches that really impacts connectivity
            Set<ComputedContingencyElement> breakingConnectivityElements = breakingConnectivityCandidates.stream()
                    .filter(element -> isBreakingConnectivity(connectivity, element))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (breakingConnectivityElements.isEmpty()) {
                // we did not break any connectivity
                nonLosingConnectivityContingencies.addAll(contingencyList);
            } else {
                // only compute for factors that have to be computed for this contingency lost
                List<String> contingenciesIds = contingencyList.stream().map(contingency -> contingency.getContingency().getId()).collect(Collectors.toList());

                List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors = factorHolder.getFactorsForContingencies(contingenciesIds);
                if (!lfFactors.isEmpty()) {
                    ConnectivityAnalysisResult connectivityAnalysisResult = connectivityAnalysisResults.computeIfAbsent(breakingConnectivityElements, k -> {
                        Set<String> elementsToReconnect = computeElementsToReconnect(connectivity, breakingConnectivityElements);
                        return new ConnectivityAnalysisResult(elementsToReconnect, connectivity, lfNetwork);
                    });
                    connectivityAnalysisResult.getContingencies().addAll(contingencyList);
                } else {
                    // write contingency status
                    for (PropagatedContingency propagatedContingency : contingencyList) {
                        resultWriter.writeContingencyStatus(propagatedContingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
                    }
                }
            }
            connectivity.undoTemporaryChanges();
        }
        return new ArrayList<>(connectivityAnalysisResults.values());
    }

    private static boolean isBreakingConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity, ComputedContingencyElement element) {
        LfBranch lfBranch = element.getLfBranch();
        return connectivity.getComponentNumber(lfBranch.getBus1()) != connectivity.getComponentNumber(lfBranch.getBus2());
    }

    /**
     * Given the elements breaking the connectivity, extract the minimum number of elements which reconnect all connected components together
     */
    private static Set<String> computeElementsToReconnect(GraphConnectivity<LfBus, LfBranch> connectivity, Set<ComputedContingencyElement> breakingConnectivityElements) {
        Set<String> elementsToReconnect = new LinkedHashSet<>();

        // We suppose we're reconnecting one by one each element breaking connectivity.
        // At each step we look if the reconnection was needed on the connectivity level by maintaining a list of grouped connected components.
        List<Set<Integer>> reconnectedCc = new ArrayList<>();
        for (ComputedContingencyElement element : breakingConnectivityElements) {
            int cc1 = connectivity.getComponentNumber(element.getLfBranch().getBus1());
            int cc2 = connectivity.getComponentNumber(element.getLfBranch().getBus2());

            Set<Integer> recCc1 = reconnectedCc.stream().filter(s -> s.contains(cc1)).findFirst().orElseGet(() -> new HashSet<>(List.of(cc1)));
            Set<Integer> recCc2 = reconnectedCc.stream().filter(s -> s.contains(cc2)).findFirst().orElseGet(() -> Set.of(cc2));
            if (recCc1 != recCc2) {
                // cc1 and cc2 are still separated:
                // - mark the element as needed to reconnect all connected components together
                // - update the list of grouped connected components
                elementsToReconnect.add(element.getElement().getId());
                reconnectedCc.remove(recCc2);
                if (recCc1.size() == 1) {
                    // adding the new set (the list of grouped connected components is not initialized with the singleton sets)
                    reconnectedCc.add(recCc1);
                }
                recCc1.addAll(recCc2);
            }
        }

        if (reconnectedCc.size() != 1 || reconnectedCc.get(0).size() != connectivity.getNbConnectedComponents()) {
            LOGGER.error("Elements to reconnect computed do not reconnect all connected components together");
        }

        return elementsToReconnect;
    }

    private void processContingenciesBreakingConnectivity(ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                          LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                          AbstractSensitivityAnalysis.SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                                                          AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                          List<ParticipatingElement> participatingElements,
                                                          Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                          DenseMatrix flowStates, DenseMatrix factorsStates, DenseMatrix contingenciesStates,
                                                          SensitivityResultWriter resultWriter,
                                                          Reporter reporter) {
        DenseMatrix modifiedFlowStates = flowStates;

        List<String> contingenciesIds = connectivityAnalysisResult.getContingencies().stream().map(c -> c.getContingency().getId()).collect(Collectors.toList());
        List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactorsForContingencies = validFactorHolder.getFactorsForContingencies(contingenciesIds);

        Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();
        Set<LfBranch> partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();

        // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
        // if one bus of the line is lost.
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                });
            }
        }

        // null and unused if slack bus is not distributed
        List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
        boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
        DenseMatrix factorStateForThisConnectivity = factorsStates;
        if (lfParameters.isDistributedSlack()) {
            rhsChanged = participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
        }
        if (factorGroups.hasMultiVariables()) {
            // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
            rhsChanged |= rescaleGlsk(factorGroups, disabledBuses);
        }

        // we need to recompute the factor states because the connectivity changed
        if (rhsChanged) {
            participatingElementsForThisConnectivity = lfParameters.isDistributedSlack()
                    ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                    : Collections.emptyList();

            factorStateForThisConnectivity = calculateFactorStates(loadFlowContext, factorGroups, participatingElementsForThisConnectivity);
        }

        if (!lfFactorsForContingencies.isEmpty()) {
            modifiedFlowStates = calculateActivePowerFlows(loadFlowContext, lfFactorsForContingencies,
                    participatingElementsForThisConnectivity, new DisabledNetwork(disabledBuses, Collections.emptySet()),
                    reporter);
        }

        calculateSensitivityValuesForContingencyList(loadFlowContext, lfParametersExt,
                validFactorHolder, factorGroups, factorStateForThisConnectivity, contingenciesStates, modifiedFlowStates,
                connectivityAnalysisResult.getContingencies(), contingencyElementByBranch, disabledBuses, participatingElementsForThisConnectivity,
                connectivityAnalysisResult.getElementsToReconnect(), resultWriter, reporter, partialDisabledBranches);

        // TODO : remove me, just for reminder.
//        if (rhsChanged) {
//            setBaseCaseSensitivityValues(factorGroups, factorsStates); // we modified the rhs, we need to restore previous state
//        }
    }

    private void calculateStateValuesForContingencyList(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, AbstractSensitivityAnalysis.SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                                                              AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, DenseMatrix factorState, DenseMatrix contingenciesStates, DenseMatrix flowStates,
                                                              Collection<PropagatedContingency> contingencies, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                              Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements, Set<String> elementsToReconnect,
                                                              SensitivityResultWriter resultWriter, Reporter reporter, Set<LfBranch> partialDisabledBranches) {
        DenseMatrix modifiedFlowStates = flowStates;

        PhaseTapChangerContingenciesIndexing phaseTapChangerContingenciesIndexing = new PhaseTapChangerContingenciesIndexing(contingencies, contingencyElementByBranch, elementsToReconnect);

        var lfNetwork = loadFlowContext.getNetwork();

        // compute states without loss of phase tap changer
        // first we compute the ones without loss of phase tap changers (because we reuse the load flows from the pre contingency network for all of them)
        for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .collect(Collectors.toList());

            Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
            disabledBranches.addAll(partialDisabledBranches);

            calculateContingencyStateValues(contingency, factorGroups, factorState, contingenciesStates, modifiedFlowStates, contingencyElements, resultWriter,
                    loadFlowContext, lfParametersExt, validFactorHolder, participatingElements,
                    new DisabledNetwork(disabledBuses, disabledBranches), reporter);
            // TODO : remove. Just there for reminder.
//            calculateContingencySensitivityValues(contingency, factorGroups, factorState, contingenciesStates, modifiedFlowStates, contingencyElements, resultWriter,
//                    loadFlowContext, lfParametersExt, validFactorHolder, participatingElements,
//                    new DisabledNetwork(disabledBuses, disabledBranches), reporter);
        }

        // then we compute the ones involving the loss of a phase tap changer (because we need to recompute the load flows)
        for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> e : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
            Set<LfBranch> disabledPhaseTapChangers = e.getKey();
            Collection<PropagatedContingency> propagatedContingencies = e.getValue();
//            List<String> contingenciesIds = propagatedContingencies.stream()
//                    .map(c -> c.getContingency().getId())
//                    .collect(Collectors.toList());
            // TODO : remove the use of lfFactors there
//            List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors = validFactorHolder.getFactorsForContingencies(contingenciesIds);
//            if (!lfFactors.isEmpty()) {
            modifiedFlowStates = calculateActivePowerFlows(loadFlowContext, //lfFactors,
                        participatingElements, new DisabledNetwork(disabledBuses, disabledPhaseTapChangers), reporter); // TODO : verify if i should add a security before computing this
//            }
            for (PropagatedContingency contingency : propagatedContingencies) {
                Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementsToReconnect.contains(element))
                        .map(contingencyElementByBranch::get)
                        .collect(Collectors.toList());

                Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
                disabledBranches.addAll(partialDisabledBranches);

                calculateContingencyStateValues(contingency, factorGroups, factorState, contingenciesStates, modifiedFlowStates, contingencyElements, resultWriter,
                        loadFlowContext, lfParametersExt, validFactorHolder, participatingElements,
                        new DisabledNetwork(disabledBuses, disabledBranches), reporter);
                // TODO : remove. Just there for reminder
//                calculateContingencySensitivityValues(contingency, factorGroups, factorState, contingenciesStates, modifiedFlowStates, contingencyElements, resultWriter,
//                        loadFlowContext, lfParametersExt, validFactorHolder, participatingElements,
//                        new DisabledNetwork(disabledBuses, disabledBranches), reporter);
            }
        }
    }

    /**
     * Calculate values for a post-contingency state.
     * When a contingency involves the loss of a load or a generator, the slack distribution could changed
     * or the sensitivity factors in case of GLSK.
     */
    private void calculateContingencyStateValues(PropagatedContingency contingency, AbstractSensitivityAnalysis.SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, DenseMatrix factorStates, DenseMatrix contingenciesStates,
                                                       DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements, SensitivityResultWriter resultWriter,
                                                       DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                       AbstractSensitivityAnalysis.SensitivityFactorHolder<DcVariableType, DcEquationType> factorHolder, List<ParticipatingElement> participatingElements,
                                                       DisabledNetwork disabledNetwork, Reporter reporter) {
//        List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> factors = factorHolder.getFactorsForContingency(contingency.getContingency().getId());
        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
//            calculateStateValues(loadFlowContext, factors, factorStates, contingenciesStates, flowStates, contingencyElements,
//                    contingency, resultWriter, disabledNetwork);
            calculateStateValues(loadFlowContext, null, factorStates, contingenciesStates, flowStates, contingencyElements,
                    contingency, resultWriter, disabledNetwork);
            // write contingency status
//            if (contingency.hasNoImpact()) {
//                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
//            } else {
//                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
//            }
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            LfNetwork lfNetwork = loadFlowContext.getNetwork();
            DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            DenseMatrix newFactorStates = factorStates;
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            boolean participatingElementsChanged = false;
            boolean rhsChanged = false;
            if (lfContingency != null) {
                lfContingency.apply(lfParameters.getBalanceType());
                participatingElementsChanged = isDistributedSlackOnGenerators(lfParameters) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || isDistributedSlackOnLoads(lfParameters) && !contingency.getLoadIdsToLoose().isEmpty();
                if (factorGroups.hasMultiVariables()) {
                    Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
                    rhsChanged = rescaleGlsk(factorGroups, impactedBuses);
                }
                if (participatingElementsChanged) {
                    if (isDistributedSlackOnGenerators(lfParameters)) {
                        // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
                        Set<LfGenerator> participatingGeneratorsToRemove = lfContingency.getLostGenerators();
                        newParticipatingElements = participatingElements.stream()
                                .filter(participatingElement -> !participatingGeneratorsToRemove.contains(participatingElement.getElement()))
                                .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                                .collect(Collectors.toList());
                        normalizeParticipationFactors(newParticipatingElements);
                    } else { // slack distribution on loads
                        newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
                    }
                }
                if (participatingElementsChanged || rhsChanged) {
                    newFactorStates = calculateFactorStates(loadFlowContext, factorGroups, newParticipatingElements);
                }
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
            }

            DenseMatrix newFlowStates = calculateActivePowerFlows(loadFlowContext, //factors,
                    newParticipatingElements, disabledNetwork, reporter);

            calculateStateValues(loadFlowContext, factors, newFactorStates, contingenciesStates, newFlowStates, contingencyElements,
                    contingency, resultWriter, disabledNetwork);

            networkState.restore();
            // TODO : juste there for reminder.
//            if (participatingElementsChanged || rhsChanged) {
//                setBaseCaseSensitivityValues(factorGroups, factorStates);
//            }
        }
    }

    /**
     * Calculate values for post-contingency state using the pre-contingency state value and some flow transfer factors (alphas).
     */
    private void calculateStateValues(DcLoadFlowContext loadFlowContext, List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors, DenseMatrix factorStates,
                                      DenseMatrix contingenciesStates, DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements,
                                      PropagatedContingency contingency, SensitivityResultWriter resultWriter, DisabledNetwork disabledNetwork) {

        // for the flows
        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
        DenseMatrix postContingencyState = postContingenciesStates.get(0);
        for (int rowIndex = 0; rowIndex < postContingencyState.getRowCount(); rowIndex++) {
            double postContingencyValue = preContingencyStates.get(rowIndex, 0);
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                postContingencyValue += contingencyElement.getAlphaForFunctionReference() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
            }
            postContingencyState.set(rowIndex, contingency.getIndex(), postContingencyValue); // TODO : verify coord j
        }

        // from 1 because first one is for load flow vector
        for (int columnIndex = 1; columnIndex < postContingenciesStates.size(); columnIndex++) {
            setAlphas(loadFlowContext, contingencyElements, preContingencyStates, contingenciesStates, columnIndex, ComputedContingencyElement::setAlphaForStateValue);
            postContingencyState = postContingenciesStates.get(columnIndex);
            for (int rowIndex = 0; rowIndex < postContingencyState.getRowCount(); rowIndex++) {
                double postContingencyValue = preContingencyStates.get(rowIndex, columnIndex);
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForStateValue() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
                }
                postContingencyState.set(rowIndex, contingency.getIndex(), postContingencyValue); // TODO : verify coord j
            }
        }
//        if (lfFactors.isEmpty()) {
//            return;
//        }
//
//        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
//
//        lfFactors.stream().filter(factor -> factor.getStatus() == AbstractSensitivityAnalysis.LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
//                .forEach(factor -> createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork));
//
//        Map<AbstractSensitivityAnalysis.SensitivityFactorGroup<DcVariableType, DcEquationType>, List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
//                .filter(factor -> factor.getStatus() == AbstractSensitivityAnalysis.LfSensitivityFactor.Status.VALID)
//                .collect(Collectors.groupingBy(AbstractSensitivityAnalysis.LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
//        for (Map.Entry<AbstractSensitivityAnalysis.SensitivityFactorGroup<DcVariableType, DcEquationType>, List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
//            AbstractSensitivityAnalysis.SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
//            List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
//            setAlphas(loadFlowContext, contingencyElements, factorStates, contingenciesStates, factorGroup.getIndex(), ComputedContingencyElement::setAlphaForStateValue);
//            for (AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
//                createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork);
//            }
//        }
    }

//    /**
    // TODO : is not longer used. We compute directly in the states in the function calculateStateValues
//     * Get the sensitivity value for pre-contingency state and calculate the sensitivity value for a post-contingency state if asked.
//     * The sensitivity value is written in the SensitivityResultWriter.
//     */
//    private void createBranchSensitivityValue(AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType> factor, DenseMatrix contingenciesStates,
//                                              Collection<ComputedContingencyElement> contingencyElements,
//                                              PropagatedContingency contingency, SensitivityResultWriter resultWriter,
//                                              DisabledNetwork disabledNetwork) {
////        Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, disabledNetwork, contingency);
////        Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
////        Optional<Double> functionPredefinedResults = predefinedResults.getRight();
////        double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
////        double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);
//        EquationTerm<DcVariableType, DcEquationType> p1 = factor.getFunctionEquationTerm();
//
//        if (!(functionPredefinedResults.isPresent() && sensitivityValuePredefinedResult.isPresent())) {
//            for (ComputedContingencyElement contingencyElement : contingencyElements) {
//                double contingencySensitivity = p1.calculateSensi(contingenciesStates, contingencyElement.getContingencyIndex());
//                if (functionPredefinedResults.isEmpty()) {
//                    functionValue += contingencyElement.getAlphaForFunctionReference() * contingencySensitivity;
//                }
//                if (sensitivityValuePredefinedResult.isEmpty()) {
//                    sensitivityValue += contingencyElement.getAlphaForStateValue() * contingencySensitivity;
//                }
//            }
//        }
//
//        functionValue = fixZeroFunctionReference(contingency, functionValue);
//
//        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
//        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
//            resultWriter.writeSensitivityValue(factor.getIndex(), contingency != null ? contingency.getIndex() : -1, unscaledSensi, unscaleFunction(factor, functionValue));
//        }
//    }

    // FIXME : from AbstractSensitivityAnalysis
    protected static boolean isDistributedSlackOnGenerators(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
    }

    // FIXME : from AbstractSensitivityAnalysis
    protected static boolean isDistributedSlackOnLoads(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
    }

    private static Map<String, ComputedContingencyElement> createContingencyElementsIndexByBranchId(List<PropagatedContingency> contingencies,
                                                                                                                   LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        Map<String, WoodburyEngine.ComputedContingencyElement> contingencyElementByBranch =
                contingencies.stream()
                        .flatMap(contingency -> contingency.getBranchIdsToOpen().keySet().stream())
                        .map(branch -> new WoodburyEngine.ComputedContingencyElement(new BranchContingency(branch), lfNetwork, equationSystem))
                        .filter(element -> element.getLfBranchEquation() != null)
                        .collect(Collectors.toMap(
                                computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                computedContingencyElement -> computedContingencyElement,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ));
        WoodburyEngine.ComputedContingencyElement.setContingencyIndexes(contingencyElementByBranch.values());
        return contingencyElementByBranch;
    }

    // TODO : verify for validFactorHolder if we need it. Seems not, and should be replaced by handmade construct set with states.
    public void run(List<ParticipatingElement> participatingElements, Reporter reporter, AbstractSensitivityAnalysis.SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                    LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        int rowNum = injectionVectors.getRowCount(); // number of buses
        int columnNum = injectionVectors.getColumnCount() + 1; // number of preContingencyStates to compute

        // pre-contingencies states
        DenseMatrix flowStates = calculateActivePowerFlows(loadFlowContext, participatingElements, new DisabledNetwork(), reporter); // run DC load flow on pre-contingency network
        loadFlowContext.getJacobianMatrix().solveTransposed(injectionVectors); // Compute pre-contingency states
        double[] tempo = new double[rowNum * columnNum];
        // TODO : fill tempo with flowStates and injectionVectors
        preContingencyStates = new DenseMatrix(rowNum, columnNum, tempo); // fill with flowStates and injections vectors

        // post-contingencies matrices (void for now)
        postContingenciesStates = new ArrayList<>();
        for (int i = 0; i < contingencies.size(); i++) {
            postContingenciesStates.add(new DenseMatrix(rowNum, columnNum));
        }

        // index contingency elements by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = createContingencyElementsIndexByBranchId(contingencies, loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem());
        // Compute post-element-contingency states
        DenseMatrix contingenciesStates = calculateContingenciesStates(loadFlowContext, contingencyElementByBranch);

        // connectivity analysis by contingency
        // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
        // so, we will index contingencies by a list of branch that may break connectivity
        // for example, if in the network, loosing line L1 breaks connectivity, and loosing L2 and L3 together breaks connectivity,
        // the index would be: {L1, L2, L3}
        // a contingency involving a phase tap changer loss has to be processed separately
        List<PropagatedContingency> nonBreakingConnectivityContingencies = new ArrayList<>();
        Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsPotentiallyBreakingConnectivity = new LinkedHashMap<>();

        // this first method based on sensitivity criteria is able to detect some contingencies that do not break
        // connectivity and other contingencies that potentially break connectivity
        detectPotentialConnectivityBreak(loadFlowContext.getNetwork(), contingenciesStates, contingencies, contingencyElementByBranch, loadFlowContext.getEquationSystem(),
                nonBreakingConnectivityContingencies, contingenciesByGroupOfElementsPotentiallyBreakingConnectivity);
        LOGGER.info("After sensitivity based connectivity analysis, {} contingencies do not break connectivity, {} contingencies potentially break connectivity",
                nonBreakingConnectivityContingencies.size(), contingenciesByGroupOfElementsPotentiallyBreakingConnectivity.values().stream().mapToInt(List::size).count());

        // this second method process all contingencies that potentially break connectivity and using graph algorithms
        // find remaining contingencies that do not break connectivity
        List<ConnectivityAnalysisResult> connectivityAnalysisResults
                = computeConnectivityData(loadFlowContext.getNetwork(), validFactorHolder, contingenciesByGroupOfElementsPotentiallyBreakingConnectivity, nonBreakingConnectivityContingencies, resultWriter);
        LOGGER.info("After graph based connectivity analysis, {} contingencies do not break connectivity, {} contingencies break connectivity",
                nonBreakingConnectivityContingencies.size(), connectivityAnalysisResults.stream().mapToInt(results -> results.getContingencies().size()).count());

        LOGGER.info("Processing contingencies with no connectivity break");

        // calculate state values for contingencies with no connectivity break
        // TODO : calculateStateValuesForContingencyList
        calculateStateValuesForContingencyList(loadFlowContext, lfParametersExt, validFactorHolder, null,
                null, contingenciesStates, flowStates, nonBreakingConnectivityContingencies, contingencyElementByBranch,
                Collections.emptySet(), participatingElements, Collections.emptySet(), null, reporter, Collections.emptySet());
//        calculateSensitivityValuesForContingencyList(loadFlowContext, lfParametersExt, validFactorHolder, factorGroups,
//                factorsStates, contingenciesStates, flowStates, nonBreakingConnectivityContingencies, contingencyElementByBranch,
//                Collections.emptySet(), participatingElements, Collections.emptySet(), resultWriter, reporter, Collections.emptySet());

        LOGGER.info("Processing contingencies with connectivity break");

        // process contingencies with connectivity break
        // TODO : processContingenciesBreakingConnectivity but using calculateStateValuesForContingencyList this time
        for (ConnectivityAnalysisResult connectivityAnalysisResult : connectivityAnalysisResults) {
            processContingenciesBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, lfParameters, lfParametersExt,
                    validFactorHolder, null, participatingElements,
                    contingencyElementByBranch, flowStates, null, contingenciesStates, null, reporter);
        }
    }
}
