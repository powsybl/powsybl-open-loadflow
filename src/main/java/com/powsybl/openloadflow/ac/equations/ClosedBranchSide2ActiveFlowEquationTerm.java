/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchSide2ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide2ActiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                   VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    public ClosedBranchSide2ActiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                   VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1,
                                                   Fortescue.SequenceType sequenceType) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, sequenceType);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double y = branchVector.y[num];
        double ksi = branchVector.ksi[num];
        double g2 = branchVector.g2[num];
        double v1 = v1();
        double r1 = r1();
        double v2 = v2();
        double theta = theta2(ksi, ph1(), a1(), ph2());
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        return dp2dph1(y, v1, r1, v2, cosTheta) * dph1
                + dp2dph2(y, v1, r1, v2, cosTheta) * dph2
                + dp2dv1(y, r1, v2, sinTheta) * dv1
                + dp2dv2(y, FastMath.sin(ksi), g2, v1, r1, v2, sinTheta) * dv2
                + dp2da1(y, v1, r1, v2, cosTheta) * da1
                + dp2dr1(y, v1, v2, sinTheta) * dr1;
    }

    public static double p2(double y, double sinKsi, double g2, double v1, double r1, double v2, double sinTheta) {
        return R2 * v2 * (g2 * R2 * v2 - y * r1 * v1 * sinTheta + y * R2 * v2 * sinKsi);
    }

    public static double dp2dv1(double y, double r1, double v2, double sinTheta) {
        return -y * r1 * R2 * v2 * sinTheta;
    }

    public static double dp2dv2(double y, double sinKsi, double g2, double v1, double r1, double v2, double sinTheta) {
        return R2 * (2 * g2 * R2 * v2 - y * r1 * v1 * sinTheta + 2 * y * R2 * v2 * sinKsi);
    }

    public static double dp2dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return -y * r1 * R2 * v1 * v2 * cosTheta;
    }

    public static double dp2dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return -dp2dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp2da1(double y, double v1, double r1, double v2, double cosTheta) {
        return dp2dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp2dr1(double y, double v1, double v2, double sinTheta) {
        return -y * R2 * v1 * v2 * sinTheta;
    }

    @Override
    public double eval() {
        return branchVector.p2[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return branchVector.dp2dv1[num];
        } else if (variable.equals(v2Var)) {
            return branchVector.dp2dv2[num];
        } else if (variable.equals(ph1Var)) {
            return branchVector.dp2dph1[num];
        } else if (variable.equals(ph2Var)) {
            return branchVector.dp2dph2[num];
        } else if (variable.equals(a1Var)) {
            return branchVector.dp2da1[num];
        } else if (variable.equals(r1Var)) {
            return branchVector.dp2dr1[num];
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_2";
    }
}
