/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowLogger;
import com.powsybl.openloadflow.ac.AcLoadFlowProfiler;
import com.powsybl.openloadflow.ac.DistributedSlackOuterLoop;
import com.powsybl.openloadflow.ac.ReactiveLimitsOuterLoop;
import com.powsybl.openloadflow.ac.nr.*;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.equations.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.SlackBusSelector;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.tools.PowsyblCoreVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
@AutoService(LoadFlowProvider.class)
public class OpenLoadFlowProvider implements LoadFlowProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenLoadFlowProvider.class);

    private static final String NAME = "OpenLoadFlow";

    private final MatrixFactory matrixFactory;

    public OpenLoadFlowProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenLoadFlowProvider(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblCoreVersion().getMavenProjectVersion();
    }

    private AcLoadFlowObserver getObserver(OpenLoadFlowParameters parametersExt) {
        List<AcLoadFlowObserver> observers = new ArrayList<>(parametersExt.getAdditionalObservers().size() + 2);
        observers.add(new AcLoadFlowLogger());
        observers.add(new AcLoadFlowProfiler());
        observers.addAll(parametersExt.getAdditionalObservers());
        return AcLoadFlowObserver.of(observers);
    }

    private static ImmutableMap<String, String> createMetrics(AcLoadFlowResult result) {
        return ImmutableMap.of("iterations", Integer.toString(result.getNewtonRaphsonIterations()),
                "status", result.getNewtonRaphsonStatus().name());
    }

    private static VoltageInitializer getVoltageInitializer(LoadFlowParameters parameters) {
        switch (parameters.getVoltageInitMode()) {
            case UNIFORM_VALUES:
                return new UniformValueVoltageInitializer();
            case PREVIOUS_VALUES:
                return new PreviousValueVoltageInitializer();
            case DC_VALUES:
                return new DcValueVoltageInitializer();
            default:
                throw new UnsupportedOperationException("Unsupported voltage init mode: " + parameters.getVoltageInitMode());
        }
    }

    private OpenLoadFlowParameters getParametersExt(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        if (parametersExt == null) {
            parametersExt = new OpenLoadFlowParameters();
        }
        return parametersExt;
    }

    private CompletableFuture<LoadFlowResult> runAc(Network network, String workingStateId, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            SlackBusSelector slackBusSelector = parametersExt.getSlackBusSelector();

            VoltageInitializer voltageInitializer = getVoltageInitializer(parameters);

            NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

            LOGGER.info("Slack bus selector: {}", slackBusSelector.getClass().getSimpleName());
            LOGGER.info("Voltage level initializer: {}", voltageInitializer.getClass().getSimpleName());
            LOGGER.info("Distributed slack: {}", parametersExt.isDistributedSlack());
            LOGGER.info("Reactive limits: {}", !parameters.isNoGeneratorReactiveLimits());

            List<OuterLoop> outerLoops = new ArrayList<>();
            if (parametersExt.isDistributedSlack()) {
                outerLoops.add(new DistributedSlackOuterLoop());
            }
            if (!parameters.isNoGeneratorReactiveLimits()) {
                outerLoops.add(new ReactiveLimitsOuterLoop());
            }

            AcLoadFlowResult result = new AcloadFlowEngine(network, slackBusSelector, voltageInitializer, stoppingCriteria,
                                                           outerLoops, matrixFactory, getObserver(parametersExt))
                    .run();

            // update network state
            Networks.resetState(network);
            result.getNetworks().get(0).updateState(!parameters.isNoGeneratorReactiveLimits());

            return new LoadFlowResultImpl(result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED, createMetrics(result), null);
        });
    }

    private CompletableFuture<LoadFlowResult> runDc(Network network, String workingStateId) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            DcLoadFlowResult result = new DcLoadFlowEngine(network, matrixFactory)
                    .run();

            Networks.resetState(network);
            result.getNetworks().get(0).updateState(false);

            return new LoadFlowResultImpl(result.isOk(), Collections.emptyMap(), null);
        });
    }

    @Override
    public CompletableFuture<LoadFlowResult> run(Network network, ComputationManager computationManager, String workingVariantId, LoadFlowParameters parameters) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(parameters);

        OpenLoadFlowParameters parametersExt = getParametersExt(parameters);

        return parametersExt.isDc() ? runDc(network, workingVariantId)
                : runAc(network, workingVariantId, parameters, parametersExt);
    }
}
