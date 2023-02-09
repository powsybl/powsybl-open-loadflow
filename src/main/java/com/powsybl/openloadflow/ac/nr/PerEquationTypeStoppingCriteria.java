/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
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
public class PerEquationTypeStoppingCriteria implements NewtonRaphsonStoppingCriteria {

    private final double maxDefaultAngleMismatch;

    private final double maxDefaultRatioMismatch;

    private final double maxDefaultSusceptanceMismatch;

    private final double maxActivePowerMismatch;

    private final double maxReactivePowerMismatch;

    private final double maxVoltageMismatch;

    public PerEquationTypeStoppingCriteria(double maxActivePowerMismatch,
                                           double maxReactivePowerMismatch, double maxVoltageMismatch,
                                           double maxDefaultAngleMismatch, double maxDefaultRatioMismatch,
                                           double maxDefaultSusceptanceMismatch) {
        this.maxActivePowerMismatch = maxActivePowerMismatch;
        this.maxReactivePowerMismatch = maxReactivePowerMismatch;
        this.maxVoltageMismatch = maxVoltageMismatch;
        this.maxDefaultAngleMismatch = maxDefaultAngleMismatch;
        this.maxDefaultRatioMismatch = maxDefaultRatioMismatch;
        this.maxDefaultSusceptanceMismatch = maxDefaultSusceptanceMismatch;
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
                        return false;
                    }
                    break;
                case BRANCH_TARGET_RHO1:
                case DISTR_RHO:
                    if (Math.abs(fx[idx]) >= maxDefaultRatioMismatch) {
                        return false;
                    }
                    break;
                case DISTR_SHUNT_B:
                case SHUNT_TARGET_B:
                    if (Math.abs(fx[idx]) >= maxDefaultSusceptanceMismatch) {
                        return false;
                    }
                    break;
                case BUS_TARGET_PHI:
                case ZERO_PHI:
                case BRANCH_TARGET_ALPHA1:
                    if (Math.abs(fx[idx]) >= maxDefaultAngleMismatch) {
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
