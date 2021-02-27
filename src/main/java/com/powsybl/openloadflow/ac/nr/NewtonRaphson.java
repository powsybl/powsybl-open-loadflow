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

    public NewtonRaphson(LfNetwork network, MatrixFactory matrixFactory, EquationSystem equationSystem, JacobianMatrix j,
                         NewtonRaphsonStoppingCriteria stoppingCriteria) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.j = Objects.requireNonNull(j);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
    }

    private final class NewtonRaphsonContext {

        private final double[] fx;

        private final double[] targets;

        private final double[] x;

        private double[] oldX;

        private double[] dx;

        private double norm = Double.NaN;

        private NewtonRaphsonContext(double[] fx, double[] targets, double[] x) {
            this.fx = fx;
            this.targets = targets;
            this.x = x;
        }
    }

    private NewtonRaphsonStatus runIteration(NewtonRaphsonContext context) {
        LOGGER.debug("Start iteration {}", iteration);

        try {
            // solve f(x) = j * dx
            if (context.dx == null) {
                context.dx = new double[context.fx.length];
            }
            System.arraycopy(context.fx, 0, context.dx, 0, context.fx.length);
            try {
                j.solveTransposed(context.dx);
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
                return NewtonRaphsonStatus.SOLVER_FAILED;
            }

            // save x
            if (context.oldX == null) {
                context.oldX = new double[context.x.length];
            }
            System.arraycopy(context.x, 0, context.oldX, 0, context.x.length);

            // update x
            Vectors.minus(context.x, context.dx);

            // evaluate equation terms with new x
            equationSystem.updateEquations(context.x);

            // recalculate f(x) with new x
            equationSystem.updateEquationVector(context.fx);

            Vectors.minus(context.fx, context.targets);
            // now fx contains mismatch

            if (LOGGER.isTraceEnabled()) {
                equationSystem.findLargestMismatches(context.fx, 5)
                        .forEach(e -> LOGGER.trace("Mismatch for {}: {}", e.getKey(), e.getValue()));
            }

            // test stopping criteria and log norm(fx)
            NewtonRaphsonStoppingCriteria.TestResult testResult = stoppingCriteria.test(context.fx);
            double norm = testResult.getNorm();
            LOGGER.debug("|f(x)|={}", norm);

            if (testResult.isStop()) {
                return NewtonRaphsonStatus.CONVERGED;
            }

            if (context.norm < norm) {
                LOGGER.info("OUPS " + context.norm + " " + norm);
                double mu = 1;
                for (int i = 0; i < 10 && context.norm < norm; i++) {
                    mu /= 4;

                    // restore x from previous iteration
                    System.arraycopy(context.oldX, 0, context.x, 0, context.oldX.length);

                    // update x
                    Vectors.minus(context.x, context.dx, mu);

                    // evaluate equation terms with new x
                    equationSystem.updateEquations(context.x);

                    // recalculate f(x) with new x
                    equationSystem.updateEquationVector(context.fx);

                    Vectors.minus(context.fx, context.targets);
                    // now fx contains mismatch

                    testResult = stoppingCriteria.test(context.fx);
                    norm = testResult.getNorm();
                    LOGGER.debug("New |f(x)|={}", norm);

                    if (testResult.isStop()) {
                        return NewtonRaphsonStatus.CONVERGED;
                    }
                }

//                throw new AssertionError("OUPS");
            }

            // save norm for next iteration check
            context.norm = norm;

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

        // initialize target vector
        double[] targets = equationSystem.createTargetVector();

        // initialize mismatch vector (difference between equation values and targets)
        double[] fx = equationSystem.createEquationVector();

        Vectors.minus(fx, targets);

        // start iterations
        NewtonRaphsonContext context = new NewtonRaphsonContext(fx, targets, x);
        NewtonRaphsonStatus status = NewtonRaphsonStatus.NO_CALCULATION;
        while (iteration <= parameters.getMaxIteration()) {
            NewtonRaphsonStatus newStatus = runIteration(context);
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
            equationSystem.updateNetwork(x);
        }

        return new NewtonRaphsonResult(status, iteration, slackBusActivePowerMismatch);
    }
}
