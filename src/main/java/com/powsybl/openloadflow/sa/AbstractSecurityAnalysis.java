/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisReport;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
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

    protected final GraphDecrementalConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected final StateMonitorIndex monitorIndex;

    protected AbstractSecurityAnalysis(Network network, MatrixFactory matrixFactory, GraphDecrementalConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                       List<StateMonitor> stateMonitors) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.monitorIndex = new StateMonitorIndex(stateMonitors);
    }

    public CompletableFuture<SecurityAnalysisReport> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters,
                                                         ContingenciesProvider contingenciesProvider, ComputationManager computationManager) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFutureTask.runAsync(() -> {
            String oldWorkingVariantId = network.getVariantManager().getWorkingVariantId();
            network.getVariantManager().setWorkingVariant(workingVariantId);
            try {
                return runSync(workingVariantId, securityAnalysisParameters, contingenciesProvider, computationManager);
            } finally {
                network.getVariantManager().setWorkingVariant(oldWorkingVariantId);
            }
        }, computationManager.getExecutor());
    }

    abstract SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                            ComputationManager computationManager);
}
