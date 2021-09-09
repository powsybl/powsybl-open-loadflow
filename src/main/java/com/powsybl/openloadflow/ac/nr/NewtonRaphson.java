/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
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

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private int iteration = 0;

    private final JacobianMatrix<AcVariableType, AcEquationType> j;

    private final TargetVector<AcVariableType, AcEquationType> targetVector;

    public NewtonRaphson(LfNetwork network, MatrixFactory matrixFactory, EquationSystem<AcVariableType, AcEquationType> equationSystem, JacobianMatrix<AcVariableType, AcEquationType> j,
                         TargetVector<AcVariableType, AcEquationType> targetVector, NewtonRaphsonStoppingCriteria stoppingCriteria) {
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

    private double computeSlackBusActivePowerMismatch(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // search equation corresponding to slack bus active power injection
        LfBus slackBus = network.getSlackBus();
        Equation<AcVariableType, AcEquationType> slackBusActivePowerEquation = equationSystem.createEquation(slackBus.getNum(), AcEquationType.BUS_P);

        return slackBusActivePowerEquation.eval()
                - slackBus.getTargetP(); // slack bus can also have real injection connected
    }

    public static double[] createStateVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, VoltageInitializer initializer) {
        double[] x = new double[equationSystem.getSortedVariablesToFind().size()];
        for (Variable<AcVariableType> v : equationSystem.getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_V:
                    x[v.getRow()] = initializer.getMagnitude(network.getBus(v.getNum()));
                    break;

                case BUS_PHI:
                    x[v.getRow()] = Math.toRadians(initializer.getAngle(network.getBus(v.getNum())));
                    break;

                case BRANCH_ALPHA1:
                    x[v.getRow()] = network.getBranch(v.getNum()).getPiModel().getA1();
                    break;

                case BRANCH_RHO1:
                    x[v.getRow()] = network.getBranch(v.getNum()).getPiModel().getR1();
                    break;

                case DUMMY_P:
                case DUMMY_Q:
                    x[v.getRow()] = 0;
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type "  + v.getType());
            }
        }
        return x;
    }

    public void updateNetwork(double[] x) {
        // update state variable
        for (Variable<AcVariableType> v : equationSystem.getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_V:
                    // Equation must have been updated
                    break;

                case BUS_PHI:
                    network.getBus(v.getNum()).setAngle(Math.toDegrees(x[v.getRow()]));
                    break;

                case BRANCH_ALPHA1:
                    network.getBranch(v.getNum()).getPiModel().setA1(x[v.getRow()]);
                    break;

                case BRANCH_RHO1:
                    network.getBranch(v.getNum()).getPiModel().setR1(x[v.getRow()]);
                    break;

                case DUMMY_P:
                case DUMMY_Q:
                    // nothing to do
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type "  + v.getType());
            }
        }
    }

    public NewtonRaphsonResult run(NewtonRaphsonParameters parameters, Reporter reporter) {
        Objects.requireNonNull(parameters);

        // initialize state vector
        VoltageInitializer voltageInitializer = iteration == 0 ? parameters.getVoltageInitializer()
                                                               : new PreviousValueVoltageInitializer();

        voltageInitializer.prepare(network, matrixFactory, reporter);

        double[] x = createStateVector(network, equationSystem, voltageInitializer);

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
            updateNetwork(x);
        }

        return new NewtonRaphsonResult(status, iteration, slackBusActivePowerMismatch);
    }
}
