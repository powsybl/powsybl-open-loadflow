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
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.loadflow.resultscompletion.z0flows.Z0FlowsCompletion;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.*;
import com.powsybl.openloadflow.ac.nr.*;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.NetworkSlackBusSelector;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.SlackBusSelector;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PowsyblOpenLoadFlowVersion;
import com.powsybl.tools.PowsyblCoreVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    private static AcLoadFlowObserver getObserver(OpenLoadFlowParameters parametersExt) {
        List<AcLoadFlowObserver> observers = new ArrayList<>(parametersExt.getAdditionalObservers().size() + 2);
        observers.add(new AcLoadFlowLogger());
        observers.add(new AcLoadFlowProfiler());
        observers.addAll(parametersExt.getAdditionalObservers());
        return AcLoadFlowObserver.of(observers);
    }

    private static ImmutableMap<String, String> createMetrics(AcLoadFlowResult result) {
        String prefix = "network_" + result.getNetwork().getNum() + "_";
        return ImmutableMap.of(prefix + "iterations", Integer.toString(result.getNewtonRaphsonIterations()),
                               prefix + "status", result.getNewtonRaphsonStatus().name());
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

    static OpenLoadFlowParameters getParametersExt(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        if (parametersExt == null) {
            parametersExt = new OpenLoadFlowParameters();
        }
        return parametersExt;
    }

    private static SlackBusSelector getSlackBusSelector(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return parameters.isReadSlackBus() ? new NetworkSlackBusSelector(network, parametersExt.getSlackBusSelector())
                                           : parametersExt.getSlackBusSelector();
    }

    static AcLoadFlowParameters createAcParameters(Network network, MatrixFactory matrixFactory, LoadFlowParameters parameters,
                                                   OpenLoadFlowParameters parametersExt) {

        SlackBusSelector slackBusSelector = getSlackBusSelector(network, parameters, parametersExt);

        VoltageInitializer voltageInitializer = getVoltageInitializer(parameters);

        NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

        LOGGER.info("Slack bus selector: {}", slackBusSelector.getClass().getSimpleName());
        LOGGER.info("Voltage level initializer: {}", voltageInitializer.getClass().getSimpleName());
        LOGGER.info("Distributed slack: {}", parametersExt.isDistributedSlack());
        LOGGER.info("Balance type: {}", parametersExt.getBalanceType());
        LOGGER.info("Reactive limits: {}", !parameters.isNoGeneratorReactiveLimits());
        LOGGER.info("Voltage remote control: {}", parametersExt.hasVoltageRemoteControl());
        LOGGER.info("Phase control: {}", parameters.isPhaseShifterRegulationOn());
        LOGGER.info("Split shunt admittance: {}", parameters.isTwtSplitShuntAdmittance());

        List<OuterLoop> outerLoops = new ArrayList<>();
        if (parametersExt.isDistributedSlack()) {
            switch (parametersExt.getBalanceType()) {
                case PROPORTIONAL_TO_GENERATION_P_MAX:
                    outerLoops.add(new DistributedSlackOnGenerationOuterLoop(parametersExt.isThrowsExceptionInCaseOfSlackDistributionFailure()));
                    break;
                case PROPORTIONAL_TO_LOAD:
                    outerLoops.add(new DistributedSlackOnLoadOuterLoop(parametersExt.isThrowsExceptionInCaseOfSlackDistributionFailure(), false));
                    break;
                case PROPORTIONAL_TO_CONFORM_LOAD:
                    outerLoops.add(new DistributedSlackOnLoadOuterLoop(parametersExt.isThrowsExceptionInCaseOfSlackDistributionFailure(), true));
                    break;
                case PROPORTIONAL_TO_GENERATION_P: // to be implemented.
                    throw new UnsupportedOperationException("Unsupported balance type mode: " + parametersExt.getBalanceType());
                default:
                    throw new UnsupportedOperationException("Unknown balance type mode: " + parametersExt.getBalanceType());
            }
        }
        if (parameters.isPhaseShifterRegulationOn()) {
            outerLoops.add(new PhaseControlOuterLoop());
        }
        if (!parameters.isNoGeneratorReactiveLimits()) {
            outerLoops.add(new ReactiveLimitsOuterLoop());
        }
        if (parameters.isTransformerVoltageControlOn()) {
            outerLoops.add(new TransformerVoltageControlOuterLoop());
        }

        return new AcLoadFlowParameters(slackBusSelector, voltageInitializer, stoppingCriteria,
                outerLoops, matrixFactory, getObserver(parametersExt),
                parametersExt.hasVoltageRemoteControl(),
                parameters.isPhaseShifterRegulationOn(),
                parameters.isTransformerVoltageControlOn(),
                parametersExt.getLowImpedanceBranchMode() == OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE,
                parameters.isTwtSplitShuntAdmittance());
    }

    private CompletableFuture<LoadFlowResult> runAc(Network network, String workingStateId, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            AcLoadFlowParameters acParameters = createAcParameters(network, matrixFactory, parameters, parametersExt);
            List<AcLoadFlowResult> results = AcloadFlowEngine.run(network, acParameters);

            Networks.resetState(network);

            boolean ok = results.stream().anyMatch(result -> result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED);
            // reset slack buses if at least one component has converged
            if (ok && parameters.isWriteSlackBus()) {
                SlackTerminal.reset(network);
            }

            Map<String, String> metrics = new HashMap<>();
            for (AcLoadFlowResult result : results) {
                // update network state
                result.getNetwork().updateState(!parameters.isNoGeneratorReactiveLimits(), parameters.isWriteSlackBus());

                metrics.putAll(createMetrics(result));
            }

            // zero or low impedance branch flows computation
            if (ok) {
                new Z0FlowsCompletion(network, line -> {
                    // to be consistent with low impedance criteria used in DcEquationSystem and AcEquationSystem
                    double nominalV = line.getTerminal1().getVoltageLevel().getNominalV();
                    double zb = nominalV * nominalV / PerUnit.SB;
                    double z = Math.hypot(line.getR(), line.getX());
                    return z / zb <= DcEquationSystem.LOW_IMPEDANCE_THRESHOLD;
                }).complete();
            }

            return new LoadFlowResultImpl(ok, metrics, null);
        });
    }

    private CompletableFuture<LoadFlowResult> runDc(Network network, String workingStateId, LoadFlowParameters parameters,
                                                    OpenLoadFlowParameters parametersExt) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            SlackBusSelector slackBusSelector = getSlackBusSelector(network, parameters, parametersExt);
            DcLoadFlowParameters dcParameters = new DcLoadFlowParameters(slackBusSelector, matrixFactory, true, parameters.isTwtSplitShuntAdmittance());

            DcLoadFlowResult result = new DcLoadFlowEngine(network, dcParameters)
                    .run();

            Networks.resetState(network);

            if (result.isOk() && parameters.isWriteSlackBus()) {
                SlackTerminal.reset(network);
            }

            result.getNetwork().updateState(false, parameters.isWriteSlackBus());

            return new LoadFlowResultImpl(result.isOk(), Collections.emptyMap(), null);
        });
    }

    @Override
    public CompletableFuture<LoadFlowResult> run(Network network, ComputationManager computationManager, String workingVariantId, LoadFlowParameters parameters) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(parameters);

        LOGGER.info("Version: {}", new PowsyblOpenLoadFlowVersion());

        OpenLoadFlowParameters parametersExt = getParametersExt(parameters);

        return parametersExt.isDc() ? runDc(network, workingVariantId, parameters, parametersExt)
                                    : runAc(network, workingVariantId, parameters, parametersExt);
    }
}
