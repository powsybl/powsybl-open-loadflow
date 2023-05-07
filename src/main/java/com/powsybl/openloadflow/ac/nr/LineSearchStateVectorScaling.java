/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LineSearchStateVectorScaling implements StateVectorScaling {

    private static final Logger LOGGER = LoggerFactory.getLogger(LineSearchStateVectorScaling.class);

    public enum NormUpperBoundFunctionType {
        CONSTANT,
        EXPONENTIAL
    }

    interface NormUpperBoundFunction {

        double getUpperBound(double previousNorm, double stepSize);

        static NormUpperBoundFunction find(NormUpperBoundFunctionType type) {
            switch (type) {
                case CONSTANT:
                    return new ConstantNormUpperBound();
                case EXPONENTIAL:
                    return new ExpNormUpperBound();
                default:
                    throw new IllegalStateException("Unknown norm upper bound function type: " + type);
            }
        }
    }

    static class ConstantNormUpperBound implements NormUpperBoundFunction {

        @Override
        public double getUpperBound(double previousNorm, double stepSize) {
            return previousNorm;
        }
    }

    static class ExpNormUpperBound implements NormUpperBoundFunction {

        @Override
        public double getUpperBound(double previousNorm, double stepSize) {
            return previousNorm * Math.exp(-stepSize);
        }
    }

    public static final int MAX_ITERATION_DEFAULT_VALUE = 5;
    public static final double STEP_FOLD_DEFAULT_VALUE = 4d;
    public static final NormUpperBoundFunctionType NORM_UPPER_BOUND_FUNCTION_TYPE_DEFAULT_VALUE = NormUpperBoundFunctionType.EXPONENTIAL;

    private final int maxIterations;
    private final double stepFold;
    private final NormUpperBoundFunction normUpperBoundFunction;

    private double[] lastDx;

    private NewtonRaphsonStoppingCriteria.TestResult lastTestResult;

    public LineSearchStateVectorScaling(int maxIterations, double stepFold, NormUpperBoundFunctionType normUpperBoundFunctionType,
                                        NewtonRaphsonStoppingCriteria.TestResult initialTestResult) {
        this.maxIterations = maxIterations;
        this.stepFold = stepFold;
        this.normUpperBoundFunction = NormUpperBoundFunction.find(normUpperBoundFunctionType);
        this.lastTestResult = Objects.requireNonNull(initialTestResult);
    }

    @Override
    public StateVectorScalingMode getMode() {
        return StateVectorScalingMode.LINE_SEARCH;
    }

    @Override
    public void apply(double[] dx, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // just save dx vector
        if (lastDx == null || lastDx.length != dx.length) {
            lastDx = dx.clone();
        } else {
            System.arraycopy(dx, 0, lastDx, 0, dx.length);
        }
    }

    @Override
    public NewtonRaphsonStoppingCriteria.TestResult applyAfter(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                               EquationVector<AcVariableType, AcEquationType> equationVector,
                                                               TargetVector<AcVariableType, AcEquationType> targetVector,
                                                               NewtonRaphsonStoppingCriteria stoppingCriteria,
                                                               NewtonRaphsonStoppingCriteria.TestResult testResult) {
        StateVector stateVector = equationSystem.getStateVector();
        if (lastTestResult != null) {
            double stepSize = 1;
            NewtonRaphsonStoppingCriteria.TestResult currentTestResult = testResult;
            double[] x = null;
            int iteration = 1;
            while (currentTestResult.getNorm() >= normUpperBoundFunction.getUpperBound(lastTestResult.getNorm(), -stepSize)
                    && iteration <= maxIterations) {
                if (x == null) {
                    x = stateVector.get();
                }

                // x(i+1) = x(i) - dx
                // x(i) = x(i+1) + dx
                // x(i+1)' = x(i) - dx * mu
                // x(i+1)' = x(i+1) + dx (1 - mu)
                double[] newX = x.clone();
                stepSize = 1 / Math.pow(stepFold, iteration);
                Vectors.plus(newX, lastDx, 1 - stepSize);
                stateVector.set(newX);
                // equation vector has been updated

                // recompute mismatch with new x
                equationVector.minus(targetVector);

                // and recompute new norm
                currentTestResult = stoppingCriteria.test(equationVector.getArray(), equationSystem);

                iteration++;
            }
            lastTestResult = currentTestResult;
            if (iteration > maxIterations) {
                LOGGER.warn("Minimal step size have been reached with max number of iterations: {}", stepSize);
            } else {
                LOGGER.debug("Step size: {}", stepSize);
            }
        }
        return lastTestResult;
    }
}
