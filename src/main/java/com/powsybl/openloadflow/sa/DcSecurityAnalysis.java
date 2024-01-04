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
import com.powsybl.contingency.*;
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
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DcSecurityAnalysis extends AbstractSecurityAnalysis<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcLoadFlowResult> {

    protected DcSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                 List<StateMonitor> stateMonitors, Reporter reporter) {
        super(network, matrixFactory, connectivityFactory, stateMonitors, reporter);
    }

    @Override
    SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                   ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        var saReporter = Reports.createDcSecurityAnalysis(reporter, network.getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(securityAnalysisParameters.getLoadFlowParameters());
        OpenSecurityAnalysisParameters securityAnalysisParametersExt = OpenSecurityAnalysisParameters.getOrDefault(securityAnalysisParameters);

        // check actions validity
        checkActions(network, actions);

        // try for find all switches to be operated as actions.
        LfTopoConfig topoConfig = new LfTopoConfig();
        findAllSwitchesToOperate(network, actions, topoConfig);

        // try to find all ptc to retain because involved in ptc actions
        findAllPtcToOperate(actions, topoConfig);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);
        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                .setContingencyPropagation(securityAnalysisParametersExt.isContingencyPropagation())
                .setShuntCompensatorVoltageControlOn(false)
                .setSlackDistributionOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setHvdcAcEmulation(false);

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);

        var dcParameters = OpenLoadFlowParameters.createDcParameters(network, lfParameters,
                lfParametersExt, matrixFactory, connectivityFactory, false);
        boolean breakers = topoConfig.isBreaker();
        dcParameters.getNetworkParameters()
                .setBreakers(breakers)
                .setCacheEnabled(false); // force not caching as not supported in security analysis

        // create networks including all necessary switches
        try (LfNetworkList lfNetworks = Networks.load(network, dcParameters.getNetworkParameters(), topoConfig, saReporter)) {
            // run simulation on largest network
            SecurityAnalysisResult result = lfNetworks.getLargest().filter(LfNetwork::isValid)
                    .map(largestNetwork -> runSimulations(largestNetwork, propagatedContingencies, dcParameters, securityAnalysisParameters, operatorStrategies, actions))
                    .orElse(createNoResult());

            stopwatch.stop();
            LOGGER.info("Security analysis {} in {} ms", Thread.currentThread().isInterrupted() ? "cancelled" : "done",
                    stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return new SecurityAnalysisReport(result);
        }
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
    protected double calculateMismatch(LfNetwork lfNetwork, LfContingency lfContingency) {
        return DcLoadFlowEngine.getActivePowerMismatch(lfNetwork.getBuses().stream().filter(bus -> !bus.isDisabled()).collect(Collectors.toSet()));
    }

    @Override
    protected PostContingencyComputationStatus postContingencyStatusFromLoadFlowResult(DcLoadFlowResult result) {
        return result.isSucceeded() ? PostContingencyComputationStatus.CONVERGED : PostContingencyComputationStatus.FAILED;
    }
}
