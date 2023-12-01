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
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.lf.AbstractLoadFlowResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
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
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
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

    private GraphConnectivityFactory<LfBus, LfBranch> getConnectivityFactory(OpenLoadFlowParameters parametersExt) {
        return parametersExt.isNetworkCacheEnabled() && !parametersExt.getActionableSwitchesIds().isEmpty()
                || parametersExt.isSimulateAutomationSystems()
                ? new NaiveGraphConnectivityFactory<>(LfBus::getNum)
                : connectivityFactory;
    }

    private void updateAcState(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               AcLoadFlowResult result, AcLoadFlowParameters acParameters, boolean atLeastOneComponentHasToBeUpdated) {
        if (parametersExt.isNetworkCacheEnabled()) {
            NetworkCache.INSTANCE.findEntry(network).orElseThrow().setPause(true);
        }
        try {
            // update network state
            if (atLeastOneComponentHasToBeUpdated || parametersExt.isAlwaysUpdateNetwork()) {
                var updateParameters = new LfNetworkStateUpdateParameters(parameters.isUseReactiveLimits(),
                                                                          parameters.isWriteSlackBus(),
                                                                          parameters.isPhaseShifterRegulationOn(),
                                                                          parameters.isTransformerVoltageControlOn(),
                                                                          parameters.isTransformerReactivePowerControlOn(),
                                                                          parameters.isDistributedSlack() && (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD || parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) && parametersExt.isLoadPowerFactorConstant(),
                                                                          parameters.isDc(),
                                                                          acParameters.getNetworkParameters().isBreakers(),
                                                                          parametersExt.getReactivePowerDispatchMode());
                result.getNetwork().updateState(updateParameters);

                // zero or low impedance branch flows computation
                computeZeroImpedanceFlows(result.getNetwork(), LoadFlowModel.AC);
            }
        } finally {
            if (parametersExt.isNetworkCacheEnabled()) {
                NetworkCache.INSTANCE.findEntry(network).orElseThrow().setPause(false);
            }
        }
    }

    private LoadFlowResult runAc(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Reporter reporter) {
        GraphConnectivityFactory<LfBus, LfBranch> selectedConnectivityFactory = getConnectivityFactory(parametersExt);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, selectedConnectivityFactory);
        acParameters.setDetailedReport(parametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Outer loops: {}", acParameters.getOuterLoops().stream().map(OuterLoop::getName).toList());
        }

        List<AcLoadFlowResult> results;
        if (parametersExt.isNetworkCacheEnabled()) {
            results = new AcLoadFlowFromCache(network, parameters, parametersExt, acParameters, reporter)
                    .run();
        } else {
            try (LfNetworkList lfNetworkList = Networks.load(network, acParameters.getNetworkParameters(), new LfTopoConfig(), reporter)) {
                results = AcloadFlowEngine.run(lfNetworkList.getList(), acParameters);
            }
        }

        // we reset the state if at least one component needs a network update.
        boolean atLeastOneComponentHasToBeUpdated = results.stream().anyMatch(AcLoadFlowResult::isWithNetworkUpdate);
        if (atLeastOneComponentHasToBeUpdated || parametersExt.isAlwaysUpdateNetwork()) {
            Networks.resetState(network);

            // reset slack buses if at least one component has converged
            if (parameters.isWriteSlackBus()) {
                SlackTerminal.reset(network);
            }
        }

        List<LoadFlowResult.ComponentResult> componentResults = new ArrayList<>(results.size());
        for (AcLoadFlowResult result : results) {
            updateAcState(network, parameters, parametersExt, result, acParameters, atLeastOneComponentHasToBeUpdated);

            ReferenceBusAndSlackBusesResults referenceBusAndSlackBusesResults = buildReferenceBusAndSlackBusesResults(result);
            componentResults.add(new LoadFlowResultImpl.ComponentResultImpl(result.getNetwork().getNumCC(),
                    result.getNetwork().getNumSC(),
                    result.toComponentResultStatus(),
                    result.toComponentResultStatus().name(), // statusText: can do better later on
                    Collections.emptyMap(), // metrics: can do better later on
                    result.getSolverIterations(),
                    referenceBusAndSlackBusesResults.referenceBusId(),
                    referenceBusAndSlackBusesResults.slackBusResultList(),
                    result.getDistributedActivePower() * PerUnit.SB));
        }

        boolean ok = results.stream().anyMatch(AcLoadFlowResult::isOk);
        return new LoadFlowResultImpl(ok, Collections.emptyMap(), null, componentResults);
    }

    private static ReferenceBusAndSlackBusesResults buildReferenceBusAndSlackBusesResults(AbstractLoadFlowResult result) {
        String referenceBusId = null;
        List<LoadFlowResult.SlackBusResult> slackBusResultList = new ArrayList<>();
        double slackBusActivePowerMismatch = result.getSlackBusActivePowerMismatch() * PerUnit.SB;
        if (result.getNetwork().isValid()) {
            referenceBusId = result.getNetwork().getReferenceBus().getId();
            List<LfBus> slackBuses = result.getNetwork().getSlackBuses();
            slackBusResultList = slackBuses.stream().map(
                    b -> (LoadFlowResult.SlackBusResult) new LoadFlowResultImpl.SlackBusResultImpl(b.getId(),
                            slackBusActivePowerMismatch / slackBuses.size())).toList();
        }
        return new ReferenceBusAndSlackBusesResults(referenceBusId, slackBusResultList);
    }

    private record ReferenceBusAndSlackBusesResults(String referenceBusId, List<LoadFlowResult.SlackBusResult> slackBusResultList) {
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

        List<LoadFlowResult.ComponentResult> componentsResult = results.stream().map(r -> processResult(network, r, parameters, dcParameters.getNetworkParameters().isBreakers())).toList();
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
                                                                      parameters.isTransformerReactivePowerControlOn(),
                                                                      false,
                                                                      true,
                                                                      breakers,
                                                                      ReactivePowerDispatchMode.Q_EQUAL_PROPORTION);
            result.getNetwork().updateState(updateParameters);

            // zero or low impedance branch flows computation
            computeZeroImpedanceFlows(result.getNetwork(), LoadFlowModel.DC);
        }

        var referenceBusAndSlackBusesResults = buildReferenceBusAndSlackBusesResults(result);
        LoadFlowResult.ComponentResult.Status status = result.isSucceeded() ? LoadFlowResult.ComponentResult.Status.CONVERGED : LoadFlowResult.ComponentResult.Status.FAILED;
        return new LoadFlowResultImpl.ComponentResultImpl(
                result.getNetwork().getNumCC(),
                result.getNetwork().getNumSC(),
                status,
                status.name(), // statusText: can do better later on
                Collections.emptyMap(), // metrics: can do better later on
                0, // iterationCount
                referenceBusAndSlackBusesResults.referenceBusId(),
                referenceBusAndSlackBusesResults.slackBusResultList(),
                Double.NaN);
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

    // TODO : remove me ?
    @Override
    public Optional<Class<? extends Extension<LoadFlowParameters>>> getSpecificParametersClass() {
        return Optional.empty();
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

    // TODO : remove me ?
    @Override
    public Map<String, String> createMapFromSpecificParameters(Extension<LoadFlowParameters> extension) {
        return null;
    }

    @Override
    public List<Parameter> getSpecificParameters() {
        return OpenLoadFlowParameters.SPECIFIC_PARAMETERS;
    }

    @Override
    public void updateSpecificParameters(Extension<LoadFlowParameters> extension, Map<String, String> properties) {
        ((OpenLoadFlowParameters) extension).update(properties);
    }

    @Override
    public Optional<Class<? extends Extension<LoadFlowParameters>>> getSpecificParametersClass() {
        return Optional.of(OpenLoadFlowParameters.class);
    }

    @Override
    public Map<String, String> createMapFromSpecificParameters(Extension<LoadFlowParameters> extension) {
        return ((OpenLoadFlowParameters) extension).toMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Objects.toString(e.getValue(), "")));
    }
}
