/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.loadflow.open.ac.AcLoadFlowLogger;
import com.powsybl.loadflow.open.ac.AcLoadFlowProfiler;
import com.powsybl.loadflow.open.ac.DistributedSlackOuterLoop;
import com.powsybl.loadflow.open.ac.ReactiveLimitsOuterLoop;
import com.powsybl.loadflow.open.ac.nr.*;
import com.powsybl.loadflow.open.ac.outerloop.AcLoadFlowResult;
import com.powsybl.loadflow.open.ac.outerloop.AcloadFlowEngine;
import com.powsybl.loadflow.open.ac.outerloop.OuterLoop;
import com.powsybl.loadflow.open.dc.DcLoadFlowEngine;
import com.powsybl.loadflow.open.equations.PreviousValueVoltageInitializer;
import com.powsybl.loadflow.open.equations.UniformValueVoltageInitializer;
import com.powsybl.loadflow.open.equations.VoltageInitializer;
import com.powsybl.loadflow.open.network.FirstSlackBusSelector;
import com.powsybl.loadflow.open.network.LfBus;
import com.powsybl.loadflow.open.network.LfNetwork;
import com.powsybl.loadflow.open.network.SlackBusSelector;
import com.powsybl.loadflow.open.network.impl.LfNetworks;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
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

    private static final String NAME = "OpenLoadflow";

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

            List<OuterLoop> outerLoops = new ArrayList<>();
            if (parametersExt.isDistributedSlack()) {
                outerLoops.add(new DistributedSlackOuterLoop());
            }
            if (parametersExt.hasReactiveLimits()) {
                outerLoops.add(new ReactiveLimitsOuterLoop());
            }

            List<LfNetwork> lfNetworks = LfNetworks.create(network, slackBusSelector);

            // only process main (largest) connected component
            LfNetwork lfNetwork = lfNetworks.get(0);

            AcLoadFlowResult result = new AcloadFlowEngine(lfNetwork, voltageInitializer, stoppingCriteria, outerLoops,
                    matrixFactory, getObserver(parametersExt))
                    .run();

            // update network state
            LfNetworks.resetState(network);
            lfNetwork.updateState(parametersExt.hasReactiveLimits());

            return new LoadFlowResultImpl(result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED, createMetrics(result), null);
        });
    }

    private static void balance(LfNetwork network) {
        double activeGeneration = 0;
        double activeLoad = 0;
        for (LfBus b : network.getBuses()) {
            activeGeneration += b.getGenerationTargetP();
            activeLoad += b.getLoadTargetP();
        }

        LOGGER.info("Active generation={} Mw, active load={} Mw", Math.round(activeGeneration), Math.round(activeLoad));
    }

    private CompletableFuture<LoadFlowResult> runDc(Network network, String workingStateId) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            LfNetwork lfNetwork = LfNetworks.create(network, new FirstSlackBusSelector()).get(0);

            balance(lfNetwork);

            boolean status = new DcLoadFlowEngine(lfNetwork, matrixFactory)
                    .run();

            LfNetworks.resetState(network);
            lfNetwork.updateState(false);

            return new LoadFlowResultImpl(status, Collections.emptyMap(), null);
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
