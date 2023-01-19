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
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchSide1ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
    }

    public ClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1, DisymAcSequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double v1 = v1();
        double r1 = r1();
        double v2 = v2();
        double theta = theta1(ksi, ph1(), a1(), ph2());
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        return dq1dph1(y, v1, r1, v2, sinTheta) * dph1
                + dq1dph2(y, v1, r1, v2, sinTheta) * dph2
                + dq1dv1(y, FastMath.cos(ksi), b1, v1, r1, v2, cosTheta) * dv1
                + dq1dv2(y, v1, r1, cosTheta) * dv2;
    }

    public static double q1(double y, double cosKsi, double b1, double v1, double r1, double v2, double cosTheta) {
        return r1 * v1 * (-b1 * r1 * v1 + y * r1 * v1 * cosKsi
                - y * R2 * v2 * cosTheta);
    }

    private static double dq1dv1(double y, double cosKsi, double b1, double v1, double r1, double v2, double cosTheta) {
        return r1 * (-2 * b1 * r1 * v1 + 2 * y * r1 * v1 * cosKsi
                - y * R2 * v2 * cosTheta);
    }

    private static double dq1dv2(double y, double v1, double r1, double cosTheta) {
        return -y * r1 * R2 * v1 * cosTheta;
    }

    private static double dq1dph1(double y, double v1, double r1, double v2, double sinTheta) {
        return -y * r1 * R2 * v1 * v2 * sinTheta;
    }

    private static double dq1dph2(double y, double v1, double r1, double v2, double sinTheta) {
        return -dq1dph1(y, v1, r1, v2, sinTheta);
    }

    private static double dq1da1(double y, double v1, double r1, double v2, double sinTheta) {
        return dq1dph1(y, v1, r1, v2, sinTheta);
    }

    private static double dq1dr1(double y, double cosKsi, double b1, double v1, double r1, double v2, double cosTheta) {
        return v1 * (2 * r1 * v1 * (-b1 + y * cosKsi) - y * R2 * v2 * cosTheta);
    }

    @Override
    public double eval() {
        return q1(y, FastMath.cos(ksi), b1, v1(), r1(), v2(), FastMath.cos(theta1(ksi, ph1(), a1(), ph2())));
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta1(ksi, ph1(), a1(), ph2());
        if (variable.equals(v1Var)) {
            return dq1dv1(y, FastMath.cos(ksi), b1, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(v2Var)) {
            return dq1dv2(y, v1(), r1(), FastMath.cos(theta));
        } else if (variable.equals(ph1Var)) {
            return dq1dph1(y, v1(), r1(), v2(), FastMath.sin(theta));
        } else if (variable.equals(ph2Var)) {
            return dq1dph2(y, v1(), r1(), v2(), FastMath.sin(theta));
        } else if (variable.equals(a1Var)) {
            return dq1da1(y, v1(), r1(), v2(), FastMath.sin(theta));
        } else if (variable.equals(r1Var)) {
            return dq1dr1(y, FastMath.cos(ksi), b1, v1(), r1(), v2(), FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_1";
    }
}
