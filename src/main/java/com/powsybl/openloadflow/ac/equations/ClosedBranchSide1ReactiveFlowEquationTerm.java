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
public class ClosedBranchSide1ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
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
        return dq1dph1(y, ksi, v1, ph1, r1, a1, v2, ph2) * dph1
                + dq1dph2(y, ksi, v1, ph1, r1, a1, v2, ph2) * dph2
                + dq1dv1(y, ksi, b1, v1, ph1, r1, a1, v2, ph2) * dv1
                + dq1dv2(y, ksi, v1, ph1, r1, a1, ph2) * dv2;
    }

    protected static double theta(double ksi, double ph1, double a1, double ph2) {
        return ksi - a1 + A2 - ph1 + ph2;
    }

    public static double q1(double y, double ksi, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return r1 * v1 * (-b1 * r1 * v1 + y * r1 * v1 * FastMath.cos(ksi)
                - y * R2 * v2 * FastMath.cos(theta(ksi, ph1, a1, ph2)));
    }

    private static double dq1dv1(double y, double ksi, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return r1 * (-2 * b1 * r1 * v1 + 2 * y * r1 * v1 * FastMath.cos(ksi)
                - y * R2 * v2 * FastMath.cos(theta(ksi, ph1, a1, ph2)));
    }

    private static double dq1dv2(double y, double ksi, double v1, double ph1, double r1, double a1, double ph2) {
        return -y * r1 * R2 * v1 * FastMath.cos(theta(ksi, ph1, a1, ph2));
    }

    private static double dq1dph1(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return -y * r1 * R2 * v1 * v2 * FastMath.sin(theta(ksi, ph1, a1, ph2));
    }

    private static double dq1dph2(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return -dq1dph1(y, ksi, v1, ph1, r1, a1, v2, ph2);
    }

    private static double dq1da1(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return dq1dph1(y, ksi, v1, ph1, r1, a1, v2, ph2);
    }

    private static double dq1dr1(double y, double ksi, double b1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return v1 * (2 * r1 * v1 * (-b1 + y * FastMath.cos(ksi)) - y * R2 * v2 * FastMath.cos(theta(ksi, ph1, a1, ph2)));
    }

    @Override
    public double eval() {
        return q1(y, ksi, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dq1dv1(y, ksi, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(v2Var)) {
            return dq1dv2(y, ksi, v1(), ph1(), r1(), a1(), ph2());
        } else if (variable.equals(ph1Var)) {
            return dq1dph1(y, ksi, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(ph2Var)) {
            return dq1dph2(y, ksi, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(a1Var)) {
            return dq1da1(y, ksi, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(r1Var)) {
            return dq1dr1(y, ksi, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_1";
    }
}
