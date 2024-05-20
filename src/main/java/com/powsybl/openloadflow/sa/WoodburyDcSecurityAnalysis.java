package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
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

    @Override
    protected SecurityAnalysisResult runSimulations(LfNetwork lfNetwork, List<PropagatedContingency> propagatedContingencies, DcLoadFlowParameters acParameters,
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

        try (DcLoadFlowContext context = createLoadFlowContext(lfNetwork, acParameters)) {
            ReportNode networkReportNode = lfNetwork.getReportNode();
            ReportNode preContSimReportNode = Reports.createPreContingencySimulation(networkReportNode);
            lfNetwork.setReportNode(preContSimReportNode);

            // run pre-contingency simulation
            DcLoadFlowResult preContingencyLoadFlowResult = createLoadFlowEngine(context)
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
