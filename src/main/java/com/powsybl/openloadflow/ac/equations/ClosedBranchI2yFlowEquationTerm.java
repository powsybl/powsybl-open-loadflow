/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchI2yFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchI2yFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                           boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType);
    }

    public double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        return 0;
    }

    /**
     * ignoring for now rho, we have:
     *  [I1x]   [ g1+g12  -b1-b12   -g12     b12   ]   [V1x]
     *  [I1y]   [ b1+b12   g1+g12   -b12    -g12   ]   [V1y]
     *  [I2x] = [  -g21     b21    g2+g21  -b2-b21 ] * [V2x]
     *  [I2y]   [  -b21    -g21    b2+b21   g2+g21 ]   [V2y]
     */
    public static double i2y(double g2, double b2, double v1, double ph1, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return -b21 * v1 * FastMath.cos(ph1) - g21 * v1 * FastMath.sin(ph1) + (b2 + b21) * v2 * FastMath.cos(ph2) + (g2 + g21) * v2 * FastMath.sin(ph2);
    }

    private static double di2ydv1(double ph1, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return -b21 * FastMath.cos(ph1) - g21 * FastMath.sin(ph1);
    }

    private static double di2ydv2(double g2, double b2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return (b2 + b21) * FastMath.cos(ph2) + (g2 + g21) * FastMath.sin(ph2);
    }

    private static double di2ydph1(double v1, double ph1, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return b21 * v1 * FastMath.sin(ph1) - g21 * v1 * FastMath.cos(ph1);
    }

    private static double di2ydph2(double g2, double b2, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return -(b2 + b21) * v2 * FastMath.sin(ph2) + (g2 + g21) * v2 * FastMath.cos(ph2);
    }

    @Override
    public double eval() {
        return i2y(g2, b2, v1(), ph1(), v2(), ph2(), g12, b12);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di2ydv1(ph1(), g12, b12);
        } else if (variable.equals(v2Var)) {
            return di2ydv2(g2, b2, ph2(), g12, b12);
        } else if (variable.equals(ph1Var)) {
            return di2ydph1(v1(), ph1(), g12, b12);
        } else if (variable.equals(ph2Var)) {
            return di2ydph2(g2, b2, v2(), ph2(), g12, b12);
        } else {
            throw new IllegalStateException("Unexpected variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "ac_iy_closed_2";
    }
}
