/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.base.Stopwatch;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.nr.*;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final LfNetwork network;

    private final VoltageInitializer voltageInitializer;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private final List<OuterLoop> outerLoops;

    private final MatrixFactory matrixFactory;

    private final AcLoadFlowObserver observer;

    public AcloadFlowEngine(LfNetwork network, VoltageInitializer voltageInitializer, NewtonRaphsonStoppingCriteria stoppingCriteria,
                            List<OuterLoop> outerLoops, MatrixFactory matrixFactory, AcLoadFlowObserver observer) {
        this.network = Objects.requireNonNull(network);
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.observer = Objects.requireNonNull(observer);
    }

    private void updatePvBusesReactivePower(NewtonRaphsonResult lastNrResult, EquationSystem equationSystem) {
        if (lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            observer.beforePvBusesReactivePowerUpdate();

            for (LfBus bus : network.getBuses()) {
                if (bus.hasVoltageControl()) {
                    Equation q = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
                    bus.setCalculatedQ(q.eval());
                } else {
                    bus.setCalculatedQ(Double.NaN);
                }
            }

            observer.afterPvBusesReactivePowerUpdate();
        }
    }

    public AcLoadFlowResult run() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        observer.beforeEquationSystemCreation();

        EquationSystem equationSystem = AcEquationSystem.create(network);

        observer.afterEquationSystemCreation();

        NewtonRaphsonResult lastNrResult;
        int outerLoopIteration = 0;
        try (NewtonRaphson newtonRaphson = new NewtonRaphson(network, matrixFactory, observer, equationSystem, stoppingCriteria)) {

            NewtonRaphsonParameters nrParameters = new NewtonRaphsonParameters().setVoltageInitializer(voltageInitializer);

            // initial Newton-Raphson
            lastNrResult = newtonRaphson.run(nrParameters);

            updatePvBusesReactivePower(lastNrResult, equationSystem);

            // for each outer loop re-run Newton-Raphson until stabilization
            // outer loops are nested: inner most loop first in the list, outer mosy loop last
            for (OuterLoop outerLoop : outerLoops) {
                OuterLoopStatus outerLoopStatus;
                do {
                    observer.beforeOuterLoopStatusCheck(outerLoopIteration, outerLoop.getName());

                    // check outer loop status
                    outerLoopStatus = outerLoop.check(new OuterLoopContext(outerLoopIteration, network, equationSystem, lastNrResult));

                    observer.afterOuterLoopStatusCheck(outerLoopIteration, outerLoop.getName(), outerLoopStatus == OuterLoopStatus.STABLE);

                    if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                        observer.beforeOuterLoopBody(outerLoopIteration, outerLoop.getName());

                        // if not yet stable, restart Newton-Raphson
                        lastNrResult = newtonRaphson.run(nrParameters);

                        observer.afterOuterLoopBody(outerLoopIteration, outerLoop.getName());

                        updatePvBusesReactivePower(lastNrResult, equationSystem);

                        outerLoopIteration++;
                    }
                } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
            }
        }

        stopwatch.stop();

        int nrIterations = lastNrResult.getIteration();
        int outerLoopIterations = outerLoopIteration + 1;

        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Ac loadflow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        AcLoadFlowResult result = new AcLoadFlowResult(outerLoopIterations, nrIterations, lastNrResult.getStatus(), lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete (result={})", result);

        return result;
    }
}
