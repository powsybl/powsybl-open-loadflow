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

/**
 * @author Alexandre Le Jean <alexandre.le-jean at artelys.com>
 */
public class CustomNewtonRaphsonStoppingCriteria implements NewtonRaphsonStoppingCriteria {

    /**
     * Convergence epsilon per equation: 10^-4 in p.u => 10^-2 in Kv, Mw or MVar
     */
    //FIXME what to do with this value?
    public static final double DEFAULT_CONV_EPS_PER_EQ = Math.pow(10, -4);

    //FIXME what to do with this value?
    private final double convEpsPerEq;

    private final double maxActivePowerMismatch;

    private final double maxReactivePowerMismatch;

    private final double maxVoltageMismatch;

    public CustomNewtonRaphsonStoppingCriteria(double convEpsPerEq, double maxActivePowerMismatch,
                                               double maxReactivePowerMismatch, double maxVoltageMismatch) {
        this.convEpsPerEq = convEpsPerEq;
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
                    if (fx[idx] >= maxActivePowerMismatch) {
                        return false;
                    }
                    break;
                case BRANCH_TARGET_Q:
                case BUS_TARGET_Q:
                case DISTR_Q:
                case DUMMY_TARGET_Q:
                    if (fx[idx] >= maxReactivePowerMismatch) {
                        return false;
                    }
                    break;
                case BUS_TARGET_V:
                case BUS_TARGET_V_WITH_SLOPE:
                case ZERO_V:
                    if (fx[idx] >= maxVoltageMismatch) {
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
