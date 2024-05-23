package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.*;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.sensi.DcSensitivityAnalysis;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.ConnectivityResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;
import com.powsybl.sensitivity.SensitivityResultWriter;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.initStateVector;
import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.updateNetwork;
import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;
import static com.powsybl.openloadflow.sensi.DcSensitivityAnalysis.getPreContingencyFlowRhs;

public class WoodburyDcSecurityAnalysis extends AbstractSecurityAnalysis<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcLoadFlowResult> {

    protected WoodburyDcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, ReportNode reportNode) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reportNode);
    }

    @Override
    protected ReportNode createSaRootReportNode() {
        return Reports.createWoodburyDcSecurityAnalysis(reportNode, network.getId());
    }

    @Override
    protected boolean isShuntCompensatorVoltageControlOn(LoadFlowParameters lfParameters) {
        return false;
    }

    @Override
    protected DcLoadFlowParameters createParameters(LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers) {
        var dcParameters = OpenLoadFlowParameters.createDcParameters(network, lfParameters,
                lfParametersExt, matrixFactory, connectivityFactory, false);
        dcParameters.getNetworkParameters()
                .setBreakers(breakers)
                .setCacheEnabled(false) // force not caching as not supported in secu analysis
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR); // not supported yet
        return dcParameters;
    }

    @Override
    protected DcLoadFlowContext createLoadFlowContext(LfNetwork lfNetwork, DcLoadFlowParameters parameters) {
        return new DcLoadFlowContext(lfNetwork, parameters);
    }

    @Override
    protected DcLoadFlowEngine createLoadFlowEngine(DcLoadFlowContext context) {
        return new DcLoadFlowEngine(context);
    }

    @Override
    protected PostContingencyComputationStatus postContingencyStatusFromLoadFlowResult(DcLoadFlowResult result) {
        return result.isSuccess() ? PostContingencyComputationStatus.CONVERGED : PostContingencyComputationStatus.FAILED;
    }

    protected List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType, OpenLoadFlowParameters openLoadFlowParameters) {
        ActivePowerDistribution.Step step = ActivePowerDistribution.getStep(balanceType, openLoadFlowParameters.isLoadPowerFactorConstant(), openLoadFlowParameters.isUseActiveLimits());
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);
        ParticipatingElement.normalizeParticipationFactors(participatingElements);
        return participatingElements;
    }

    private List<ParticipatingElement> getNewNormalizedParticipationFactors(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                                            LfContingency lfContingency, List<ParticipatingElement> participatingElements) {
        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
        List<ParticipatingElement> newParticipatingElements;
        if (isDistributedSlackOnGenerators(loadFlowContext.getParameters())) {
            // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
            Set<LfGenerator> participatingGeneratorsToRemove = lfContingency.getLostGenerators();
            newParticipatingElements = participatingElements.stream()
                    .filter(participatingElement -> !(participatingElement.getElement() instanceof LfGenerator lfGenerator
                            && participatingGeneratorsToRemove.contains(lfGenerator)))
                    .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                    .toList();
            normalizeParticipationFactors(newParticipatingElements);
        } else { // slack distribution on loads
            newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
        }
        return newParticipatingElements;
    }

    private List<ParticipatingElement> processInjectionRhsModificationForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                                                      LfContingency lfContingency, PropagatedContingency contingency, List<ParticipatingElement> participatingElements,
                                                                                      WoodburyEngineRhsModifications injectionRhsModifications) {
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
        lfContingency.apply(lfParameters.getBalanceType());
        List<ParticipatingElement> modifiedParticipatingElements = participatingElements;
        boolean rhsChanged = isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
        if (rhsChanged) {
            modifiedParticipatingElements = getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, modifiedParticipatingElements);
        }
        return modifiedParticipatingElements;
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

    protected void buildRhsModificationsForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                      PropagatedContingency contingency, List<ParticipatingElement> participatingElements,
                                                      WoodburyEngineRhsModifications flowRhsModifications, DisabledNetwork disabledNetwork) {

        if (!(contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty())
            || contingency.getBusIdsToLose().stream().map(id -> network.getBusBreakerView().getBus(id)).filter(bus -> bus.getLoadStream().collect(Collectors.toList()).size() > 0).collect(Collectors.toList()).size() > 0) {
            LfNetwork lfNetwork = loadFlowContext.getNetwork();
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            if (lfContingency != null) {
                newParticipatingElements = processInjectionRhsModificationForAContingency(loadFlowContext, lfParametersExt, lfContingency, contingency,
                        newParticipatingElements, null);
                // write contingency status
            }

            DenseMatrix flowsRhsOverride = getPreContingencyFlowRhs(loadFlowContext, newParticipatingElements, disabledNetwork);
            flowRhsModifications.addRhsOverrideByPropagatedContingency(contingency, flowsRhsOverride);

            networkState.restore();
        }
    }

    /**
     * Compute right hand side overrides for a list of contingencies.
     */
    protected void buildRhsModificationsForContingencies(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                       Collection<PropagatedContingency> contingencies, List<ParticipatingElement> participatingElements, WoodburyEngineRhsModifications injectionRhsModifications,
                                                       WoodburyEngineRhsModifications flowRhsModifications, HashMap<PropagatedContingency, DisabledNetwork> disabledNetworksByPropagatedContingencies, Set<LfBus> disabledBuses,
                                                       Set<LfBranch> partialDisabledBranches, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                       Set<String> elementsToReconnect, SensitivityResultWriter resultWriter) {
        LfNetwork lfNetwork = loadFlowContext.getNetwork();

        DcSensitivityAnalysis.PhaseTapChangerContingenciesIndexing phaseTapChangerContingenciesIndexing = new DcSensitivityAnalysis.PhaseTapChangerContingenciesIndexing(contingencies, contingencyElementByBranch, elementsToReconnect);

        // compute rhs modifications for contingencies without loss of phase tap changer
        // first we compute the ones without loss of phase tap changers (because no need to recompute new rhs for load flows)
        for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
            Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
            disabledBranches.addAll(partialDisabledBranches);

            DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
            disabledNetworksByPropagatedContingencies.put(contingency, disabledNetwork);

            buildRhsModificationsForAContingency(loadFlowContext, lfParametersExt, contingency, participatingElements, flowRhsModifications, disabledNetwork);
        }

        // then we compute the ones involving the loss of a phase tap changer (because we need to recompute new rhs for load flows)
        for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> e : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
            Set<LfBranch> disabledPhaseTapChangers = e.getKey();
            Collection<PropagatedContingency> propagatedContingencies = e.getValue();
            DenseMatrix modifiedFlowRhs = getPreContingencyFlowRhs(loadFlowContext, participatingElements,
                    new DisabledNetwork(disabledBuses, disabledPhaseTapChangers));

            for (PropagatedContingency contingency : propagatedContingencies) {
                flowRhsModifications.addRhsOverrideByPropagatedContingency(contingency, (DenseMatrix) modifiedFlowRhs.copy(new DenseMatrixFactory()));
                Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
                disabledBranches.addAll(partialDisabledBranches);

                DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
                disabledNetworksByPropagatedContingencies.put(contingency, disabledNetwork);

                buildRhsModificationsForAContingency(loadFlowContext, lfParametersExt, contingency, participatingElements, flowRhsModifications, new DisabledNetwork(disabledBuses, disabledBranches));
            }
        }
    }

    private double[] getAngleStatesAsArray(DenseMatrix angleStates) {
        if (angleStates.getColumnCount() > 1) {
            throw new PowsyblException();
        }
        double[] angleStatesArray = new double[angleStates.getRowCount()];
        for (int i = 0; i < angleStatesArray.length; i++) {
            angleStatesArray[i] = angleStates.get(i, 0);
        }
        return angleStatesArray;
    }

    @Override
    protected SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies, DcLoadFlowParameters acParameters,
                                                    SecurityAnalysisParameters securityAnalysisParameters, List<OperatorStrategy> operatorStrategies,
                                                    List<Action> actions) {
        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (DcLoadFlowContext context = createLoadFlowContext(lfNetwork, acParameters)) {
            ReportNode networkReportNode = lfNetwork.getReportNode();
            ReportNode preContSimReportNode = Reports.createPreContingencySimulation(networkReportNode);
            lfNetwork.setReportNode(preContSimReportNode);

            // no participating element for now
            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution)
            List<ParticipatingElement> participatingElements = Collections.emptyList();
//                    loadFlowParameters.isDistributedSlack()
//                    ? getParticipatingElements(lfNetwork.getBuses(), loadFlowParameters.getBalanceType(), openLoadFlowParameters)
//                    :

            DenseMatrix flowsRhs = getPreContingencyFlowRhs(context, participatingElements, new DisabledNetwork());

            // connectivity analysis
            ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData = ConnectivityBreakAnalysis.run(context, propagatedContingencies);

            // let's say no contingency breaking connectivity for now FIXME
            HashMap<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency = new HashMap<>();
            WoodburyEngineRhsModifications flowRhsModifications = new WoodburyEngineRhsModifications();
            buildRhsModificationsForContingencies(context, openLoadFlowParameters, propagatedContingencies,
                    participatingElements, null,
                    flowRhsModifications, disabledNetworkByPropagatedContingency, Collections.emptySet(), Collections.emptySet(),
                    connectivityData.contingencyElementByBranch(), Collections.emptySet(), null);

            // compute pre- and post-contingency flow states
            WoodburyEngine engine = new WoodburyEngine();
            WoodburyEngineResult angleStates = engine.run(context, flowsRhs, flowRhsModifications, connectivityData, reportNode);

            // Update pre contingency network results
            double[] preContingencyAnglesStates = getAngleStatesAsArray(angleStates.getPreContingencyStates());
            context.getEquationSystem().getStateVector().set(preContingencyAnglesStates);
            updateNetwork(lfNetwork, context.getEquationSystem(), preContingencyAnglesStates);

            // set all calculated voltages to NaN
            if (context.getParameters().isSetVToNan()) {
                for (LfBus bus : lfNetwork.getBuses()) {
                    bus.setV(Double.NaN);
                }
            }

//            boolean preContingencyComputationOk = true;
            var preContingencyLimitViolationManager = new LimitViolationManager();
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);

            // update network result
            preContingencyNetworkResult.update();

            // detect violations
            preContingencyLimitViolationManager.detectViolations(lfNetwork);

                // save base state for later restoration after each contingency
            NetworkState networkState = NetworkState.save(lfNetwork);

            List<PostContingencyResult> postContingencyResults = new ArrayList<>();

            for (PropagatedContingency contingency : propagatedContingencies) {
                Optional<LfContingency> optionalLfContingency = contingency.toLfContingency(lfNetwork);
                if (optionalLfContingency.isPresent()) {

                    LfContingency lfContingency = optionalLfContingency.get();
                    LOGGER.info("Start post contingency '{}' violations detection on network {}", lfContingency.getId(), network);
                    LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift {} loads",
                        lfContingency.getId(), network, lfContingency.getDisabledNetwork().getBuses(), lfContingency.getDisabledNetwork().getBranchesStatus(),
                        lfContingency.getLostGenerators(), lfContingency.getShuntsShift(), lfContingency.getLostLoads());

                    lfContingency.apply(loadFlowParameters.getBalanceType());
                    distributedMismatch(lfNetwork, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                    var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, securityAnalysisParameters.getIncreasedViolationsParameters());
                    var postContingencyNetworkResult = new PostContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency.getContingency());

                    // TODO : Should not be there.
                    initStateVector(lfNetwork, context.getEquationSystem(), new UniformValueVoltageInitializer());
                    double[] postContingencyAngleStates = getAngleStatesAsArray(angleStates.getPostContingencyWoodburyStates(contingency));
                    context.getEquationSystem().getStateVector().set(postContingencyAngleStates);
                    postContingencyNetworkResult.update();

                    // detect violations
                    postContingencyLimitViolationManager.detectViolations(lfNetwork);

                    var connectivityResult = new ConnectivityResult(
                            lfContingency.getCreatedSynchronousComponentsCount(), 0,
                            lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                            lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                            lfContingency.getDisconnectedElementIds());

                    PostContingencyResult postContingencyResult = new PostContingencyResult(contingency.getContingency(), PostContingencyComputationStatus.CONVERGED,
                            new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                            postContingencyNetworkResult.getBranchResults(),
                            postContingencyNetworkResult.getBusResults(),
                            postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                            connectivityResult);
                    postContingencyResults.add(postContingencyResult);

                    networkState.restore();
                }
            }

