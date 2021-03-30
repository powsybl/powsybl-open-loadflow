/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NewtonRaphson {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewtonRaphson.class);

    private final LfNetwork network;

    private final MatrixFactory matrixFactory;

    private final EquationSystem equationSystem;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private int iteration = 0;

    private final JacobianMatrix j;

    private final TargetVector targetVector;

    public NewtonRaphson(LfNetwork network, MatrixFactory matrixFactory, EquationSystem equationSystem, JacobianMatrix j,
                         TargetVector targetVector, NewtonRaphsonStoppingCriteria stoppingCriteria) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.j = Objects.requireNonNull(j);
        this.targetVector = Objects.requireNonNull(targetVector);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
    }

    private NewtonRaphsonStatus runIteration(double[] fx, double[] x) {
        LOGGER.debug("Start iteration {}", iteration);

        try {
            // solve f(x) = j * dx
            try {
                j.solveTransposed(fx);
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
                return NewtonRaphsonStatus.SOLVER_FAILED;
            }

            // update x
            Vectors.minus(x, fx);

            // evaluate equation terms with new x
            equationSystem.updateEquations(x);

            // recalculate f(x) with new x
            equationSystem.updateEquationVector(fx);

            Vectors.minus(fx, targetVector.toArray());

            if (LOGGER.isTraceEnabled()) {
                equationSystem.findLargestMismatches(fx, 5)
                        .forEach(e -> LOGGER.trace("Mismatch for {}: {}", e.getKey(), e.getValue()));
            }

            // test stopping criteria and log norm(fx)
            NewtonRaphsonStoppingCriteria.TestResult testResult = stoppingCriteria.test(fx);

            LOGGER.debug("|f(x)|={}", testResult.getNorm());

            if (testResult.isStop()) {
                return NewtonRaphsonStatus.CONVERGED;
            }

            return null;
        } finally {
            iteration++;
        }
    }

    private double computeSlackBusActivePowerMismatch(EquationSystem equationSystem) {
        // search equation corresponding to slack bus active power injection
        LfBus slackBus = network.getSlackBus();
        Equation slackBusActivePowerEquation = equationSystem.createEquation(slackBus.getNum(), EquationType.BUS_P);

        return slackBusActivePowerEquation.eval()
                - slackBus.getTargetP(); // slack bus can also have real injection connected
    }

    public NewtonRaphsonResult run(NewtonRaphsonParameters parameters) {
        Objects.requireNonNull(parameters);

        // initialize state vector
        VoltageInitializer voltageInitializer = iteration == 0 ? parameters.getVoltageInitializer()
                                                               : new PreviousValueVoltageInitializer();

        voltageInitializer.prepare(network, matrixFactory);

        double[] x = equationSystem.createStateVector(voltageInitializer);

        equationSystem.updateEquations(x);

        // initialize mismatch vector (difference between equation values and targets)
        double[] fx = equationSystem.createEquationVector();

        Vectors.minus(fx, targetVector.toArray());

        // start iterations
        NewtonRaphsonStatus status = NewtonRaphsonStatus.NO_CALCULATION;
        while (iteration <= parameters.getMaxIteration()) {
            NewtonRaphsonStatus newStatus = runIteration(fx, x);
            if (newStatus != null) {
                status = newStatus;
                break;
            }
        }

        if (iteration >= parameters.getMaxIteration()) {
            status = NewtonRaphsonStatus.MAX_ITERATION_REACHED;
        }

        double slackBusActivePowerMismatch = computeSlackBusActivePowerMismatch(equationSystem);

        // update network state variable
        if (status == NewtonRaphsonStatus.CONVERGED) {
            equationSystem.updateEquations(x, EquationSystem.EquationUpdateType.AFTER_NR);
            equationSystem.updateNetwork(x);
        }

        return new NewtonRaphsonResult(status, iteration, slackBusActivePowerMismatch);
    }
}
