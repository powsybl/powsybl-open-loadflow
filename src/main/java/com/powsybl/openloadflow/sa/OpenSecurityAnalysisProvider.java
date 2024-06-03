/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.google.auto.service.AutoService;
import com.powsybl.action.Action;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionJsonSerializer;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PowsyblOpenLoadFlowVersion;
import com.powsybl.openloadflow.util.ProviderConstants;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;
import com.powsybl.security.limitreduction.LimitReduction;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.strategy.OperatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
@AutoService(SecurityAnalysisProvider.class)
public class OpenSecurityAnalysisProvider implements SecurityAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSecurityAnalysisProvider.class);

    private final MatrixFactory matrixFactory;

    private final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    public OpenSecurityAnalysisProvider(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this.matrixFactory = matrixFactory;
        this.connectivityFactory = connectivityFactory;
    }

    public OpenSecurityAnalysisProvider() {
        this(new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());
    }

    @Override
    public CompletableFuture<SecurityAnalysisReport> run(Network network, String workingVariantId, LimitViolationDetector limitViolationDetector,
                                                         LimitViolationFilter limitViolationFilter, ComputationManager computationManager,
                                                         SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                                         List<SecurityAnalysisInterceptor> interceptors, List<OperatorStrategy> operatorStrategies, List<Action> actions,
                                                         List<StateMonitor> stateMonitors, List<LimitReduction> limitReductions, ReportNode reportNode) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(computationManager);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        Objects.requireNonNull(interceptors);
        Objects.requireNonNull(operatorStrategies);
        Objects.requireNonNull(actions);
        Objects.requireNonNull(stateMonitors);
        Objects.requireNonNull(reportNode);

        LoadFlowParameters loadFlowParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters loadFlowParametersExt = OpenLoadFlowParameters.get(loadFlowParameters);

        // FIXME implement a fast incremental connectivity algorithm
        GraphConnectivityFactory<LfBus, LfBranch> selectedConnectivityFactory;
        if (operatorStrategies.isEmpty() && !loadFlowParametersExt.isSimulateAutomationSystems()) {
            selectedConnectivityFactory = connectivityFactory;
        } else {
            LOGGER.warn("Naive (and slow!!!) connectivity algorithm has been selected because at least one operator strategy is configured");
            selectedConnectivityFactory = new NaiveGraphConnectivityFactory<>(LfBus::getNum);
        }

        AbstractSecurityAnalysis<?, ?, ?, ?, ?> securityAnalysis;
        if (loadFlowParameters.isDc()) {
            securityAnalysis = new DcSecurityAnalysis(network, matrixFactory, selectedConnectivityFactory, stateMonitors, reportNode);
        } else {
            securityAnalysis = new AcSecurityAnalysis(network, matrixFactory, selectedConnectivityFactory, stateMonitors, reportNode);
        }

        return securityAnalysis.run(workingVariantId, securityAnalysisParameters, contingenciesProvider, computationManager, operatorStrategies, actions, limitReductions);
    }

    @Override
    public String getName() {
        return ProviderConstants.NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblOpenLoadFlowVersion().toString();
    }

    @Override
    public Optional<String> getLoadFlowProviderName() {
        return Optional.of(ProviderConstants.NAME);
    }

    @Override
    public Optional<ExtensionJsonSerializer> getSpecificParametersSerializer() {
        return Optional.of(new OpenSecurityAnalysisParameterJsonSerializer());
    }

    @Override
    public Optional<Extension<SecurityAnalysisParameters>> loadSpecificParameters(PlatformConfig platformConfig) {
        return Optional.of(OpenSecurityAnalysisParameters.load(platformConfig));
    }

    @Override
    public Optional<Extension<SecurityAnalysisParameters>> loadSpecificParameters(Map<String, String> properties) {
        return Optional.of(OpenSecurityAnalysisParameters.load(properties));
    }

    @Override
    public List<String> getSpecificParametersNames() {
        return OpenSecurityAnalysisParameters.SPECIFIC_PARAMETERS_NAMES;
    }

    @Override
    public void updateSpecificParameters(Extension<SecurityAnalysisParameters> extension, Map<String, String> properties) {
        ((OpenSecurityAnalysisParameters) extension).update(properties);
    }
}
