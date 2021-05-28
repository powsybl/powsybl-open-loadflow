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
import com.powsybl.openloadflow.ac.DistributedSlackOuterLoop;
import com.powsybl.openloadflow.ac.PhaseControlOuterLoop;
import com.powsybl.openloadflow.ac.ReactiveLimitsOuterLoop;
import com.powsybl.openloadflow.ac.TransformerVoltageControlOuterLoop;
import com.powsybl.openloadflow.ac.nr.DcValueVoltageInitializer;
import com.powsybl.openloadflow.ac.nr.DefaultNewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.equations.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.NetworkSlackBusSelector;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.SlackBusSelector;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.Markers;
import com.powsybl.openloadflow.util.PowsyblOpenLoadFlowVersion;
import com.powsybl.tools.PowsyblCoreVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    private boolean forcePhaseControlOffAndAddAngle1Var = false; // just for unit testing

    public OpenLoadFlowProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenLoadFlowProvider(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
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

    public static VoltageInitializer getVoltageInitializer(LoadFlowParameters parameters) {
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

    public static OpenLoadFlowParameters getParametersExt(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        if (parametersExt == null) {
            parametersExt = OpenLoadFlowParameters.load();
        }
        return parametersExt;
    }

    private static SlackBusSelector getSlackBusSelector(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(parametersExt.getSlackBusSelectionMode(), parametersExt.getSlackBusId());
        return parameters.isReadSlackBus() ? new NetworkSlackBusSelector(network, slackBusSelector)
                                           : slackBusSelector;
    }

    public static AcLoadFlowParameters createAcParameters(Network network, MatrixFactory matrixFactory, LoadFlowParameters parameters,
                                                          OpenLoadFlowParameters parametersExt, boolean breakers) {
        return createAcParameters(network, matrixFactory, parameters, parametersExt, breakers, false, null);
    }

    public static AcLoadFlowParameters createAcParameters(Network network, MatrixFactory matrixFactory, LoadFlowParameters parameters,
                                                          OpenLoadFlowParameters parametersExt, boolean breakers, boolean forceA1Var,
                                                          Set<String> branchesWithCurrent) {

        SlackBusSelector slackBusSelector = getSlackBusSelector(network, parameters, parametersExt);

        VoltageInitializer voltageInitializer = getVoltageInitializer(parameters);

        NewtonRaphsonStoppingCriteria stoppingCriteria = new DefaultNewtonRaphsonStoppingCriteria();

        LOGGER.info("Slack bus selector: {}", slackBusSelector.getClass().getSimpleName());
        LOGGER.info("Voltage level initializer: {}", voltageInitializer.getClass().getSimpleName());
        LOGGER.info("Distributed slack: {}", parameters.isDistributedSlack());
        LOGGER.info("Balance type: {}", parameters.getBalanceType());
        LOGGER.info("Reactive limits: {}", !parameters.isNoGeneratorReactiveLimits());
        LOGGER.info("Voltage remote control: {}", parametersExt.hasVoltageRemoteControl());
        LOGGER.info("Phase control: {}", parameters.isPhaseShifterRegulationOn());
        LOGGER.info("Split shunt admittance: {}", parameters.isTwtSplitShuntAdmittance());
        LOGGER.info("Direct current: {}", parameters.isDc());
        LOGGER.info("Transformer voltage control: {}", parameters.isTransformerVoltageControlOn());
        LOGGER.info("Load power factor constant: {}", parametersExt.isLoadPowerFactorConstant());
        LOGGER.info("Plausible active power limit: {}", parametersExt.getPlausibleActivePowerLimit());
        LOGGER.info("Add ratio to lines with different nominal voltage at both ends: {}", parametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds());
        LOGGER.info("Slack bus Pmax mismatch: {}", parametersExt.getSlackBusPMaxMismatch());
        LOGGER.info("Connected component mode: {}", parameters.getConnectedComponentMode());

        List<OuterLoop> outerLoops = new ArrayList<>();
        if (parameters.isDistributedSlack()) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant());
            outerLoops.add(new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.isThrowsExceptionInCaseOfSlackDistributionFailure(), parametersExt.getSlackBusPMaxMismatch()));
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
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Outer loops: {}", outerLoops.stream().map(OuterLoop::getType).collect(Collectors.toList()));
        }

        return new AcLoadFlowParameters(slackBusSelector,
                                        voltageInitializer,
                                        stoppingCriteria,
                                        outerLoops, matrixFactory,
                                        parametersExt.hasVoltageRemoteControl(),
                                        parameters.isPhaseShifterRegulationOn(),
                                        parameters.isTransformerVoltageControlOn(),
                                        parametersExt.getLowImpedanceBranchMode() == OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE,
                                        parameters.isTwtSplitShuntAdmittance(),
                                        breakers,
                                        parametersExt.getPlausibleActivePowerLimit(),
                                        forceA1Var,
                                        parametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(),
                                        branchesWithCurrent,
                                        parameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN,
                                        parameters.getCountriesToBalance(),
                  parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
    }

    private LoadFlowResult runAc(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Reporter reporter) {
        AcLoadFlowParameters acParameters = createAcParameters(network, matrixFactory, parameters, parametersExt, false);
        List<AcLoadFlowResult> results = AcloadFlowEngine.run(network, acParameters, reporter);

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
                double z = Math.hypot(line.getR(), line.getX());
                return z / zb <= LOW_IMPEDANCE_THRESHOLD;
            }).complete();
        }

        return new LoadFlowResultImpl(ok, Collections.emptyMap(), null, componentResults);
    }

    private LoadFlowResult runDc(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Reporter reporter) {
        SlackBusSelector slackBusSelector = getSlackBusSelector(network, parameters, parametersExt);

        LOGGER.info("Slack bus selector: {}", slackBusSelector.getClass().getSimpleName());
        LOGGER.info("Use transformer ratio: {}", parameters.isDcUseTransformerRatio());
        LOGGER.info("Distributed slack: {}", parameters.isDistributedSlack());
        LOGGER.info("Balance type: {}", parameters.getBalanceType());
        LOGGER.info("Plausible active power limit: {}", parametersExt.getPlausibleActivePowerLimit());
        LOGGER.info("Add ratio to lines with different nominal voltage at both ends: {}", parametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds());
        LOGGER.info("Connected component mode: {}", parameters.getConnectedComponentMode());

        DcLoadFlowParameters dcParameters = new DcLoadFlowParameters(slackBusSelector,
                                                                     matrixFactory,
                                                                     true,
                                                                     parameters.isDcUseTransformerRatio(),
                                                                     parameters.isDistributedSlack(),
                                                                     parameters.getBalanceType(),
                                                                     forcePhaseControlOffAndAddAngle1Var,
                                                                     parametersExt.getPlausibleActivePowerLimit(),
                                                                     parametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(),
                                                                     true,
                                                                     parameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN,
                                                                      parameters.getCountriesToBalance());

        List<DcLoadFlowResult> results = new DcLoadFlowEngine(network, dcParameters, reporter)
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

        OpenLoadFlowParameters parametersExt = getParametersExt(parameters);

        Reporter lfReporter = reporter.createSubReporter("loadFlow", "Load flow on network ${networkId}",
            "networkId", network.getId());

        return CompletableFuture.supplyAsync(() -> {

            network.getVariantManager().setWorkingVariant(workingVariantId);

            Stopwatch stopwatch = Stopwatch.createStarted();

            LoadFlowResult result = parameters.isDc() ? runDc(network, parameters, parametersExt, lfReporter)
                                                      : runAc(network, parameters, parametersExt, lfReporter);

            stopwatch.stop();
            LOGGER.info(Markers.PERFORMANCE_MARKER, "Load flow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return result;
        });
    }
}
