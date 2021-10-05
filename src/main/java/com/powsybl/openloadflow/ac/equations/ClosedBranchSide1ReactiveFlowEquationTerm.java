/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.BranchVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ClosedBranchSide1ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double q1;

    private double dq1dv1;

    private double dq1dv2;

    private double dq1dph1;

    private double dq1dph2;

    private double dq1da1;

    private double dq1dr1;

    public ClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dq1dph1 * dph1 + dq1dph2 * dph2 + dq1dv1 * dv1 + dq1dv2 * dv2;
    }

    @Override
    public void update(double[] x, BranchVector vec) {
        double v1 = x[v1Var.getRow()];
        double v2 = x[v2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double theta = vec.ksi[num] - (a1Var != null ? x[a1Var.getRow()] : branch.getPiModel().getA1())
                + A2 - ph1 + ph2;
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        double r1 = r1Var != null ? x[r1Var.getRow()] : branch.getPiModel().getR1();
        q1 = r1 * v1 * (-vec.b1[num] * r1 * v1 + vec.y[num] * r1 * v1 * vec.cosKsi[num] - vec.y[num] * R2 * v2 * cosTheta);
        dq1dv1 = r1 * (-2 * vec.b1[num] * r1 * v1 + 2 * vec.y[num] * r1 * v1 * vec.cosKsi[num] - vec.y[num] * R2 * v2 * cosTheta);
        dq1dv2 = -vec.y[num] * r1 * R2 * v1 * cosTheta;
        dq1dph1 = -vec.y[num] * r1 * R2 * v1 * v2 * sinTheta;
        dq1dph2 = -dq1dph1;
        if (a1Var != null) {
            dq1da1 = dq1dph1;
        }
        if (r1Var != null) {
            dq1dr1 = v1 * (2 * r1 * v1 * (-vec.b1[num] + vec.y[num] * vec.cosKsi[num]) - vec.y[num] * R2 * v2 * cosTheta);
        }
    }

    @Override
    public double eval() {
        return q1;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        if (variable.equals(v1Var)) {
            return dq1dv1;
        } else if (variable.equals(v2Var)) {
            return dq1dv2;
        } else if (variable.equals(ph1Var)) {
            return dq1dph1;
        } else if (variable.equals(ph2Var)) {
            return dq1dph2;
        } else if (variable.equals(a1Var)) {
            return dq1da1;
        } else if (variable.equals(r1Var)) {
            return dq1dr1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_1";
    }
}
