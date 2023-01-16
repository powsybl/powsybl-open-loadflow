/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Vectors;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Alexandre Le Jean <alexandre.le-jean at artelys.com>
 */
public class CustomNewtonRaphsonStoppingCriteria implements NewtonRaphsonStoppingCriteria {

    private final double maxActivePowerMismatch;

    private final double maxReactivePowerMismatch;

    private final double maxVoltageMismatch;

    public CustomNewtonRaphsonStoppingCriteria(double maxActivePowerMismatch,
                                               double maxReactivePowerMismatch, double maxVoltageMismatch) {
        this.maxActivePowerMismatch = maxActivePowerMismatch;
        this.maxReactivePowerMismatch = maxReactivePowerMismatch;
        this.maxVoltageMismatch = maxVoltageMismatch;
    }

    private double computeNorm(double[] fx) {
        return Vectors.norm2(fx);
    }

    private boolean computeStop(double[] fx, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for (var eq : equationSystem.getIndex().getSortedEquationsToSolve()) {
            var type = eq.getType();
            var idx = eq.getColumn();
            switch (type) {
                case BRANCH_TARGET_P:
                case BUS_TARGET_P:
                case DUMMY_TARGET_P:
                    if (Math.abs(fx[idx]) * PerUnit.SB >= maxActivePowerMismatch) {
                        return false;
                    }
                    break;
                case BRANCH_TARGET_Q:
                case BUS_TARGET_Q:
                case DISTR_Q:
                case DUMMY_TARGET_Q:
                    if (Math.abs(fx[idx]) * PerUnit.SB >= maxReactivePowerMismatch) {
                        return false;
                    }
                    break;
                case BUS_TARGET_V:
                case BUS_TARGET_V_WITH_SLOPE:
                case ZERO_V:
                    if (Math.abs(fx[idx]) >= maxVoltageMismatch) {
                    // FIXME, condition must be : Math.abs(fx[idx]) * PerUnit.zb(idx_nominal_voltage) >= maxVoltageMismatch
                    //  We need the value NominalVoltage associated to the equation
                    //  either get the value or the associated BUS to fetch the information
                        return false;
                    }
                    break;
                default:
                    //FIXME how to deal default case?
                    break;
            }
        }
        return true;
    }

    @Override
    public TestResult test(double[] fx, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return new TestResult(computeStop(fx, equationSystem), computeNorm(fx));
    }
}
