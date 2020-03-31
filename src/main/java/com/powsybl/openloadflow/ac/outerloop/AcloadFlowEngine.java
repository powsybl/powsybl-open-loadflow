/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.base.Stopwatch;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final AcLoadFlowParameters parameters;

    public AcloadFlowEngine(Object network, AcLoadFlowParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters);
        networks = LfNetwork.load(network, parameters.getSlackBusSelector(), parameters.isVoltageRemoteControl());
    }

    private void updatePvBusesReactivePower(NewtonRaphsonResult lastNrResult, LfNetwork network, EquationSystem equationSystem) {
        if (lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            parameters.getObserver().beforePvBusesReactivePowerUpdate();

            for (LfBus bus : network.getBuses()) {
                if (bus.hasVoltageControl()) {
                    Equation q = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
                    bus.setCalculatedQ(q.eval());
                } else {
                    bus.setCalculatedQ(Double.NaN);
                }
            }

            parameters.getObserver().afterPvBusesReactivePowerUpdate();
        }
    }

    private static class OuterLoopRunningContext {

        private NewtonRaphsonResult lastNrResult;

        private int outerLoopIteration = 0;
    }

    private void runOuterLoop(OuterLoop outerLoop, LfNetwork network, EquationSystem equationSystem, VariableSet variableSet,
                              NewtonRaphson newtonRaphson, NewtonRaphsonParameters nrParameters, OuterLoopRunningContext runningContext) {
        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            parameters.getObserver().beforeOuterLoopStatusCheck(runningContext.outerLoopIteration, outerLoop.getName());

            // check outer loop status
            outerLoopStatus = outerLoop.check(new OuterLoopContext(runningContext.outerLoopIteration, network, equationSystem, variableSet, runningContext.lastNrResult));

            parameters.getObserver().afterOuterLoopStatusCheck(runningContext.outerLoopIteration, outerLoop.getName(), outerLoopStatus == OuterLoopStatus.STABLE);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                parameters.getObserver().beforeOuterLoopBody(runningContext.outerLoopIteration, outerLoop.getName());

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(nrParameters);
                if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                    return;
                }

                parameters.getObserver().afterOuterLoopBody(runningContext.outerLoopIteration, outerLoop.getName());

                // update PV buses reactive power some outer loops might need this information
                updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

                runningContext.outerLoopIteration++;
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
    }

    private AcLoadFlowResult run(LfNetwork network) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        LOGGER.info("Start Ac loadflow on network {}", network.getNum());

        parameters.getObserver().beforeEquationSystemCreation();

        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = AcEquationSystem.create(network, variableSet, parameters.isVoltageRemoteControl());

        parameters.getObserver().afterEquationSystemCreation();

        OuterLoopRunningContext runningContext = new OuterLoopRunningContext();
        try (NewtonRaphson newtonRaphson = new NewtonRaphson(network, parameters.getMatrixFactory(), parameters.getObserver(), equationSystem, parameters.getStoppingCriteria())) {

            NewtonRaphsonParameters nrParameters = new NewtonRaphsonParameters().setVoltageInitializer(parameters.getVoltageInitializer());

            // run initial Newton-Raphson
            runningContext.lastNrResult = newtonRaphson.run(nrParameters);

            // continue with outer loops only if initial Newton-Raphson succeed
            if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
                updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

                // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
                int oldIterationCount;
                do {
                    oldIterationCount = runningContext.lastNrResult.getIteration();

                    // outer loops are nested: inner most loop first in the list, outer most loop last
                    for (OuterLoop outerLoop : parameters.getOuterLoops()) {
                        runOuterLoop(outerLoop, network, equationSystem, variableSet, newtonRaphson, nrParameters, runningContext);

                        // continue with next outer loop only if last Newton-Raphson succeed
                        if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                            break;
                        }
                    }
                } while (runningContext.lastNrResult.getIteration() > oldIterationCount
                        && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED);
            }
        }

        stopwatch.stop();

        int nrIterations = runningContext.lastNrResult.getIteration();
        int outerLoopIterations = runningContext.outerLoopIteration + 1;

        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Ac loadflow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        AcLoadFlowResult result = new AcLoadFlowResult(network, outerLoopIterations, nrIterations, runningContext.lastNrResult.getStatus(),
                runningContext.lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete (result={})", result);

        return result;
    }

    public List<AcLoadFlowResult> run() {
        return networks.stream().map(this::run).collect(Collectors.toList());
    }
}
