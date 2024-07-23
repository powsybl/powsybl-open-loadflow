package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.sensi.ComputedContingencyElement;
import com.powsybl.openloadflow.sensi.ConnectivityBreakAnalysis;
import com.powsybl.openloadflow.sensi.DcSensitivityAnalysis;
import com.powsybl.openloadflow.sensi.WoodburyEngine;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.limitreduction.LimitReduction;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.ConnectivityResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.initStateVector;
import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.updateNetwork;
import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;

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
        normalizeParticipationFactors(participatingElements);
        return participatingElements;
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

    /**
     * Calculate sensitivity values for a contingency.
     * In case of connectivity break, a pre-computation has been done in TODO
     * to get a first version of the new participating elements, that can be overridden in this method, and to indicate
     * if the factorsStates should be overridden or not in this method.
     * If connectivity, a generator, a load or a phase tap changer is lost due to the contingency,
     * the flowStates are overridden.
     */
    private DenseMatrix calculatePostContingencyStatesForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, DenseMatrix contingenciesStates, DenseMatrix flowStates,
                                                                      PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                      Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements, Set<String> elementsToReconnect,
                                                                      ReportNode reportNode, Set<LfBranch> partialDisabledBranches) {

        List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(contingencyElementByBranch::get)
                .collect(Collectors.toList());

        var lfNetwork = loadFlowContext.getNetwork();
        Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
        disabledBranches.addAll(partialDisabledBranches);
        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);

        WoodburyEngine engine = new WoodburyEngine(loadFlowContext.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates);
        DenseMatrix postContingencyStates;
        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
            DenseMatrix newFlowStates = flowStates;

            // get the lost phase tap changers for this contingency
            Set<LfBranch> lostPhaseControllers = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .map(ComputedContingencyElement::getLfBranch)
                    .filter(LfBranch::hasPhaseControllerCapability)
                    .collect(Collectors.toSet());

            // if a phase tap changer is lost or if the connectivity have changed, we must recompute load flows
            if (!disabledBuses.isEmpty() || !lostPhaseControllers.isEmpty()) {
                newFlowStates = DcSensitivityAnalysis.calculateFlowStates(loadFlowContext, participatingElements, disabledNetwork, reportNode);
            }
            postContingencyStates = engine.run(newFlowStates);
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
            NetworkState networkState = NetworkState.save(lfNetwork);
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            boolean participatingElementsChanged;
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            if (lfContingency != null) {
                lfContingency.apply(lfParameters.getBalanceType());
                participatingElementsChanged = isDistributedSlackOnGenerators(lfParameters) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || isDistributedSlackOnLoads(lfParameters) && !contingency.getLoadIdsToLoose().isEmpty();
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
            }

            DenseMatrix newFlowStates = DcSensitivityAnalysis.calculateFlowStates(loadFlowContext, newParticipatingElements, disabledNetwork, reportNode);
            postContingencyStates = engine.run(newFlowStates);
            networkState.restore();
        }
        return postContingencyStates;
    }

    // TODO : remove this method after woodbury refactoring
    private DenseMatrix processContingencyBreakingConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                          LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                          List<ParticipatingElement> participatingElements,
                                                          Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                          DenseMatrix flowStates, DenseMatrix contingenciesStates,
                                                          ReportNode reportNode) {

        PropagatedContingency contingency = connectivityAnalysisResult.getPropagatedContingency();
        Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();
        Set<LfBranch> partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();

        // as we are processing a contingency with connectivity break, we have to reset active power flow of a hvdc line
        // if one bus of the line is lost.
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
            }
        }

        List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
        boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
        if (lfParameters.isDistributedSlack()) {
            rhsChanged = participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
        }

        // we need to recompute the participating elements because the connectivity changed
        if (rhsChanged) {
            participatingElementsForThisConnectivity = lfParameters.isDistributedSlack()
                    ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                    : Collections.emptyList();
        }

        return calculatePostContingencyStatesForAContingency(loadFlowContext, lfParametersExt, contingenciesStates, flowStates,
                contingency, contingencyElementByBranch, disabledBuses, participatingElementsForThisConnectivity,
                connectivityAnalysisResult.getElementsToReconnect(), reportNode, partialDisabledBranches);
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
                                                    List<Action> actions, List<LimitReduction> limitReductions) {
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
            ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData = ConnectivityBreakAnalysis.run(context, null, propagatedContingencies, null);

            // no participating element for now
            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution)
            List<ParticipatingElement> participatingElements = loadFlowParameters.isDistributedSlack()
                    ? getParticipatingElements(lfNetwork.getBuses(), loadFlowParameters.getBalanceType(), openLoadFlowParameters)
                    : Collections.emptyList();

            double[] preContingencyFlowRhsArray = DcSensitivityAnalysis.runDcLoadFlow(context, new DisabledNetwork(), reportNode);

            // set pre contingency angle states as state vector of equation system
            context.getEquationSystem().getStateVector().set(preContingencyFlowRhsArray);

            // Update network voltages with pre contingency states
            updateNetwork(lfNetwork, context.getEquationSystem(), preContingencyFlowRhsArray);
            if (context.getParameters().isSetVToNan()) {
                for (LfBus bus : lfNetwork.getBuses()) {
                    bus.setV(Double.NaN);
                }
            }

            // update network result
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
            preContingencyNetworkResult.update();

            // detect violations
            var preContingencyLimitViolationManager = new LimitViolationManager(limitReductions);
            preContingencyLimitViolationManager.detectViolations(lfNetwork);

            // compute states with +1 -1 to model the contingencies and run connectivity analysis
            ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityBreakAnalysisResults = ConnectivityBreakAnalysis.run(context, null, propagatedContingencies, null);

            NetworkState networkState = NetworkState.save(lfNetwork);

            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            LOGGER.info("Processing contingencies with no connectivity break");
            for (PropagatedContingency contingency : connectivityBreakAnalysisResults.nonBreakingConnectivityContingencies()) {
                // TODO : refactor in a method
                contingency.toLfContingency(lfNetwork)
                        .ifPresent(lfContingency -> { // only process contingencies that impact the network
                            DenseMatrix postContingencyStates = calculatePostContingencyStatesForAContingency(context, openLoadFlowParameters, connectivityBreakAnalysisResults.contingenciesStates(), new DenseMatrix(preContingencyFlowRhsArray.length, 1, preContingencyFlowRhsArray), contingency,
                                    connectivityBreakAnalysisResults.contingencyElementByBranch(), Collections.emptySet(), participatingElements, Collections.emptySet(), reportNode, Collections.emptySet());
                            LOGGER.info("Start post contingency '{}' violations detection on network {}", lfContingency.getId(), network);
                            LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift {} loads",
                                    lfContingency.getId(), network, lfContingency.getDisabledNetwork().getBuses(), lfContingency.getDisabledNetwork().getBranchesStatus(),
                                    lfContingency.getLostGenerators(), lfContingency.getShuntsShift(), lfContingency.getLostLoads());

                            lfContingency.apply(loadFlowParameters.getBalanceType());

                            initStateVector(lfNetwork, context.getEquationSystem(), new UniformValueVoltageInitializer());
                            double[] postContingencyAngleStates = getAngleStatesAsArray(postContingencyStates);
                            context.getEquationSystem().getStateVector().set(postContingencyAngleStates);

                            var postContingencyNetworkResult = new PostContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency.getContingency());
                            postContingencyNetworkResult.update();

                            // detect violations
                            var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions, securityAnalysisParameters.getIncreasedViolationsParameters());
                            postContingencyLimitViolationManager.detectViolations(lfNetwork);

                            var connectivityResult = new ConnectivityResult(
                                    lfContingency.getCreatedSynchronousComponentsCount(), 0,
                                    lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                                    lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                                    lfContingency.getDisconnectedElementIds());

                            PostContingencyResult postContingencyResult = new PostContingencyResult(contingency.getContingency(),
                                    PostContingencyComputationStatus.CONVERGED, new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                                    postContingencyNetworkResult.getBranchResults(),
                                    postContingencyNetworkResult.getBusResults(),
                                    postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                                    connectivityResult);
                            postContingencyResults.add(postContingencyResult);

                            networkState.restore();
                        });

            }

            for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityBreakAnalysisResults.connectivityAnalysisResults()) {
                // TODO : refactor in a method
                connectivityAnalysisResult.getPropagatedContingency().toLfContingency(lfNetwork)
                        .ifPresent(lfContingency -> { // only process contingencies that impact the network
                            DenseMatrix postContingencyStates = processContingencyBreakingConnectivity(connectivityAnalysisResult, context, loadFlowParameters, openLoadFlowParameters, participatingElements,
                                    connectivityBreakAnalysisResults.contingencyElementByBranch(),
                                    new DenseMatrix(preContingencyFlowRhsArray.length, 1, preContingencyFlowRhsArray), connectivityBreakAnalysisResults.contingenciesStates(), reportNode);
                            LOGGER.info("Start post contingency '{}' violations detection on network {}", lfContingency.getId(), network);
                            LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift {} loads",
                                    lfContingency.getId(), network, lfContingency.getDisabledNetwork().getBuses(), lfContingency.getDisabledNetwork().getBranchesStatus(),
                                    lfContingency.getLostGenerators(), lfContingency.getShuntsShift(), lfContingency.getLostLoads());

                            lfContingency.apply(loadFlowParameters.getBalanceType());

                            initStateVector(lfNetwork, context.getEquationSystem(), new UniformValueVoltageInitializer());
                            double[] postContingencyAngleStates = getAngleStatesAsArray(postContingencyStates);
                            context.getEquationSystem().getStateVector().set(postContingencyAngleStates);

                            var postContingencyNetworkResult = new PostContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension, preContingencyNetworkResult, connectivityAnalysisResult.getPropagatedContingency().getContingency());
                            postContingencyNetworkResult.update();

                            // detect violations
                            var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, limitReductions, securityAnalysisParameters.getIncreasedViolationsParameters());
                            postContingencyLimitViolationManager.detectViolations(lfNetwork);

                            var connectivityResult = new ConnectivityResult(
                                    lfContingency.getCreatedSynchronousComponentsCount(), 0,
                                    lfContingency.getDisconnectedLoadActivePower() * PerUnit.SB,
                                    lfContingency.getDisconnectedGenerationActivePower() * PerUnit.SB,
                                    lfContingency.getDisconnectedElementIds());

                            PostContingencyResult postContingencyResult = new PostContingencyResult(connectivityAnalysisResult.getPropagatedContingency().getContingency(),
                                    PostContingencyComputationStatus.CONVERGED, new LimitViolationsResult(postContingencyLimitViolationManager.getLimitViolations()),
                                    postContingencyNetworkResult.getBranchResults(),
                                    postContingencyNetworkResult.getBusResults(),
                                    postContingencyNetworkResult.getThreeWindingsTransformerResults(),
                                    connectivityResult);
                            postContingencyResults.add(postContingencyResult);

                            networkState.restore();
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
