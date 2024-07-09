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
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
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

public class WoodburyDcSecurityAnalysis extends DcSecurityAnalysis {

    protected WoodburyDcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, ReportNode reportNode) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reportNode);
    }

    @Override
    protected ReportNode createSaRootReportNode() {
        return Reports.createWoodburyDcSecurityAnalysis(reportNode, network.getId());
    }

    // TODO : remove this method after woodbury refactoring
    protected List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType, OpenLoadFlowParameters openLoadFlowParameters) {
        ActivePowerDistribution.Step step = ActivePowerDistribution.getStep(balanceType, openLoadFlowParameters.isLoadPowerFactorConstant(), openLoadFlowParameters.isUseActiveLimits());
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);
        ParticipatingElement.normalizeParticipationFactors(participatingElements);
        return participatingElements;
    }

    // TODO : remove this method after woodbury refactoring
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

    // TODO : remove this method after woodbury refactoring
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

    // TODO : remove this method after woodbury refactoring
    public static boolean isDistributedSlackOnGenerators(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
    }

    // TODO : remove this method after woodbury refactoring
    public static boolean isDistributedSlackOnLoads(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
    }

    // TODO : remove this method after woodbury refactoring
    protected void buildRhsModificationsForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                      PropagatedContingency contingency, List<ParticipatingElement> participatingElements,
                                                      WoodburyEngineRhsModifications flowRhsModifications, DisabledNetwork disabledNetwork) {

        if (!(contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty())) {
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
     * TODO : remove this method after woodbury refactoring
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

    // TODO : remove this method after woodbury refactoring
    private void processHvdcLinesWithDisconnection(DcLoadFlowContext loadFlowContext, Set<LfBus> disabledBuses, ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                });
            }
        }
    }

    /**
     * Compute right hand side overrides for groups of contingencies breaking connectivity.
     * // TODO : remove this method after woodbury refactoring
     */
    private void buildRhsModificationsForContingenciesBreakingConnectivity(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityDataResult,
                                                                           List<ParticipatingElement> participatingElements,
                                                                           WoodburyEngineRhsModifications injectionRhsModifications, WoodburyEngineRhsModifications flowRhsModifications, HashMap<PropagatedContingency, DisabledNetwork> disabledNetworksByPropagatedContingencies,
                                                                           SensitivityResultWriter resultWriter) {
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();

        // Loop on the different connectivity schemas among the post-contingency states
        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityDataResult.connectivityAnalysisResults()) {
            Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();

            // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
            // if one bus of the line is lost.
            processHvdcLinesWithDisconnection(loadFlowContext, disabledBuses, connectivityAnalysisResult);

            // null and unused if slack bus is not distributed
            List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
            boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
            if (lfParameters.isDistributedSlack()) {
                rhsChanged = participatingElementsForThisConnectivity.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
            }

            // we need to recompute the injection rhs because the connectivity changed
            if (rhsChanged) {
                participatingElementsForThisConnectivity = new ArrayList<>(lfParameters.isDistributedSlack()
                        ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                        : Collections.emptyList());
            }

            DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, Collections.emptySet());
            // recompute the flow rhs
            DenseMatrix flowRhsOverride = getPreContingencyFlowRhs(loadFlowContext, participatingElementsForThisConnectivity, disabledNetwork);
            flowRhsModifications.addRhsOverrideForAConnectivity(connectivityAnalysisResult, flowRhsOverride);

            // Build rhs modifications for each contingency bringing this connectivity schema
            buildRhsModificationsForContingencies(loadFlowContext, lfParametersExt, connectivityAnalysisResult.getContingencies(), participatingElementsForThisConnectivity, null, flowRhsModifications,
                    disabledNetworksByPropagatedContingencies, disabledBuses, connectivityAnalysisResult.getPartialDisabledBranches(), connectivityDataResult.contingencyElementByBranch(),
                    connectivityAnalysisResult.getElementsToReconnect(), resultWriter);
        }
    }

    private double[] getAngleStatesAsArray(DenseMatrix angleStates) {
        if (angleStates.getColumnCount() > 1) {
            throw new PowsyblException("Angle states should be a DenseMatrix with 1 column");
        }
        double[] angleStatesArray = new double[angleStates.getRowCount()];
        for (int i = 0; i < angleStatesArray.length; i++) {
            angleStatesArray[i] = angleStates.get(i, 0);
        }
        return angleStatesArray;
    }

    private void filterPropagatedContingencies(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies) {

        // contingencies on switch not yet supported
        propagatedContingencies.stream()
                .flatMap(contingency -> contingency.getBranchIdsToOpen().keySet().stream())
                .map(branchId -> lfNetwork.getBranchById(branchId).getBranchType())
                .filter(branchType -> branchType == LfBranch.BranchType.SWITCH)
                .findAny()
                .ifPresent(e -> {
                    throw new IllegalArgumentException("Contingencies on switch not yet supported in fast DC Security Analysis");
                });

        // map contingencies on buses to contingencies on linked branches terminals
        propagatedContingencies.stream()
                .filter(contingency -> !contingency.getBusIdsToLose().isEmpty())
                .forEach(contingency -> {
                    for (String s : contingency.getBusIdsToLose()) {
                        for (LfBranch disabledBranch : lfNetwork.getBusById(s).getBranches()) {
                            DisabledBranchStatus status = disabledBranch.getBus1().getId().equals(s) ? DisabledBranchStatus.SIDE_1 : DisabledBranchStatus.SIDE_2;
                            contingency.getBranchIdsToOpen().put(disabledBranch.getId(), status);
                        }
                    }
                });
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

            // prepare contingencies for connectivity analysis and woodbury engine
            filterPropagatedContingencies(lfNetwork, propagatedContingencies);

            // connectivity analysis
            ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData = ConnectivityBreakAnalysis.run(context, propagatedContingencies);

            // no participating element for now
            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution)
            List<ParticipatingElement> participatingElements = loadFlowParameters.isDistributedSlack()
                    ? getParticipatingElements(lfNetwork.getBuses(), loadFlowParameters.getBalanceType(), openLoadFlowParameters)
                    : Collections.emptyList();

            // woodbury engine input
            DenseMatrix flowsRhs = getPreContingencyFlowRhs(context, participatingElements, new DisabledNetwork());
            HashMap<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency = new HashMap<>();
            WoodburyEngineRhsModifications flowRhsModifications = new WoodburyEngineRhsModifications();
            buildRhsModificationsForContingenciesBreakingConnectivity(context, openLoadFlowParameters, connectivityData, participatingElements,
                    null, flowRhsModifications, disabledNetworkByPropagatedContingency, null);
            buildRhsModificationsForContingencies(context, openLoadFlowParameters, connectivityData.nonBreakingConnectivityContingencies(),
                    participatingElements, null,
                    flowRhsModifications, disabledNetworkByPropagatedContingency, Collections.emptySet(), Collections.emptySet(),
                    connectivityData.contingencyElementByBranch(), Collections.emptySet(), null);

            // compute pre- and post-contingency flow states
            WoodburyEngine engine = new WoodburyEngine();
            WoodburyEngineResult angleStates = engine.run(context, flowsRhs, flowRhsModifications, connectivityData, reportNode);

            // set pre contingency angle states as state vector of equation system
            double[] preContingencyAnglesStates = getAngleStatesAsArray(angleStates.getPreContingencyStates());
            context.getEquationSystem().getStateVector().set(preContingencyAnglesStates);

            // Update network voltages with pre contingency states
            updateNetwork(lfNetwork, context.getEquationSystem(), preContingencyAnglesStates);
            if (context.getParameters().isSetVToNan()) {
                for (LfBus bus : lfNetwork.getBuses()) {
                    bus.setV(Double.NaN);
                }
            }

            // update network result
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
            preContingencyNetworkResult.update();

            // detect violations
            var preContingencyLimitViolationManager = new LimitViolationManager();
            preContingencyLimitViolationManager.detectViolations(lfNetwork);

            // save base state for later restoration after each contingency
            NetworkState networkState = NetworkState.save(lfNetwork);

            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
            while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
                PropagatedContingency propagatedContingency = contingencyIt.next();
                propagatedContingency.toLfContingency(lfNetwork)
                        .ifPresent(lfContingency -> { // only process contingencies that impact the network
                            LOGGER.info("Start post contingency '{}' violations detection on network {}", lfContingency.getId(), network);
                            LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift {} loads",
                                lfContingency.getId(), network, lfContingency.getDisabledNetwork().getBuses(), lfContingency.getDisabledNetwork().getBranchesStatus(),
                                lfContingency.getLostGenerators(), lfContingency.getShuntsShift(), lfContingency.getLostLoads());

                            lfContingency.apply(loadFlowParameters.getBalanceType());

                            initStateVector(lfNetwork, context.getEquationSystem(), new UniformValueVoltageInitializer());
                            double[] postContingencyAngleStates = getAngleStatesAsArray(angleStates.getPostContingencyWoodburyStates(propagatedContingency));
                            context.getEquationSystem().getStateVector().set(postContingencyAngleStates);

                            var postContingencyNetworkResult = new PostContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension, preContingencyNetworkResult, propagatedContingency.getContingency());
                            postContingencyNetworkResult.update();

                            // detect violations
                            var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, securityAnalysisParameters.getIncreasedViolationsParameters());
                            postContingencyLimitViolationManager.detectViolations(lfNetwork);

                            var connectivityResult = new ConnectivityResult(
                                    lfContingency.getCreatedSynchronousComponentsCount(), 0,
                                    lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                                    lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                                    lfContingency.getDisconnectedElementIds());

                            PostContingencyResult postContingencyResult = new PostContingencyResult(propagatedContingency.getContingency(),
                                    PostContingencyComputationStatus.CONVERGED, new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                                    postContingencyNetworkResult.getBranchResults(),
                                    postContingencyNetworkResult.getBusResults(),
                                    postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                                    connectivityResult);
                            postContingencyResults.add(postContingencyResult);

                            if (contingencyIt.hasNext()) {
                                // restore base state
                                networkState.restore();
                            }
                        });
            }

            return new SecurityAnalysisResult(
                    new PreContingencyResult(LoadFlowResult.ComponentResult.Status.CONVERGED, // if not converge, woodbury whould have throw first
                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
                    postContingencyResults, new ArrayList<>());
        }
    }

}
