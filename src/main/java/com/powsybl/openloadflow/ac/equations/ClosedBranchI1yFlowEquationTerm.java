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
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchI1yFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchI1yFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                           VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1,
                                           Fortescue.SequenceType sequenceType) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, sequenceType);
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
    public static double i1y(double g1, double b1, double v1, double ph1, double v2, double ph2, double g12, double b12) {
        return (b1 + b12) * v1 * FastMath.cos(ph1) + (g1 + g12) * v1 * FastMath.sin(ph1) - b12 * v2 * FastMath.cos(ph2) - g12 * v2 * FastMath.sin(ph2);
    }

    private static double di1ydv1(double g1, double b1, double ph1, double g12, double b12) {
        return (b1 + b12) * FastMath.cos(ph1) + (g1 + g12) * FastMath.sin(ph1);
    }

    private static double di1ydv2(double ph2, double g12, double b12) {
        return -b12 * FastMath.cos(ph2) - g12 * FastMath.sin(ph2);
    }

    private static double di1ydph1(double g1, double b1, double v1, double ph1, double g12, double b12) {
        return -(b1 + b12) * v1 * FastMath.sin(ph1) + (g1 + g12) * v1 * FastMath.cos(ph1);
    }

    private static double di1ydph2(double v2, double ph2, double g12, double b12) {
        return b12 * v2 * FastMath.sin(ph2) - g12 * v2 * FastMath.cos(ph2);
    }

    @Override
    public double eval() {
        return i1y(branchVector.g1[num], branchVector.b1[num], v1(), ph1(), v2(), ph2(), branchVector.g12[num], branchVector.b12[num]);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di1ydv1(branchVector.g1[num], branchVector.b1[num], ph1(), branchVector.g12[num], branchVector.b12[num]);
        } else if (variable.equals(v2Var)) {
            return di1ydv2(ph2(), branchVector.g12[num], branchVector.b12[num]);
        } else if (variable.equals(ph1Var)) {
            return di1ydph1(branchVector.g1[num], branchVector.b1[num], v1(), ph1(), branchVector.g12[num], branchVector.b12[num]);
        } else if (variable.equals(ph2Var)) {
            return di1ydph2(v2(), ph2(), branchVector.g12[num], branchVector.b12[num]);
        } else {
            throw new IllegalStateException("Unexpected variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "ac_iy_closed_1";
    }
}
