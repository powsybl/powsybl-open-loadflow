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
import com.powsybl.openloadflow.util.Profiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NewtonRaphson {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewtonRaphson.class);

    private final LfNetwork network;

    private final MatrixFactory matrixFactory;

    private final Profiler profiler;

    private final EquationSystem equationSystem;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private int iteration = 0;

    private final JacobianMatrix j;

    public NewtonRaphson(LfNetwork network, MatrixFactory matrixFactory, Profiler profiler,
                         EquationSystem equationSystem, JacobianMatrix j, NewtonRaphsonStoppingCriteria stoppingCriteria) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.profiler = Objects.requireNonNull(profiler);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.j = Objects.requireNonNull(j);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
    }

    public void logLargestMismatches(double[] mismatch, EquationSystem equationSystem, int count) {
        if (LOGGER.isTraceEnabled()) {
            Map<Equation, Double> mismatchByEquation = new HashMap<>(equationSystem.getSortedEquationsToSolve().size());
            for (Equation equation : equationSystem.getSortedEquationsToSolve().keySet()) {
                mismatchByEquation.put(equation, mismatch[equation.getColumn()]);
            }
            mismatchByEquation.entrySet().stream()
                    .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                    .sorted(Comparator.comparingDouble((Map.Entry<Equation, Double> e) -> Math.abs(e.getValue())).reversed())
                    .limit(count)
                    .forEach(e -> LOGGER.trace("Mismatch for {}: {}", e.getKey(), e.getValue()));
        }
    }

    private NewtonRaphsonStatus runIteration(double[] fx, double[] targets, double[] x) {
        LOGGER.debug("Start iteration {}", iteration);

        profiler.beforeTask("NewtonRaphsonIterationRun");

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

            Vectors.minus(fx, targets);

            // test stopping criteria and log norm(fx)
            logLargestMismatches(fx, equationSystem, iteration);

            profiler.beforeTask("StoppingCriteriaEvaluation");

            NewtonRaphsonStoppingCriteria.TestResult testResult = stoppingCriteria.test(fx);

            LOGGER.debug("|f(x)|={}", testResult.getNorm());

            profiler.afterTask("StoppingCriteriaEvaluation");

            if (testResult.isStop()) {
                return NewtonRaphsonStatus.CONVERGED;
            }

            return null;
        } finally {
            iteration++;
            profiler.afterTask("NewtonRaphsonIterationRun");
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

        profiler.beforeTask("VoltageInitializerPreparation");

        voltageInitializer.prepare(network, matrixFactory, profiler);

        profiler.afterTask("VoltageInitializerPreparation");

        double[] x = equationSystem.createStateVector(voltageInitializer);

        equationSystem.updateEquations(x);

        // initialize target vector
        double[] targets = equationSystem.createTargetVector();

        // initialize mismatch vector (difference between equation values and targets)
        double[] fx = equationSystem.createEquationVector();

        Vectors.minus(fx, targets);

        // start iterations
        NewtonRaphsonStatus status = NewtonRaphsonStatus.NO_CALCULATION;
        while (iteration <= parameters.getMaxIteration()) {
            NewtonRaphsonStatus newStatus = runIteration(fx, targets, x);
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
