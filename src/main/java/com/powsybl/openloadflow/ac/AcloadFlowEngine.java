/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.google.common.collect.Lists;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.openloadflow.lf.LoadFlowEngine;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkLoader;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine implements LoadFlowEngine<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final AcLoadFlowContext context;

    public AcloadFlowEngine(AcLoadFlowContext context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public AcLoadFlowContext getContext() {
        return context;
    }

    private static class RunningContext {

        private NewtonRaphsonResult lastNrResult;

        private final Map<String, MutableInt> outerLoopIterationByType = new HashMap<>();

        private int outerLoopTotalIterations = 0;

        private final MutableInt nrTotalIterations = new MutableInt();
    }

    private void runOuterLoop(OuterLoop outerLoop, OuterLoopContextImpl outerLoopContext, NewtonRaphson newtonRaphson, RunningContext runningContext) {
        Reporter olReporter = Reports.createOuterLoopReporter(outerLoopContext.getNetwork().getReporter(), outerLoop.getType());

        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.outerLoopIterationByType.computeIfAbsent(outerLoop.getType(), k -> new MutableInt());

            // check outer loop status
            outerLoopContext.setIteration(outerLoopIteration.getValue());
            outerLoopContext.setLastNewtonRaphsonResult(runningContext.lastNrResult);
            outerLoopContext.setAcLoadFlowContext(context);
            outerLoopStatus = outerLoop.check(outerLoopContext, olReporter);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop '{}' iteration {}", outerLoop.getType(), runningContext.outerLoopTotalIterations);

                Reporter nrReporter = context.getNetwork().getReporter();
                if (context.getParameters().getNewtonRaphsonParameters().isDetailedReport()) {
                    nrReporter = Reports.createDetailedNewtonRaphsonReporterOuterLoop(nrReporter,
                            context.getNetwork().getNumCC(),
                            context.getNetwork().getNumSC(),
                            outerLoopIteration.toInteger() + 1, outerLoop.getType());
                }

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(new PreviousValueVoltageInitializer(), nrReporter);

                runningContext.nrTotalIterations.add(runningContext.lastNrResult.getIterations());
                runningContext.outerLoopTotalIterations++;

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE
                && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED
                && runningContext.outerLoopTotalIterations < context.getParameters().getMaxOuterLoopIterations());
    }

    @Override
    public AcLoadFlowResult run() {
        LOGGER.info("Start AC loadflow on network {}", context.getNetwork());

        VoltageInitializer voltageInitializer = context.getParameters().getVoltageInitializer();
        // in case of a DC voltage initializer, an DC equation system in created and equations are attached
        // to the network. It is important that DC init is done before AC equation system is created by
        // calling ACLoadContext.getEquationSystem to avoid DC equations overwrite AC ones in the network.
        voltageInitializer.prepare(context.getNetwork());

        RunningContext runningContext = new RunningContext();
        NewtonRaphson newtonRaphson = new NewtonRaphson(context.getNetwork(),
                                                        context.getParameters().getNewtonRaphsonParameters(),
                                                        context.getEquationSystem(),
                                                        context.getJacobianMatrix(),
                                                        context.getTargetVector(),
                                                        context.getEquationVector());

        List<OuterLoop> outerLoops = context.getParameters().getOuterLoops();
        List<Pair<OuterLoop, OuterLoopContextImpl>> outerLoopsAndContexts = outerLoops.stream()
                .map(outerLoop -> Pair.of(outerLoop, new OuterLoopContextImpl(context.getNetwork())))
                .collect(Collectors.toList());

        // outer loops initialization
        for (var outerLoopAndContext : outerLoopsAndContexts) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.initialize(outerLoopContext);
        }

        Reporter nrReporter = context.getNetwork().getReporter();
        if (context.getParameters().getNewtonRaphsonParameters().isDetailedReport()) {
            nrReporter = Reports.createDetailedNewtonRaphsonReporter(nrReporter,
                    context.getNetwork().getNumCC(),
                    context.getNetwork().getNumSC());
        }
        // run initial Newton-Raphson
        runningContext.lastNrResult = newtonRaphson.run(voltageInitializer, nrReporter);
        double initialSlackBusActivePowerMismatch = runningContext.lastNrResult.getSlackBusActivePowerMismatch();

        runningContext.nrTotalIterations.add(runningContext.lastNrResult.getIterations());

        // continue with outer loops only if initial Newton-Raphson succeed
        if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {

            // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
            int oldNrTotalIterations;
            do {
                oldNrTotalIterations = runningContext.nrTotalIterations.getValue();

                // outer loops are nested: inner most loop first in the list, outer most loop last
                for (var outerLoopAndContext : outerLoopsAndContexts) {
                    runOuterLoop(outerLoopAndContext.getLeft(), outerLoopAndContext.getRight(), newtonRaphson, runningContext);

                    // continue with next outer loop only if last Newton-Raphson succeed
                    // and we have not reach max number of outer loop iteration
                    if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED
                            || runningContext.outerLoopTotalIterations >= context.getParameters().getMaxOuterLoopIterations()) {
                        break;
                    }
                }
            } while (runningContext.nrTotalIterations.getValue() > oldNrTotalIterations
                    && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED
                    && runningContext.outerLoopTotalIterations < context.getParameters().getMaxOuterLoopIterations());
        }

        // outer loops finalization (in reverse order to allow correct cleanup)
        for (var outerLoopAndContext : Lists.reverse(outerLoopsAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.cleanup(outerLoopContext);
        }

        OuterLoopStatus outerLoopFinalStatus = runningContext.outerLoopTotalIterations < context.getParameters().getMaxOuterLoopIterations()
                ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;

        AcLoadFlowResult result = new AcLoadFlowResult(context.getNetwork(),
                                                       runningContext.outerLoopTotalIterations,
                                                       runningContext.nrTotalIterations.getValue(),
                                                       runningContext.lastNrResult.getStatus(),
                                                       outerLoopFinalStatus,
                                                       runningContext.lastNrResult.getSlackBusActivePowerMismatch(),
                                                       initialSlackBusActivePowerMismatch - runningContext.lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete on network {} (result={})", context.getNetwork(), result);

        Reports.reportAcLfComplete(context.getNetwork().getReporter(), result.getNewtonRaphsonStatus().name(),
                result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED ? TypedValue.INFO_SEVERITY : TypedValue.ERROR_SEVERITY);

        context.setResult(result);

        return result;
    }

    public static <T> List<AcLoadFlowResult> run(T network, LfNetworkLoader<T> networkLoader, AcLoadFlowParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reporter)
                .stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (AcLoadFlowContext context = new AcLoadFlowContext(n, parameters)) {
                            return new AcloadFlowEngine(context)
                                    .run();
                        }
                    }
                    return AcLoadFlowResult.createNoCalculationResult(n);
                })
                .collect(Collectors.toList());
    }
}
