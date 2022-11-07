/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisReport;
import com.powsybl.security.action.Action;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.strategy.OperatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSecurityAnalysis {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSecurityAnalysis.class);

    protected final Network network;

    protected final MatrixFactory matrixFactory;

    protected final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected final StateMonitorIndex monitorIndex;

    protected final Reporter reporter;

    protected AbstractSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                       List<StateMonitor> stateMonitors, Reporter reporter) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.monitorIndex = new StateMonitorIndex(stateMonitors);
        this.reporter = Objects.requireNonNull(reporter);
    }

    public CompletableFuture<SecurityAnalysisReport> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters,
                                                         ContingenciesProvider contingenciesProvider, ComputationManager computationManager,
                                                         List<OperatorStrategy> operatorStrategies, List<Action> actions) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFutureTask.runAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingVariantId);
            return runSync(workingVariantId, securityAnalysisParameters, contingenciesProvider, computationManager, operatorStrategies, actions);
        }, computationManager.getExecutor());
    }

    abstract SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                            ComputationManager computationManager, List<OperatorStrategy> operatorStrategies, List<Action> actions);

    public static PostContingencyComputationStatus postContingencyStatusFromNRStatus(NewtonRaphsonStatus status) {
        switch (status) {
            case CONVERGED:
                return PostContingencyComputationStatus.CONVERGED;
            case MAX_ITERATION_REACHED:
                return PostContingencyComputationStatus.MAX_ITERATION_REACHED;
            case SOLVER_FAILED:
                return PostContingencyComputationStatus.SOLVER_FAILED;
            case NO_CALCULATION:
                return PostContingencyComputationStatus.NO_IMPACT;
            case UNREALISTIC_STATE:
                return PostContingencyComputationStatus.FAILED;
            default:
                throw new PowsyblException("Unsupported Newton Raphson status : " + status);
        }
    }

    public static LoadFlowResult.ComponentResult.Status loadFlowResultStatusFromNRStatus(NewtonRaphsonStatus status) {
        switch (status) {
            case CONVERGED:
                return LoadFlowResult.ComponentResult.Status.CONVERGED;
            case MAX_ITERATION_REACHED:
                return LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
            case SOLVER_FAILED:
                return LoadFlowResult.ComponentResult.Status.SOLVER_FAILED;
            case NO_CALCULATION:
            case UNREALISTIC_STATE:
                return LoadFlowResult.ComponentResult.Status.FAILED;
            default:
                throw new PowsyblException("Unsupported Newton Raphson status : " + status);
        }
    }
}
