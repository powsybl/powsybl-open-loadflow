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

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final AcLoadFlowParameters parameters;

    public AcloadFlowEngine(Object network, AcLoadFlowParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters);
        networks = LfNetwork.load(network, parameters.getSlackBusSelector(), parameters.isGeneratorVoltageRemoteControl());
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

    public AcLoadFlowResult run() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        // only process main (largest) connected component
        LfNetwork network = networks.get(0);

        parameters.getObserver().beforeEquationSystemCreation();

        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = AcEquationSystem.create(network, variableSet, parameters.isGeneratorVoltageRemoteControl());

        parameters.getObserver().afterEquationSystemCreation();

        NewtonRaphsonResult lastNrResult;
        int outerLoopIteration = 0;
        try (NewtonRaphson newtonRaphson = new NewtonRaphson(network, parameters.getMatrixFactory(), parameters.getObserver(), equationSystem, parameters.getStoppingCriteria())) {

            NewtonRaphsonParameters nrParameters = new NewtonRaphsonParameters().setVoltageInitializer(parameters.getVoltageInitializer());

            // initial Newton-Raphson
            lastNrResult = newtonRaphson.run(nrParameters);

            updatePvBusesReactivePower(lastNrResult, network, equationSystem);

            // for each outer loop re-run Newton-Raphson until stabilization
            // outer loops are nested: inner most loop first in the list, outer mosy loop last
            for (OuterLoop outerLoop : parameters.getOuterLoops()) {
                OuterLoopStatus outerLoopStatus;
                do {
                    parameters.getObserver().beforeOuterLoopStatusCheck(outerLoopIteration, outerLoop.getName());

                    // check outer loop status
                    outerLoopStatus = outerLoop.check(new OuterLoopContext(outerLoopIteration, network, equationSystem, variableSet, lastNrResult));

                    parameters.getObserver().afterOuterLoopStatusCheck(outerLoopIteration, outerLoop.getName(), outerLoopStatus == OuterLoopStatus.STABLE);

                    if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                        parameters.getObserver().beforeOuterLoopBody(outerLoopIteration, outerLoop.getName());

                        // if not yet stable, restart Newton-Raphson
                        lastNrResult = newtonRaphson.run(nrParameters);

                        parameters.getObserver().afterOuterLoopBody(outerLoopIteration, outerLoop.getName());

                        updatePvBusesReactivePower(lastNrResult, network, equationSystem);

                        outerLoopIteration++;
                    }
                } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
            }
        }

        stopwatch.stop();

        int nrIterations = lastNrResult.getIteration();
        int outerLoopIterations = outerLoopIteration + 1;

        LOGGER.debug(Markers.PERFORMANCE_MARKER, "Ac loadflow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        AcLoadFlowResult result = new AcLoadFlowResult(networks, outerLoopIterations, nrIterations, lastNrResult.getStatus(), lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete (result={})", result);

        return result;
    }
}
