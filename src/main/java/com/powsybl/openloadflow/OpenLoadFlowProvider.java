/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionJsonSerializer;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.*;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlows;
import com.powsybl.openloadflow.util.*;
import com.powsybl.tools.PowsyblCoreVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
@AutoService(LoadFlowProvider.class)
public class OpenLoadFlowProvider implements LoadFlowProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenLoadFlowProvider.class);

    private final MatrixFactory matrixFactory;

    private final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    private boolean forcePhaseControlOffAndAddAngle1Var = false; // just for unit testing

    public OpenLoadFlowProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenLoadFlowProvider(MatrixFactory matrixFactory) {
        this(matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>());
    }

    public OpenLoadFlowProvider(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
    }

    public void setForcePhaseControlOffAndAddAngle1Var(boolean forcePhaseControlOffAndAddAngle1Var) {
        this.forcePhaseControlOffAndAddAngle1Var = forcePhaseControlOffAndAddAngle1Var;
    }

    @Override
    public String getName() {
        return ProviderConstants.NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblCoreVersion().getMavenProjectVersion();
    }

    private static LoadFlowResult.ComponentResult.Status convertStatus(AcLoadFlowResult result) {
        if (result.getOuterLoopStatus() == OuterLoopStatus.UNSTABLE) {
            return LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
        } else {
            switch (result.getNewtonRaphsonStatus()) {
                case CONVERGED:
                    return LoadFlowResult.ComponentResult.Status.CONVERGED;
                case MAX_ITERATION_REACHED:
                    return LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
                case SOLVER_FAILED:
                    return LoadFlowResult.ComponentResult.Status.SOLVER_FAILED;
                default:
                    return LoadFlowResult.ComponentResult.Status.FAILED;
            }
        }
    }

    private GraphConnectivityFactory<LfBus, LfBranch> getConnectivityFactory(OpenLoadFlowParameters parametersExt) {
        return parametersExt.isNetworkCacheEnabled() && !parametersExt.getActionableSwitchesIds().isEmpty()
                ? new NaiveGraphConnectivityFactory<>(LfBus::getNum)
                : connectivityFactory;
    }

    private LoadFlowResult runAc(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Reporter reporter) {
        GraphConnectivityFactory<LfBus, LfBranch> selectedConnectivityFactory = getConnectivityFactory(parametersExt);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, selectedConnectivityFactory);
        acParameters.getNewtonRaphsonParameters()
                .setDetailedReport(parametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Outer loops: {}", acParameters.getOuterLoops().stream().map(OuterLoop::getName).collect(Collectors.toList()));
        }

        List<AcLoadFlowResult> results;
        if (parametersExt.isNetworkCacheEnabled()) {
            results = new AcLoadFlowFromCache(network, parameters, parametersExt, acParameters, reporter)
                    .run();
        } else {
            results = AcloadFlowEngine.run(network, new LfNetworkLoaderImpl(), acParameters, reporter);
        }

        Networks.resetState(network);

        boolean ok = results.stream().anyMatch(result -> result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED);
        // reset slack buses if at least one component has converged
        if (ok && parameters.isWriteSlackBus()) {
            SlackTerminal.reset(network);
        }

        List<LoadFlowResult.ComponentResult> componentResults = new ArrayList<>(results.size());
        for (AcLoadFlowResult result : results) {
            // update network state
            if (result.isOk() || parametersExt.isAlwaysUpdateNetwork()) {
                var updateParameters = new LfNetworkStateUpdateParameters(parameters.isUseReactiveLimits(),
                                                                          parameters.isWriteSlackBus(),
                                                                          parameters.isPhaseShifterRegulationOn(),
                                                                          parameters.isTransformerVoltageControlOn(),
                                                                          parameters.isDistributedSlack() && (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD || parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) && parametersExt.isLoadPowerFactorConstant(),
                                                                          parameters.isDc(),
                                                                          acParameters.getNetworkParameters().isBreakers());
                result.getNetwork().updateState(updateParameters);

                // zero or low impedance branch flows computation
                computeZeroImpedanceFlows(result.getNetwork(), LoadFlowModel.AC);
            }

            LoadFlowResult.ComponentResult.Status status = convertStatus(result);
            // FIXME a null slack bus ID should be allowed
            String slackBusId = result.getNetwork().isValid() ? result.getNetwork().getSlackBus().getId() : "";
            componentResults.add(new LoadFlowResultImpl.ComponentResultImpl(result.getNetwork().getNumCC(),
                                                                            result.getNetwork().getNumSC(),
                                                                            status,
                                                                            result.getNewtonRaphsonIterations(),
                                                                            slackBusId, // FIXME manage multiple slack buses
                                                                            result.getSlackBusActivePowerMismatch() * PerUnit.SB,
                                                                            result.getDistributedActivePower() * PerUnit.SB));
        }

        return new LoadFlowResultImpl(ok, Collections.emptyMap(), null, componentResults);
    }

    private void computeZeroImpedanceFlows(LfNetwork network, LoadFlowModel loadFlowModel) {
        for (LfZeroImpedanceNetwork zeroImpedanceNetwork : network.getZeroImpedanceNetworks(loadFlowModel)) {
            new ZeroImpedanceFlows(zeroImpedanceNetwork.getGraph(), zeroImpedanceNetwork.getSpanningTree(), loadFlowModel)
                    .compute();
        }
    }

    private LoadFlowResult runDc(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Reporter reporter) {

        var dcParameters = OpenLoadFlowParameters.createDcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory, forcePhaseControlOffAndAddAngle1Var);
        dcParameters.getNetworkParameters()
                .setCacheEnabled(false); // force not caching as not supported in DC LF

        List<DcLoadFlowResult> results = DcLoadFlowEngine.run(network, new LfNetworkLoaderImpl(), dcParameters, reporter);

        Networks.resetState(network);

        List<LoadFlowResult.ComponentResult> componentsResult = results.stream().map(r -> processResult(network, r, parameters, dcParameters.getNetworkParameters().isBreakers())).collect(Collectors.toList());
        boolean ok = results.stream().anyMatch(DcLoadFlowResult::isSucceeded);
        return new LoadFlowResultImpl(ok, Collections.emptyMap(), null, componentsResult);
    }

    private LoadFlowResult.ComponentResult processResult(Network network, DcLoadFlowResult result, LoadFlowParameters parameters, boolean breakers) {
        if (result.isSucceeded() && parameters.isWriteSlackBus()) {
            SlackTerminal.reset(network);
        }

        if (result.isSucceeded()) {
            var updateParameters = new LfNetworkStateUpdateParameters(false,
                                                                      parameters.isWriteSlackBus(),
                                                                      parameters.isPhaseShifterRegulationOn(),
                                                                      parameters.isTransformerVoltageControlOn(),
                                                                      false,
                                                                      true,
                                                                      breakers);
            result.getNetwork().updateState(updateParameters);

            // zero or low impedance branch flows computation
            computeZeroImpedanceFlows(result.getNetwork(), LoadFlowModel.DC);
        }

        return new LoadFlowResultImpl.ComponentResultImpl(
                result.getNetwork().getNumCC(),
                result.getNetwork().getNumSC(),
                result.isSucceeded() ? LoadFlowResult.ComponentResult.Status.CONVERGED : LoadFlowResult.ComponentResult.Status.FAILED,
                0,
                result.getNetwork().getSlackBus().getId(), // FIXME manage multiple slack buses
                result.getSlackBusActivePowerMismatch() * PerUnit.SB,
                Double.NaN);
    }

    @Override
    public CompletableFuture<LoadFlowResult> run(Network network, ComputationManager computationManager, String workingVariantId, LoadFlowParameters parameters) {
        return run(network, computationManager, workingVariantId, parameters, Reporter.NO_OP);
    }

    @Override
    public CompletableFuture<LoadFlowResult> run(Network network, ComputationManager computationManager, String workingVariantId, LoadFlowParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(computationManager);
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(reporter);

        LOGGER.info("Version: {}", new PowsyblOpenLoadFlowVersion());

        Reporter lfReporter = Reports.createLoadFlowReporter(reporter, network.getId());

        return CompletableFuture.supplyAsync(() -> {

            network.getVariantManager().setWorkingVariant(workingVariantId);

            Stopwatch stopwatch = Stopwatch.createStarted();

            OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
            OpenLoadFlowParameters.log(parameters, parametersExt);

            LoadFlowResult result = parameters.isDc() ? runDc(network, parameters, parametersExt, lfReporter)
                                                      : runAc(network, parameters, parametersExt, lfReporter);

            stopwatch.stop();
            LOGGER.info(Markers.PERFORMANCE_MARKER, "Load flow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return result;
        }, computationManager.getExecutor());
    }

    @Override
    public Optional<ExtensionJsonSerializer> getSpecificParametersSerializer() {
        return Optional.of(new OpenLoadFlowParameterJsonSerializer());
    }

    @Override
    public Optional<Extension<LoadFlowParameters>> loadSpecificParameters(PlatformConfig platformConfig) {
        return Optional.of(OpenLoadFlowParameters.load(platformConfig));
    }

    @Override
    public Optional<Extension<LoadFlowParameters>> loadSpecificParameters(Map<String, String> properties) {
        return Optional.of(OpenLoadFlowParameters.load(properties));
    }

    @Override
    public List<Parameter> getSpecificParameters() {
        return OpenLoadFlowParameters.SPECIFIC_PARAMETERS;
    }

    @Override
    public void updateSpecificParameters(Extension<LoadFlowParameters> extension, Map<String, String> properties) {
        ((OpenLoadFlowParameters) extension).update(properties);
    }
}
