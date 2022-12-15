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

    private static final int MAX_ITERATION = 10;
    private static final double STEP_FOLD = 4d / 3;

    private double[] lastDx;

    private NewtonRaphsonStoppingCriteria.TestResult lastTestResult;

    public LineSearchStateVectorScaling(NewtonRaphsonStoppingCriteria.TestResult initialTestResult) {
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
    public NewtonRaphsonStoppingCriteria.TestResult applyAfter(StateVector stateVector,
                                                               EquationVector<AcVariableType, AcEquationType> equationVector,
                                                               TargetVector<AcVariableType, AcEquationType> targetVector,
                                                               NewtonRaphsonStoppingCriteria stoppingCriteria,
                                                               NewtonRaphsonStoppingCriteria.TestResult testResult) {
        if (lastTestResult != null) {
            double stepSize = 1;
            NewtonRaphsonStoppingCriteria.TestResult currentTestResult = testResult;
            double[] x = null;
            int iteration = 1;
            while (currentTestResult.getNorm() >= lastTestResult.getNorm() && iteration <= MAX_ITERATION) {
                if (x == null) {
                    x = stateVector.get();
                }

                // x(i+1) = x(i) - dx
                // x(i) = x(i+1) + dx
                // x(i+1)' = x(i) - dx * mu
                // x(i+1)' = x(i+1) + dx (1 - mu)
                double[] newX = x.clone();
                stepSize = 1 / Math.pow(STEP_FOLD, iteration);
                Vectors.plus(newX, lastDx, 1 - stepSize);
                stateVector.set(newX);
                // equation vector has been updated

                // recompute mismatch with new x
                equationVector.minus(targetVector);

                // and recompute new norm
                currentTestResult = stoppingCriteria.test(equationVector.getArray());

                iteration++;
            }
            lastTestResult = currentTestResult;
            LOGGER.debug("Step size: {}", stepSize);
        }
        return lastTestResult;
    }
}
