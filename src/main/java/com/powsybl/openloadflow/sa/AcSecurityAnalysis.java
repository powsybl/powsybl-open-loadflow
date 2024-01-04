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
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisReport;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.action.Action;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcSecurityAnalysis extends AbstractSecurityAnalysis<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowContext, AcLoadFlowResult> {

    protected AcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, Reporter reporter) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reporter);
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        var saReporter = Reports.createAcSecurityAnalysis(reporter, network.getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        OpenSecurityAnalysisParameters securityAnalysisParametersExt = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);

        // check actions validity
        checkActions(network, actions);

        // try for find all switches to be operated as actions.
        LfTopoConfig topoConfig = new LfTopoConfig();
        findAllSwitchesToOperate(network, actions, topoConfig);

        // try to find all pst and rtc to retain because involved in pst and rtc actions
        findAllPtcToOperate(actions, topoConfig);
        findAllRtcToOperate(actions, topoConfig);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);
        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setContingencyPropagation(securityAnalysisParametersExt.isContingencyPropagation())
                .setShuntCompensatorVoltageControlOn(lfParameters.isShuntCompensatorVoltageControlOn())
                .setSlackDistributionOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setHvdcAcEmulation(lfParameters.isHvdcAcEmulation());

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);

        boolean breakers = topoConfig.isBreaker();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, breakers, false);
        acParameters.getNetworkParameters().setCacheEnabled(false); // force not caching as not supported in secu analysis
        acParameters.setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SECURITY_ANALYSIS));

        // create networks including all necessary switches
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), topoConfig, saReporter)) {
            // run simulation on largest network
            SecurityAnalysisResult result = lfNetworks.getLargest().filter(LfNetwork::isValid)
                    .map(largestNetwork -> runSimulations(largestNetwork, propagatedContingencies, acParameters, securityAnalysisParameters, operatorStrategies, actions))
                    .orElse(createNoResult());

            stopwatch.stop();
            LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                    stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return new SecurityAnalysisReport(result);
        }
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
    protected void hackParametersAfterPreContingencySimulation(AcLoadFlowParameters acParameters) {
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, no exception should be thrown. If parameters were configured to throw, reconfigure to FAIL.
        // (the contingency will be marked as not converged)
        if (OpenLoadFlowParameters.SlackDistributionFailureBehavior.THROW == acParameters.getSlackDistributionFailureBehavior()) {
            acParameters.setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL);
        }
    }

    @Override
    protected double calculateMismatch(LfNetwork lfNetwork, LfContingency lfContingency) {
        return lfContingency.getActivePowerLoss();
    }

    public static PostContingencyComputationStatus postContingencyStatusFromAcLoadFlowResult(AcLoadFlowResult result) {
        if (result.getOuterLoopStatus() == OuterLoopStatus.UNSTABLE) {
            return PostContingencyComputationStatus.MAX_ITERATION_REACHED;
        } else if (result.getOuterLoopStatus() == OuterLoopStatus.FAILED) {
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
}
