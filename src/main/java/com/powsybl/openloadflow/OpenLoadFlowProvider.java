/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.loadflow.resultscompletion.z0flows.Z0FlowsCompletion;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.Markers;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.PowsyblOpenLoadFlowVersion;
import com.powsybl.tools.PowsyblCoreVersion;
import net.jafama.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.network.LfNetwork.LOW_IMPEDANCE_THRESHOLD;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
@AutoService(LoadFlowProvider.class)
public class OpenLoadFlowProvider implements LoadFlowProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenLoadFlowProvider.class);

    private static final String NAME = "OpenLoadFlow";

    private final MatrixFactory matrixFactory;

    private final GraphDecrementalConnectivityFactory<LfBus> connectivityFactory;

    private boolean forcePhaseControlOffAndAddAngle1Var = false; // just for unit testing

    public OpenLoadFlowProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenLoadFlowProvider(MatrixFactory matrixFactory) {
        this(matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>());
    }

    public OpenLoadFlowProvider(MatrixFactory matrixFactory, GraphDecrementalConnectivityFactory<LfBus> connectivityFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
    }

    public void setForcePhaseControlOffAndAddAngle1Var(boolean forcePhaseControlOffAndAddAngle1Var) {
        this.forcePhaseControlOffAndAddAngle1Var = forcePhaseControlOffAndAddAngle1Var;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblCoreVersion().getMavenProjectVersion();
    }

    private LoadFlowResult runAc(Network network, LoadFlowParameters parameters, Reporter reporter) {
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
        OpenLoadFlowParameters.logAc(parameters, parametersExt);

        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory, reporter);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Outer loops: {}", acParameters.getOuterLoops().stream().map(OuterLoop::getType).collect(Collectors.toList()));
        }

        List<AcLoadFlowResult> results = AcloadFlowEngine.run(network, new LfNetworkLoaderImpl(), acParameters, reporter);

        Networks.resetState(network);

        boolean ok = results.stream().anyMatch(result -> result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED);
        // reset slack buses if at least one component has converged
        if (ok && parameters.isWriteSlackBus()) {
            SlackTerminal.reset(network);
        }

        List<LoadFlowResult.ComponentResult> componentResults = new ArrayList<>(results.size());
        for (AcLoadFlowResult result : results) {
            // update network state
            if (result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED) {
                result.getNetwork().updateState(!parameters.isNoGeneratorReactiveLimits(),
                                                parameters.isWriteSlackBus(),
                                                parameters.isPhaseShifterRegulationOn(),
                                                parameters.isTransformerVoltageControlOn(),
                                                parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                                                parameters.isDistributedSlack() && (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD ||
                                                    parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) && parametersExt.isLoadPowerFactorConstant());
            }

            LoadFlowResult.ComponentResult.Status status;
            switch (result.getNewtonRaphsonStatus()) {
                case CONVERGED:
                    status = LoadFlowResult.ComponentResult.Status.CONVERGED;
                    break;
                case MAX_ITERATION_REACHED:
                    status = LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
                    break;
                case SOLVER_FAILED:
                    status = LoadFlowResult.ComponentResult.Status.SOLVER_FAILED;
                    break;
                default:
                    status = LoadFlowResult.ComponentResult.Status.FAILED;
                    break;
            }
            componentResults.add(new LoadFlowResultImpl.ComponentResultImpl(result.getNetwork().getNumCC(),
                                                                            result.getNetwork().getNumSC(),
                                                                            status,
                                                                            result.getNewtonRaphsonIterations(),
                                                                            result.getNetwork().getSlackBus().getId(),
                                                                            result.getSlackBusActivePowerMismatch() * PerUnit.SB));
        }

        // zero or low impedance branch flows computation
        if (ok) {
            new Z0FlowsCompletion(network, line -> {
                // to be consistent with low impedance criteria used in DcEquationSystem and AcEquationSystem
                double nominalV = line.getTerminal1().getVoltageLevel().getNominalV();
                double zb = nominalV * nominalV / PerUnit.SB;
                double z = FastMath.hypot(line.getR(), line.getX());
                return z / zb <= LOW_IMPEDANCE_THRESHOLD;
            }).complete();
        }

        return new LoadFlowResultImpl(ok, Collections.emptyMap(), null, componentResults);
    }

    private LoadFlowResult runDc(Network network, LoadFlowParameters parameters, Reporter reporter) {
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
        OpenLoadFlowParameters.logDc(parameters, parametersExt);

        var dcParameters = OpenLoadFlowParameters.createDcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory, forcePhaseControlOffAndAddAngle1Var);

        List<DcLoadFlowResult> results = new DcLoadFlowEngine(network, new LfNetworkLoaderImpl(), dcParameters, reporter)
                .run(reporter);

        Networks.resetState(network);

        List<LoadFlowResult.ComponentResult> componentsResult = results.stream().map(r -> processResult(network, r, parameters)).collect(Collectors.toList());
        boolean ok = results.stream().anyMatch(r -> r.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED);
        return new LoadFlowResultImpl(ok, Collections.emptyMap(), null, componentsResult);
    }

    private LoadFlowResult.ComponentResult processResult(Network network, DcLoadFlowResult pResult, LoadFlowParameters parameters) {
        if (pResult.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED && parameters.isWriteSlackBus()) {
            SlackTerminal.reset(network);
        }

        if (pResult.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED) {
            pResult.getNetwork().updateState(false,
                    parameters.isWriteSlackBus(),
                    parameters.isPhaseShifterRegulationOn(),
                    parameters.isTransformerVoltageControlOn(),
                    parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                    false);
        }

        return new LoadFlowResultImpl.ComponentResultImpl(
                pResult.getNetwork().getNumCC(),
                pResult.getNetwork().getNumSC(),
                pResult.getStatus(),
                0,
                pResult.getNetwork().getSlackBus().getId(),
                pResult.getSlackBusActivePowerMismatch() * PerUnit.SB);
    }

    @Override
    public CompletableFuture<LoadFlowResult> run(Network network, ComputationManager computationManager, String workingVariantId, LoadFlowParameters parameters) {
        return run(network, computationManager, workingVariantId, parameters, Reporter.NO_OP);
    }

    @Override
    public CompletableFuture<LoadFlowResult> run(Network network, ComputationManager computationManager, String workingVariantId, LoadFlowParameters parameters, Reporter reporter) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(parameters);

        LOGGER.info("Version: {}", new PowsyblOpenLoadFlowVersion());

        Reporter lfReporter = reporter.createSubReporter("loadFlow", "Load flow on network ${networkId}",
            "networkId", network.getId());

        return CompletableFuture.supplyAsync(() -> {

            network.getVariantManager().setWorkingVariant(workingVariantId);

            Stopwatch stopwatch = Stopwatch.createStarted();

            LoadFlowResult result = parameters.isDc() ? runDc(network, parameters, lfReporter)
                                                      : runAc(network, parameters, lfReporter);

            stopwatch.stop();
            LOGGER.info(Markers.PERFORMANCE_MARKER, "Load flow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return result;
        });
    }
}
