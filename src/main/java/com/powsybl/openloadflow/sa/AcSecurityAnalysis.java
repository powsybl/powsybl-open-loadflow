/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.action.SwitchAction;
import com.powsybl.security.condition.AllViolationCondition;
import com.powsybl.security.condition.AtLeastOneViolationCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AcSecurityAnalysis extends AbstractSecurityAnalysis {

    protected AcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, Reporter reporter) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reporter);
    }

    private static SecurityAnalysisResult createNoResult() {
        return new SecurityAnalysisResult(new LimitViolationsResult(false, Collections.emptyList()), Collections.emptyList());
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        var saReporter = Reports.createAcSecurityAnalysis(reporter, network.getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, the remaining mismatch is put on the slack bus and no exception is thrown.
        lfParametersExt.setThrowsExceptionInCaseOfSlackDistributionFailure(false);
        OpenSecurityAnalysisParameters securityAnalysisParametersExt = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSecurityAnalysis(network, contingencies, allSwitchesToOpen,
                lfParameters.isShuntCompensatorVoltageControlOn(), lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                lfParameters.isHvdcAcEmulation(), securityAnalysisParametersExt.isContingencyPropagation());

        // try for find all switches to be operated as actions.
        Set<Switch> allSwitchesToClose = new HashSet<>();
        getAllSwitchesToOperate(network, actions, allSwitchesToClose, allSwitchesToOpen);

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, saReporter, true, false);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, allSwitchesToClose, acParameters.getNetworkParameters(), saReporter);

        // run simulation on largest network
        SecurityAnalysisResult result;
        if (lfNetworks.isEmpty()) {
            result = createNoResult();
        } else {
            LfNetwork largestNetwork = lfNetworks.get(0);
            if (largestNetwork.isValid()) {
                HashMap<String, LfAction> lfActionById = getLfActions(largestNetwork, actions);
                HashMap<String, OperatorStrategy> operatorStrategyByContingencyId = indexOperatorStrategyByContingencyId(propagatedContingencies, operatorStrategies);
                result = runSimulations(largestNetwork, propagatedContingencies, acParameters, securityAnalysisParameters, operatorStrategyByContingencyId, lfActionById, allSwitchesToClose);

            } else {
                result = createNoResult();
            }
        }

        stopwatch.stop();
        LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new SecurityAnalysisReport(result);
    }

    List<LfNetwork> createNetworks(Set<Switch> allSwitchesToOpen, Set<Switch> allSwitchesToClose, LfNetworkParameters networkParameters,
                                   Reporter saReporter) {
        List<LfNetwork> lfNetworks;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID();
        String variantId = network.getVariantManager().getWorkingVariantId();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        network.getVariantManager().setWorkingVariant(tmpVariantId);
        try {
            network.getSwitchStream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                    .forEach(sw -> sw.setRetained(false));
            allSwitchesToOpen.forEach(sw -> sw.setRetained(true));
            allSwitchesToClose.forEach(sw -> {
                sw.setRetained(true);
                sw.setOpen(false); // in order to be present in the network.
            });
            lfNetworks = Networks.load(network, networkParameters, saReporter);
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
            network.getVariantManager().setWorkingVariant(variantId);
        }
        return lfNetworks;
    }

    public static void distributedMismatch(LfNetwork network, double mismatch, LoadFlowParameters loadFlowParameters,
                                           OpenLoadFlowParameters openLoadFlowParameters) {
        if (loadFlowParameters.isDistributedSlack() && Math.abs(mismatch) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant());
            activePowerDistribution.run(network, mismatch);
        }
    }

    public static void getAllSwitchesToOperate(Network network, List<Action> actions, Set<Switch> allSwitchesToClose, Set<Switch> allSwitchesToOpen) {
        actions.stream().filter(action -> action.getType().equals("SWITCH"))
                .map(action -> ((SwitchAction) action).getSwitchId())
                .forEach(id -> {
                    Switch sw = network.getSwitch(id);
                    if (sw.isOpen()) {
                        allSwitchesToClose.add(sw);
                    } else {
                        allSwitchesToOpen.add(sw);
                    }
                });
    }

    public static HashMap<String, LfAction> getLfActions(LfNetwork network, List<Action> actions) {
        return new HashMap(actions.stream().collect(Collectors.toMap(Action::getId, action -> new LfAction(action, network))));
    }

    public static HashMap<String, OperatorStrategy> indexOperatorStrategyByContingencyId(List<PropagatedContingency> propagatedContingencies, List<OperatorStrategy> operatorStrategies) {
        List<String> contingencyIds = propagatedContingencies.stream().map(propagatedContingency -> propagatedContingency.getContingency().getId()).collect(Collectors.toList());
        HashMap<String, OperatorStrategy> operatorStrategyByContingencyId = new HashMap<>();
        for (OperatorStrategy operatorStrategy : operatorStrategies) {
            if (contingencyIds.contains(operatorStrategy.getContingencyId())) {
                operatorStrategyByContingencyId.put(operatorStrategy.getContingencyId(), operatorStrategy);
            } else {
                LOGGER.warn("An operator strategy linked to Contingency {} that is not present in the list of Contingencies", operatorStrategy.getContingencyId());
            }
        }
        return operatorStrategyByContingencyId;
    }

    private SecurityAnalysisResult runSimulations(LfNetwork network, List<PropagatedContingency> propagatedContingencies, AcLoadFlowParameters acParameters,
                                                  SecurityAnalysisParameters securityAnalysisParameters, HashMap<String, OperatorStrategy> operatorStrategyByContingencyId,
                                                  HashMap<String, LfAction> lfActionById, Set<Switch> allSwitchesToClose) {
        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (AcLoadFlowContext context = new AcLoadFlowContext(network, acParameters)) {
            Reporter networkReporter = network.getReporter();
            Reporter preContSimReporter = Reports.createPreContingencySimulation(networkReporter);
            network.setReporter(preContSimReporter);

            // run pre-contingency simulation
            // FIXME
            // in case of switches to close, the first run is need to create the equation system and the listener
            // needed to disabled the switches for the pre-contingency simulation. The connectivity is updated to be
            // conform to the pre-contingency state.
            // AcLoadFlowResult preContingencyLoadFlowResult = new AcloadFlowEngine(context)
            //        .run();
            AcloadFlowEngine engine = new AcloadFlowEngine(context);
            AcLoadFlowResult preContingencyLoadFlowResult = engine.run();
            if (!allSwitchesToClose.isEmpty()) {
                var connectivity = network.getConnectivity();
                allSwitchesToClose.stream().map(Identifiable::getId).forEach(id -> {
                    LfBranch branch = network.getBranchById(id);
                    branch.setDisabled(true);
                    if (branch.getBus1() != null && branch.getBus2() != null) {
                        connectivity.removeEdge(branch);
                    }
                });
                preContingencyLoadFlowResult = engine.run();
            }

            boolean preContingencyComputationOk = preContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            var preContingencyLimitViolationManager = new LimitViolationManager();
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            var preContingencyNetworkResult = new PreContingencyNetworkResult(network, monitorIndex, createResultExtension);
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            if (preContingencyComputationOk) {
                // update network result
                preContingencyNetworkResult.update();

                // detect violations
                preContingencyLimitViolationManager.detectViolations(network);

                // save base state for later restoration after each contingency
                NetworkState networkState = NetworkState.save(network);

                // start a simulation for each of the contingency
                Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
                while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
                    PropagatedContingency propagatedContingency = contingencyIt.next();
                    propagatedContingency.toLfContingency(network, true)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                Reporter postContSimReporter = Reports.createPostContingencySimulation(networkReporter, lfContingency.getId());
                                network.setReporter(postContSimReporter);

                                lfContingency.apply(loadFlowParameters.getBalanceType());

                                distributedMismatch(network, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                                var postContingencyResult = runPostContingencySimulation(network, context, propagatedContingency.getContingency(),
                                                                                         lfContingency, preContingencyLimitViolationManager,
                                                                                         securityAnalysisParameters.getIncreasedViolationsParameters(),
                                                                                         preContingencyNetworkResult, createResultExtension);
                                postContingencyResults.add(postContingencyResult);

                                if (operatorStrategyByContingencyId.get(lfContingency.getId()) != null) {
                                    // we have an operator strategy for this contingency.
                                    // FIXME: if several strategies exist?
                                    Optional<OperatorStrategyResult> optionalOperatorStrategyResult = runActionSimulation(network, context, propagatedContingency.getContingency(),
                                            operatorStrategyByContingencyId.get(lfContingency.getId()), preContingencyLimitViolationManager,
                                            securityAnalysisParameters.getIncreasedViolationsParameters(), postContingencyResult.getLimitViolationsResult(), lfActionById,
                                            preContingencyNetworkResult, createResultExtension);
                                    if (optionalOperatorStrategyResult.isPresent()) {
                                        operatorStrategyResults.add(optionalOperatorStrategyResult.get());
                                    }
                                }

                                if (contingencyIt.hasNext()) {
                                    // restore base state
                                    networkState.restore();
                                }
                            });
                }
            }

            return new SecurityAnalysisResult(new LimitViolationsResult(preContingencyComputationOk,
                                                                        preContingencyLimitViolationManager.getLimitViolations()),
                                              postContingencyResults,
                                              preContingencyNetworkResult.getBranchResults(),
                                              preContingencyNetworkResult.getBusResults(),
                                              preContingencyNetworkResult.getThreeWindingsTransformerResults(),
                                              operatorStrategyResults);
        }
    }

    private PostContingencyResult runPostContingencySimulation(LfNetwork network, AcLoadFlowContext context, Contingency contingency, LfContingency lfContingency,
                                                               LimitViolationManager preContingencyLimitViolationManager,
                                                               SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                               PreContingencyNetworkResult preContingencyNetworkResult, boolean createResultExtension) {
        LOGGER.info("Start post contingency '{}' simulation on network {}", lfContingency.getId(), network);
        LOGGER.debug("Contingency '{}' impact on network {}: remove {} buses, remove {} branches, remove {} generators, shift {} shunts, shift load of {} buses",
                lfContingency.getId(), network, lfContingency.getDisabledBuses(), lfContingency.getDisabledBranches(), lfContingency.getLostGenerators(),
                lfContingency.getShuntsShift(), lfContingency.getBusesLoadShift());

        Stopwatch stopwatch = Stopwatch.createStarted();

        // restart LF on post contingency equation system
        context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        AcLoadFlowResult postContingencyLoadFlowResult = new AcloadFlowEngine(context)
                .run();

        boolean postContingencyComputationOk = postContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        var postContingencyLimitViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, violationsParameters);
        var postContingencyNetworkResult = new PostContingencyNetworkResult(network, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency);

        if (postContingencyComputationOk) {
            // update network result
            postContingencyNetworkResult.update();

            // detect violations
            postContingencyLimitViolationManager.detectViolations(network);
        }

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done on network {} in {} ms", lfContingency.getId(),
                network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(contingency,
                                         new LimitViolationsResult(postContingencyComputationOk,
                                                                   postContingencyLimitViolationManager.getLimitViolations()),
                                         postContingencyNetworkResult.getBranchResults(),
                                         postContingencyNetworkResult.getBusResults(),
                                         postContingencyNetworkResult.getThreeWindingsTransformerResults());
    }

    private Optional<OperatorStrategyResult> runActionSimulation(LfNetwork network, AcLoadFlowContext context, Contingency contingency, OperatorStrategy operatorStrategy,
                                                                 LimitViolationManager preContingencyLimitViolationManager,
                                                                 SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters,
                                                                 LimitViolationsResult postContingencyLimitViolations, HashMap<String, LfAction> lfActionById,
                                                                 PreContingencyNetworkResult preContingencyNetworkResult, boolean createResultExtension) {

        Optional<OperatorStrategyResult> optionalOperatorStrategyResult = Optional.empty();

        if (checkCondition(operatorStrategy, postContingencyLimitViolations)) {
            LOGGER.info("Start operator strategy {} after contingency '{}' simulation on network {}", operatorStrategy.getId(),
                    operatorStrategy.getContingencyId(), network);

            List<LfAction> operatorStrategyLfActions = operatorStrategy.getActionIds().stream()
                    .map(id -> lfActionById.getOrDefault(id, null)).collect(Collectors.toList()); // FIXME: null as default value?
            operatorStrategyLfActions.stream().forEach(LfAction::apply);

            Stopwatch stopwatch = Stopwatch.createStarted();

            // restart LF on post contingency and post actions equation system
            context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
            AcLoadFlowResult postActionsLoadFlowResult = new AcloadFlowEngine(context)
                    .run();

            boolean postActionsComputationOk = postActionsLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            // FIXME: to be checked.
            var postActionsViolationManager = new LimitViolationManager(preContingencyLimitViolationManager, violationsParameters);
            var postActionsNetworkResult = new PostContingencyNetworkResult(network, monitorIndex, createResultExtension, preContingencyNetworkResult, contingency);

            if (postActionsComputationOk) {
                // update network result
                postActionsNetworkResult.update();

                // detect violations
                postActionsViolationManager.detectViolations(network);
            }

            stopwatch.stop();

            LOGGER.info("Operator strategy {} after contingency '{}' simulation done on network {} in {} ms", operatorStrategy.getId(),
                    operatorStrategy.getContingencyId(), network, stopwatch.elapsed(TimeUnit.MILLISECONDS));

            optionalOperatorStrategyResult = Optional.of(new OperatorStrategyResult(operatorStrategy,
                    new LimitViolationsResult(postActionsComputationOk,
                            postActionsViolationManager.getLimitViolations()),
                    new NetworkResult(postActionsNetworkResult.getBranchResults(),
                            postActionsNetworkResult.getBusResults(),
                            postActionsNetworkResult.getThreeWindingsTransformerResults())));
        }

        return optionalOperatorStrategyResult;
    }

    private boolean checkCondition(OperatorStrategy operatorStrategy, LimitViolationsResult limitViolationsResult) {
        // FIXME: add logs.
        HashSet<String> limitViolationEquipmentIds = (HashSet<String>) limitViolationsResult.getLimitViolations().stream()
                .map(LimitViolation::getSubjectId).collect(Collectors.toSet());
        HashSet<String> commonEquipmentIds;
        switch (operatorStrategy.getCondition().getType()) {
            case "TRUE_CONDITION":
                return true;
            case "ANY_VIOLATION_CONDITION":
                return !limitViolationEquipmentIds.isEmpty();
            case "AT_LEAST_ONE_VIOLATION":
                AtLeastOneViolationCondition atLeastCondition = (AtLeastOneViolationCondition) operatorStrategy.getCondition();
                commonEquipmentIds = (HashSet<String>) atLeastCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return !commonEquipmentIds.isEmpty();
            case "ALL_VIOLATION":
                AllViolationCondition allCondition = (AllViolationCondition) operatorStrategy.getCondition();
                commonEquipmentIds = (HashSet<String>) allCondition.getViolationIds().stream()
                        .distinct()
                        .filter(limitViolationEquipmentIds::contains)
                        .collect(Collectors.toSet());
                return commonEquipmentIds.equals(allCondition.getViolationIds());
            default:
                throw new UnsupportedOperationException("Unsupported condition type: " + operatorStrategy.getCondition().getType());
        }
    }
}
