package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.collect.Lists;
import com.powsybl.commons.reporter.Reporter;
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

public class DisymAcLoadFlowEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisymAcLoadFlowEngine.class);

    private final DisymAcLoadFlowContext context;

    public DisymAcLoadFlowEngine(DisymAcLoadFlowContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public DisymAcLoadFlowContext getContext() {
        return context;
    }

    private static class RunningContext {

        private NewtonRaphsonResult lastNrResult;

        private final Map<String, MutableInt> outerLoopIterationByType = new HashMap<>();
    }

    private void runOuterLoop(OuterLoop outerLoop, OuterLoopContextImpl outerLoopContext, NewtonRaphson newtonRaphson, DisymAcLoadFlowEngine.RunningContext runningContext) {
        Reporter olReporter = Reports.createOuterLoopReporter(outerLoopContext.getNetwork().getReporter(), outerLoop.getType());

        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.outerLoopIterationByType.computeIfAbsent(outerLoop.getType(), k -> new MutableInt());

            // check outer loop status
            outerLoopContext.setIteration(outerLoopIteration.getValue());
            outerLoopContext.setLastNewtonRaphsonResult(runningContext.lastNrResult);
            outerLoopContext.setDisymAcLoadFlowContext(context);
            outerLoopStatus = outerLoop.check(outerLoopContext, olReporter);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop iteration {} (name='{}')", outerLoopIteration, outerLoop.getType());

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(new PreviousValueVoltageInitializer());
                if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                    return;
                }

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
    }

    public DisymAcLoadFlowResult run() {
        LOGGER.info("Start DissymAC loadflow on network {}", context.getNetwork());

        VoltageInitializer voltageInitializer = context.getParameters().getVoltageInitializer();
        // in case of a DC voltage initializer, an DC equation system in created and equations are attached
        // to the network. It is important that DC init is done before AC equation system is created by
        // calling ACLoadContext.getEquationSystem to avoid DC equations overwrite AC ones in the network.
        voltageInitializer.prepare(context.getNetwork());

        DisymAcLoadFlowEngine.RunningContext runningContext = new DisymAcLoadFlowEngine.RunningContext();
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

        // run initial Newton-Raphson
        runningContext.lastNrResult = newtonRaphson.run(voltageInitializer);
        double initialSlackBusActivePowerMismatch = runningContext.lastNrResult.getSlackBusActivePowerMismatch();

        // continue with outer loops only if initial Newton-Raphson succeed
        if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {

            // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
            int oldIterationCount;
            do {
                oldIterationCount = runningContext.lastNrResult.getIteration();

                // outer loops are nested: inner most loop first in the list, outer most loop last
                for (var outerLoopAndContext : outerLoopsAndContexts) {
                    runOuterLoop(outerLoopAndContext.getLeft(), outerLoopAndContext.getRight(), newtonRaphson, runningContext);

                    // continue with next outer loop only if last Newton-Raphson succeed
                    if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                        break;
                    }
                }
            } while (runningContext.lastNrResult.getIteration() > oldIterationCount
                    && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED);
        }

        // outer loops finalization (in reverse order to allow correct cleanup)
        for (var outerLoopAndContext : Lists.reverse(outerLoopsAndContexts)) {
            var outerLoop = outerLoopAndContext.getLeft();
            var outerLoopContext = outerLoopAndContext.getRight();
            outerLoop.cleanup(outerLoopContext);
        }

        int nrIterations = runningContext.lastNrResult.getIteration();
        int outerLoopIterations = runningContext.outerLoopIterationByType.values().stream().mapToInt(MutableInt::getValue).sum() + 1;

        DisymAcLoadFlowResult result = new DisymAcLoadFlowResult(context.getNetwork(),
                outerLoopIterations,
                nrIterations,
                runningContext.lastNrResult.getStatus(),
                runningContext.lastNrResult.getSlackBusActivePowerMismatch(),
                initialSlackBusActivePowerMismatch - runningContext.lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! DisymAc loadflow complete on network {} (result={})", context.getNetwork(), result);

        Reports.reportAcLfComplete(context.getNetwork().getReporter(), result.getNewtonRaphsonStatus().name());

        return result;
    }

    public static <T> List<DisymAcLoadFlowResult> run(T network, LfNetworkLoader<T> networkLoader, DisymAcLoadFlowParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reporter)
                .stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (DisymAcLoadFlowContext context = new DisymAcLoadFlowContext(n, parameters)) {
                            return new DisymAcLoadFlowEngine(context)
                                    .run();
                        }
                    }
                    return new DisymAcLoadFlowResult(n, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, Double.NaN, Double.NaN);
                })
                .collect(Collectors.toList());
    }
}
