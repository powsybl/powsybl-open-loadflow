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

    // FIXME Any idea for the name?
    private final double defaultValueAlpha = Math.pow(10, -5);

    // FIXME Any idea for the name?
    private final double defaultValueRho = Math.pow(10, -5);

    // FIXME Any idea for the name?
    private final double defaultValueB = Math.pow(10, -5);

    // FIXME Any idea for the name?
    private final double defaultValuePhi = Math.pow(10, -5);

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
                //FIXME is the following list of cases correct?
                case BRANCH_TARGET_RHO1:
                case DISTR_RHO:
                    if (Math.abs(fx[idx]) >= defaultValueRho) {
                        return false;
                    }
                    break;
                case DISTR_SHUNT_B:
                case SHUNT_TARGET_B:
                    if (Math.abs(fx[idx]) >= defaultValueB) {
                        return false;
                    }
                    break;
                case BUS_TARGET_PHI:
                case ZERO_PHI:
                    if (Math.abs(fx[idx]) >= defaultValuePhi) {
                        return false;
                    }
                    break;
                case BRANCH_TARGET_ALPHA1:
                    if (Math.abs(fx[idx]) >= defaultValueAlpha) {
                        return false;
                    }
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
