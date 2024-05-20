/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcSecurityAnalysis extends AbstractSecurityAnalysis<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcLoadFlowResult> {

    protected AcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, ReportNode reportNode) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reportNode);
    }

    @Override
    protected ReportNode createSaRootReportNode() {
        return Reports.createAcSecurityAnalysis(reportNode, network.getId());
    }

    @Override
    protected boolean isShuntCompensatorVoltageControlOn(LoadFlowParameters lfParameters) {
        return lfParameters.isShuntCompensatorVoltageControlOn();
    }

    @Override
    protected AcLoadFlowParameters createParameters(LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers) {
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, breakers, false);
        acParameters.getNetworkParameters()
                .setCacheEnabled(false) // force not caching as not supported in secu analysis
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR); // not supported yet
        acParameters.setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SECURITY_ANALYSIS));
        return acParameters;
    }

    @Override
    protected AcLoadFlowContext createLoadFlowContext(LfNetwork lfNetwork, AcLoadFlowParameters parameters) {
        return new AcLoadFlowContext(lfNetwork, parameters);
    }

    @Override
    protected AcloadFlowEngine createLoadFlowEngine(AcLoadFlowContext context) {
        return new AcloadFlowEngine(context);
    }

    @Override
    protected void afterPreContingencySimulation(AcLoadFlowParameters acParameters) {
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, no exception should be thrown. If parameters were configured to throw, reconfigure to FAIL.
        // (the contingency will be marked as not converged)
        if (OpenLoadFlowParameters.SlackDistributionFailureBehavior.THROW == acParameters.getSlackDistributionFailureBehavior()) {
            acParameters.setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL);
        }
    }

    public static PostContingencyComputationStatus postContingencyStatusFromAcLoadFlowResult(AcLoadFlowResult result) {
        if (result.getOuterLoopResult().status() == OuterLoopStatus.UNSTABLE) {
            return PostContingencyComputationStatus.MAX_ITERATION_REACHED;
        } else if (result.getOuterLoopResult().status() == OuterLoopStatus.FAILED) {
            return PostContingencyComputationStatus.FAILED;
        } else {
            return switch (result.getSolverStatus()) {
                case CONVERGED -> PostContingencyComputationStatus.CONVERGED;
                case MAX_ITERATION_REACHED -> PostContingencyComputationStatus.MAX_ITERATION_REACHED;
                case SOLVER_FAILED -> PostContingencyComputationStatus.SOLVER_FAILED;
                case NO_CALCULATION -> PostContingencyComputationStatus.NO_IMPACT;
                case UNREALISTIC_STATE -> PostContingencyComputationStatus.FAILED;
            };
        }
    }

    @Override
    protected PostContingencyComputationStatus postContingencyStatusFromLoadFlowResult(AcLoadFlowResult result) {
        return postContingencyStatusFromAcLoadFlowResult(result);
    }

    @Override
    protected void beforeActionLoadFlowRun(AcLoadFlowContext context) {
        context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer(true));
    }

    @Override
    protected SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies, AcLoadFlowParameters acParameters,
                                                    SecurityAnalysisParameters securityAnalysisParameters, List<OperatorStrategy> operatorStrategies,
                                                    List<Action> actions) {
        Map<String, Action> actionsById = indexActionsById(actions);
        Set<Action> neededActions = new HashSet<>(actionsById.size());
        Map<String, List<OperatorStrategy>> operatorStrategiesByContingencyId = indexOperatorStrategiesByContingencyId(propagatedContingencies, operatorStrategies, actionsById, neededActions);
        Map<String, LfAction> lfActionById = createLfActions(lfNetwork, neededActions, network, acParameters.getNetworkParameters()); // only convert needed actions

        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.get(loadFlowParameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);
        boolean createResultExtension = openSecurityAnalysisParameters.isCreateResultExtension();

        try (AcLoadFlowContext context = createLoadFlowContext(lfNetwork, acParameters)) {
            ReportNode networkReportNode = lfNetwork.getReportNode();
            ReportNode preContSimReportNode = Reports.createPreContingencySimulation(networkReportNode);
            lfNetwork.setReportNode(preContSimReportNode);

            // run pre-contingency simulation
            AcLoadFlowResult preContingencyLoadFlowResult = createLoadFlowEngine(context)
                    .run();

            boolean preContingencyComputationOk = preContingencyLoadFlowResult.isSuccess();
            var preContingencyLimitViolationManager = new LimitViolationManager();
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            var preContingencyNetworkResult = new PreContingencyNetworkResult(lfNetwork, monitorIndex, createResultExtension);
            List<OperatorStrategyResult> operatorStrategyResults = new ArrayList<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            if (preContingencyComputationOk) {
                afterPreContingencySimulation(acParameters);

                // update network result
                preContingencyNetworkResult.update();

                // detect violations
                preContingencyLimitViolationManager.detectViolations(lfNetwork);

                // save base state for later restoration after each contingency
                NetworkState networkState = NetworkState.save(lfNetwork);

                // start a simulation for each of the contingency
                Iterator<PropagatedContingency> contingencyIt = propagatedContingencies.iterator();
                while (contingencyIt.hasNext() && !Thread.currentThread().isInterrupted()) {
                    PropagatedContingency propagatedContingency = contingencyIt.next();
                    propagatedContingency.toLfContingency(lfNetwork)
                            .ifPresent(lfContingency -> { // only process contingencies that impact the network
                                ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfContingency.getId());
                                lfNetwork.setReportNode(postContSimReportNode);

                                lfContingency.apply(loadFlowParameters.getBalanceType());

                                distributedMismatch(lfNetwork, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                                var postContingencyResult = runPostContingencySimulation(lfNetwork, context, propagatedContingency.getContingency(),
                                        lfContingency, preContingencyLimitViolationManager,
                                        securityAnalysisParameters.getIncreasedViolationsParameters(),
                                        preContingencyNetworkResult, createResultExtension);
                                postContingencyResults.add(postContingencyResult);

                                List<OperatorStrategy> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId.get(lfContingency.getId());
                                if (operatorStrategiesForThisContingency != null) {
                                    // we have at least an operator strategy for this contingency.
                                    if (operatorStrategiesForThisContingency.size() == 1) {
                                        runActionSimulation(lfNetwork, context,
                                                operatorStrategiesForThisContingency.get(0), preContingencyLimitViolationManager,
                                                securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
                                                createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
                                                acParameters.getNetworkParameters())
                                                .ifPresent(operatorStrategyResults::add);
                                    } else {
                                        // save post contingency state for later restoration after action
                                        NetworkState postContingencyNetworkState = NetworkState.save(lfNetwork);
                                        for (OperatorStrategy operatorStrategy : operatorStrategiesForThisContingency) {
                                            runActionSimulation(lfNetwork, context,
                                                    operatorStrategy, preContingencyLimitViolationManager,
                                                    securityAnalysisParameters.getIncreasedViolationsParameters(), lfActionById,
                                                    createResultExtension, lfContingency, postContingencyResult.getLimitViolationsResult(),
                                                    acParameters.getNetworkParameters())
                                                    .ifPresent(result -> {
                                                        operatorStrategyResults.add(result);
                                                        postContingencyNetworkState.restore();
                                                    });
                                        }
                                    }
                                }

                                if (contingencyIt.hasNext()) {
                                    // restore base state
                                    networkState.restore();
                                }
                            });
                }
            }

            return new SecurityAnalysisResult(
                    new PreContingencyResult(
                            preContingencyLoadFlowResult.toComponentResultStatus().status(),
                            new LimitViolationsResult(preContingencyLimitViolationManager.getLimitViolations()),
                            preContingencyNetworkResult.getBranchResults(), preContingencyNetworkResult.getBusResults(),
                            preContingencyNetworkResult.getThreeWindingsTransformerResults()),
                    postContingencyResults, operatorStrategyResults);
        }
    }
}
