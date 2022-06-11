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

import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchSide2ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double v1 = v1();
        double ph1 = ph1();
        double r1 = r1();
        double a1 = a1();
        double v2 = v2();
        double ph2 = ph2();
        return dq2dph1(y, ksi, v1, ph1, r1, a1, v2, ph2) * dph1
                + dq2dph2(y, ksi, v1, ph1, r1, a1, v2, ph2) * dph2
                + dq2dv1(y, ksi, ph1, r1, a1, v2, ph2) * dv1
                + dq2dv2(y, ksi, FastMath.cos(ksi), b2, v1, ph1, r1, a1, v2, ph2) * dv2;
    }

    private static double theta(double ksi, double ph1, double a1, double ph2) {
        return ksi + a1 - A2 + ph1 - ph2;
    }

    public static double q2(double y, double ksi, double cosKsi, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return R2 * v2 * (-b2 * R2 * v2 - y * r1 * v1 * FastMath.cos(theta(ksi, ph1, a1, ph2)) + y * R2 * v2 * cosKsi);
    }

    private static double dq2dv1(double y, double ksi, double ph1, double r1, double a1, double v2, double ph2) {
        return -y * r1 * R2 * v2 * FastMath.cos(theta(ksi, ph1, a1, ph2));
    }

    private static double dq2dv2(double y, double ksi, double cosKsi, double b2, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return R2 * (-2 * b2 * R2 * v2 - y * r1 * v1 * FastMath.cos(theta(ksi, ph1, a1, ph2)) + 2 * y * R2 * v2 * cosKsi);
    }

    private static double dq2dph1(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return y * r1 * R2 * v1 * v2 * FastMath.sin(theta(ksi, ph1, a1, ph2));
    }

    private static double dq2dph2(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return -dq2dph1(y, ksi, v1, ph1, r1, a1, v2, ph2);
    }

    private static double dq2da1(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return dq2dph1(y, ksi, v1, ph1, r1, a1, v2, ph2);
    }

    private static double dq2dr1(double y, double ksi, double v1, double ph1, double a1, double v2, double ph2) {
        return -y * R2 * v1 * v2 * FastMath.cos(theta(ksi, ph1, a1, ph2));
    }

    @Override
    public double eval() {
        return q2(y, ksi, FastMath.cos(ksi), b2, v1(), ph1(), r1(), a1(), v2(), ph2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dq2dv1(y, ksi, ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(v2Var)) {
            return dq2dv2(y, ksi, FastMath.cos(ksi), b2, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(ph1Var)) {
            return dq2dph1(y, ksi, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(ph2Var)) {
            return dq2dph2(y, ksi, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(a1Var)) {
            return dq2da1(y, ksi, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(r1Var)) {
            return dq2dr1(y, ksi, v1(), ph1(), a1(), v2(), ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_2";
    }
}
