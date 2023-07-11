/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ActiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                   VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    public ClosedBranchSide1ActiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                   VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1,
                                                   Fortescue.SequenceType sequenceType) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, sequenceType);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double y = branchVector.y[num];
        double ksi = branchVector.ksi[num];
        double g1 = branchVector.g1[num];
        double v1 = v1();
        double r1 = r1();
        double v2 = v2();
        double theta = theta1(ksi, ph1(), a1(), ph2());
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        double sinKsi = FastMath.sin(ksi);
        return dp1dph1(y, v1, r1, v2, cosTheta) * dph1
                + dp1dph2(y, v1, r1, v2, cosTheta) * dph2
                + dp1dv1(y, sinKsi, g1, v1, r1, v2, sinTheta) * dv1
                + dp1dv2(y, v1, r1, sinTheta) * dv2
                + dp1da1(y, v1, r1, v2, cosTheta) * da1
                + dp1dr1(y, sinKsi, g1, v1, r1, v2, sinTheta) * dr1;
    }

    public static double p1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * v1 * (g1 * r1 * v1 + y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
    }

    public static double dp1dv1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * (2 * g1 * r1 * v1 + 2 * y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
    }

    public static double dp1dv2(double y, double v1, double r1, double sinTheta) {
        return -y * r1 * R2 * v1 * sinTheta;
    }

    public static double dp1dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return y * r1 * R2 * v1 * v2 * cosTheta;
    }

    public static double dp1dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return -dp1dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp1da1(double y, double v1, double r1, double v2, double cosTheta) {
        return dp1dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp1dr1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return v1 * (2 * r1 * v1 * (g1 + y * sinKsi) - y * R2 * v2 * sinTheta);
    }

    public static double eval(AcNetworkVector networkVector, int branchNum) {
        return networkVector.getBranchVector().p1[branchNum];
    }

    @Override
    public double eval() {
        return branchVector.p1[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return branchVector.dp1dv1[num];
        } else if (variable.equals(v2Var)) {
            return branchVector.dp1dv2[num];
        } else if (variable.equals(ph1Var)) {
            return branchVector.dp1dph1[num];
        } else if (variable.equals(ph2Var)) {
            return branchVector.dp1dph2[num];
        } else if (variable.equals(a1Var)) {
            return branchVector.dp1da1[num];
        } else if (variable.equals(r1Var)) {
            return branchVector.dp1dr1[num];
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