//            // start a simulation for each of the contingency
//            Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
//            while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
//                PropagatedContingency propagatedContingency = contingencyIt.next();
//                propagatedContingency.toLfContingency(lfNetwork)
//                        .ifPresent(lfContingency -> { // only process contingencies that impact the network
//                            ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfContingency.getId());
//                            lfNetwork.setReportNode(postContSimReportNode);
//
//                            lfContingency.apply(loadFlowParameters.getBalanceType());
//
//                            distributedMismatch(lfNetwork, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);
//
//                            var postContingencyResult = runPostContingencySimulation(lfNetwork, context, propagatedContingency.getContingency(),
//                                    lfContingency, preContingencyLimitViolationManager,
//                                    securityAnalysisParameters.getIncreasedViolationsParameters(),
//                                    preContingencyNetworkResult, createResultExtension);
//                            postContingencyResults.add(postContingencyResult);
//
//                                List<OperatorStrategy> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId.get(lfContingency.getId());
//                                if (operatorStrategiesForThisContingency != null) {
//                                    // we have at least an operator strategy for this contingency.
//                                    if (operatorStrategiesForThisContingency.size() == 1) {
//                                        runActionSimulation(lfNetwork, context,
//                                                operatorStrategiesForThisContingency.get(0), preContingencyLimitViolationManager,
//                                                securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
//                                                createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
//                                                acParameters.getNetworkParameters())
//                                                .ifPresent(operatorStrategyResults::add);
//                                    } else {
//                                        // save post contingency state for later restoration after action
//                                        NetworkState postContingencyNetworkState = NetworkState.save(lfNetwork);
//                                        for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
//                                            runActionSimulation(lfNetwork, context,
//                                                    operatorStrategy, preContingencyLimitViolationManager,
//                                                    securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
//                                                    createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
//                                                    acParameters.getNetworkParameters())
//                                                    .ifPresent(result -> {
//                                                        operatorStrategyResults.add(result);
//                                                        postContingencyNetworkState.restore();
//                                                    });
//                                        }
//                                    }
//                                }
//
//                            if (contingencyIt.hasNext()) {
//                                // restore base state
//                                networkState.restore();
//                            }
//                        });
//            }

//            return new SecurityAnalysisResult(
//                    new PreContingencyResult(
//                            preContingencyLoadFlowResult.toComponentResultStatus().status(),
//                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
//                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
//                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
//                    postContingencyResults, operatorStrategyResults);

            return new SecurityAnalysisResult(
                    new PreContingencyResult(
                            LoadFlowResult.ComponentResult.Status.CONVERGED, // FIXME : should depend on result of Woodbury engine
                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
                    postContingencyResults, new ArrayList<>());
        }
    }

}
