/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.auto.service.AutoService;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PowsyblOpenLoadFlowVersion;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;
import com.powsybl.security.monitor.StateMonitor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
@AutoService(SecurityAnalysisProvider.class)
public class OpenSecurityAnalysisProvider implements SecurityAnalysisProvider {

    private final MatrixFactory matrixFactory;

    private final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider;

    public OpenSecurityAnalysisProvider(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        this.matrixFactory = matrixFactory;
        this.connectivityProvider = connectivityProvider;
    }

    public OpenSecurityAnalysisProvider() {
        this(new SparseMatrixFactory(), EvenShiloachGraphDecrementalConnectivity::new);
    }

    @Override
    public CompletableFuture<SecurityAnalysisReport> run(Network network, String workingVariantId, LimitViolationDetector limitViolationDetector,
                                                         LimitViolationFilter limitViolationFilter, ComputationManager computationManager,
                                                         SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                                         List<SecurityAnalysisInterceptor> interceptors, List<StateMonitor> stateMonitors) {
        AbstractSecurityAnalysis securityAnalysis;
        if (securityAnalysisParameters.getLoadFlowParameters().isDc()) {
            securityAnalysis = new DcSecurityAnalysis(network, limitViolationDetector, limitViolationFilter, matrixFactory, connectivityProvider, stateMonitors);
        } else {
            securityAnalysis = new AcSecurityAnalysis(network, limitViolationDetector, limitViolationFilter, matrixFactory, connectivityProvider, stateMonitors);
        }
        interceptors.forEach(securityAnalysis::addInterceptor);
        return securityAnalysis.run(workingVariantId, securityAnalysisParameters, contingenciesProvider);
    }

    @Override
    public String getName() {
        return "OpenSecurityAnalysis";
    }

    @Override
    public String getVersion() {
        return new PowsyblOpenLoadFlowVersion().toString();
    }
}
