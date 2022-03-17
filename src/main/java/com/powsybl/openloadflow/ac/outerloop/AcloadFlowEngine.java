/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkLoader;
import org.apache.commons.lang3.mutable.MutableInt;
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
public class AcloadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final AcLoadFlowContext context;

    public AcloadFlowEngine(AcLoadFlowContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public AcLoadFlowContext getContext() {
        return context;
    }

    private static class RunningContext {

        private NewtonRaphsonResult lastNrResult;

        private final Map<String, MutableInt> outerLoopIterationByType = new HashMap<>();
    }

    private void runOuterLoop(OuterLoop outerLoop, LfNetwork network, NewtonRaphson newtonRaphson, RunningContext runningContext,
                              Reporter reporter) {
        Reporter olReporter = reporter.createSubReporter("OuterLoop", "Outer loop ${outerLoopType}", "outerLoopType", outerLoop.getType());

        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.outerLoopIterationByType.computeIfAbsent(outerLoop.getType(), k -> new MutableInt());

            // check outer loop status
            outerLoopStatus = outerLoop.check(new OuterLoopContext(outerLoopIteration.getValue(), network, runningContext.lastNrResult), olReporter);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop iteration {} (name='{}')", outerLoopIteration, outerLoop.getType());

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(reporter);
                if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                    return;
                }

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
    }

    public AcLoadFlowResult run() {
        return run(Reporter.NO_OP);
    }

    public AcLoadFlowResult run(Reporter reporter) {
        LOGGER.info("Start AC loadflow on network {}", context.getNetwork());

        RunningContext runningContext = new RunningContext();
        NewtonRaphson newtonRaphson = new NewtonRaphson(context.getNetwork(), context.getParameters().getNewtonRaphsonParameters(),
                context.getEquationSystem(), context.getJacobianMatrix(), context.getTargetVector(), context.getEquationVector());

        // outer loops initialization
        for (OuterLoop outerLoop : context.getParameters().getOuterLoops()) {
            outerLoop.initialize(context.getNetwork());
        }

        // run initial Newton-Raphson
        runningContext.lastNrResult = newtonRaphson.run(reporter);

        // continue with outer loops only if initial Newton-Raphson succeed
        if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {

            // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
            int oldIterationCount;
            do {
                oldIterationCount = runningContext.lastNrResult.getIteration();

                // outer loops are nested: inner most loop first in the list, outer most loop last
                for (OuterLoop outerLoop : context.getParameters().getOuterLoops()) {
                    runOuterLoop(outerLoop, context.getNetwork(), newtonRaphson, runningContext, reporter);

                    // continue with next outer loop only if last Newton-Raphson succeed
                    if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                        break;
                    }
                }
            } while (runningContext.lastNrResult.getIteration() > oldIterationCount
                    && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED);
        }

        // outer loops finalization
        for (OuterLoop outerLoop : context.getParameters().getOuterLoops()) {
            outerLoop.cleanup(context.getNetwork());
        }

        int nrIterations = runningContext.lastNrResult.getIteration();
        int outerLoopIterations = runningContext.outerLoopIterationByType.values().stream().mapToInt(MutableInt::getValue).sum() + 1;

        AcLoadFlowResult result = new AcLoadFlowResult(context.getNetwork(), outerLoopIterations, nrIterations, runningContext.lastNrResult.getStatus(),
                runningContext.lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete on network {} (result={})", context.getNetwork(), result);

        return result;
    }

    public static <T> List<AcLoadFlowResult> run(T network, LfNetworkLoader<T> networkLoader, AcLoadFlowParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reporter)
                .stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (AcLoadFlowContext context = new AcLoadFlowContext(n, parameters)) {
                            return new AcloadFlowEngine(context)
                                    .run(reporter);
                        }
                    }
                    return new AcLoadFlowResult(n, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, Double.NaN);
                })
                .collect(Collectors.toList());
    }
}
