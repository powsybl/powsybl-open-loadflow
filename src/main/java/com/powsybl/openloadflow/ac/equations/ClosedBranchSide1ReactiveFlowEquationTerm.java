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
public class ClosedBranchSide1ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dq1dph1() * dph1 + dq1dph2() * dph2 + dq1dv1() * dv1 + dq1dv2() * dv2;
    }

    protected double theta() {
        return ksi - (a1Var != null ? x[a1Var.getRow()] : branch.getPiModel().getA1()) + A2 - ph1() + ph2();
    }

    private double q1() {
        return r1() * v1() * (-b1 * r1() * v1() + y * r1() * v1() * FastMath.cos(ksi)
                - y * R2 * v2() * FastMath.cos(theta()));
    }

    private double dq1dv1() {
        return r1() * (-2 * b1 * r1() * v1() + 2 * y * r1() * v1() * FastMath.cos(ksi)
                - y * R2 * v2() * FastMath.cos(theta()));
    }

    private double dq1dv2() {
        return -y * r1() * R2 * v1() * FastMath.cos(theta());
    }

    private double dq1dph1() {
        return -y * r1() * R2 * v1() * v2() * FastMath.sin(theta());
    }

    private double dq1dph2() {
        return -dq1dph1();
    }

    private double dq1da1() {
        return dq1dph1();
    }

    private double dq1dr1() {
        return v1() * (2 * r1() * v1() * (-b1 + y * FastMath.cos(ksi)) - y * R2 * v2() * FastMath.cos(theta()));
    }

    @Override
    public double eval() {
        return q1();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dq1dv1();
        } else if (variable.equals(v2Var)) {
            return dq1dv2();
        } else if (variable.equals(ph1Var)) {
            return dq1dph1();
        } else if (variable.equals(ph2Var)) {
            return dq1dph2();
        } else if (variable.equals(a1Var)) {
            return dq1da1();
        } else if (variable.equals(r1Var)) {
            return dq1dr1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_1";
    }
}
