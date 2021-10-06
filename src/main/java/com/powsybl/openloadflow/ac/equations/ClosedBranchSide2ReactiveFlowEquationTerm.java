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
public class ClosedBranchSide2ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double q2;

    private double dq2dv1;

    private double dq2dv2;

    private double dq2dph1;

    private double dq2dph2;

    private double dq2da1;

    private double dq2dr1;

    public ClosedBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dq2dph1 * dph1 + dq2dph2 * dph2 + dq2dv1 * dv1 + dq2dv2 * dv2;
    }

    @Override
    public void update(double[] x, BranchVector<AcVariableType, AcEquationType> vec) {
        double v1 = x[v1Var.getRow()];
        double v2 = x[v2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double theta = vec.ksi[num] + (a1Var != null ? x[a1Var.getRow()] : vec.a1[num])
                - A2 + ph1 - ph2;
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        double r1 = r1Var != null ? x[r1Var.getRow()] : vec.r1[num];
        q2 = R2 * v2 * (-vec.b2[num] * R2 * v2 - vec.y[num] * r1 * v1 * cosTheta + vec.y[num] * R2 * v2 * vec.cosKsi[num]);
        dq2dv1 = -vec.y[num] * r1 * R2 * v2 * cosTheta;
        dq2dv2 = R2 * (-2 * vec.b2[num] * R2 * v2 - vec.y[num] * r1 * v1 * cosTheta + 2 * vec.y[num] * R2 * v2 * vec.cosKsi[num]);
        dq2dph1 = vec.y[num] * r1 * R2 * v1 * v2 * sinTheta;
        dq2dph2 = -dq2dph1;
        if (a1Var != null) {
            dq2da1 = dq2dph1;
        }
        if (r1Var != null) {
            dq2dr1 = -vec.y[num] * R2 * v1 * v2 * cosTheta;
        }
    }

    @Override
    public double eval() {
        return q2;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        if (variable.equals(v1Var)) {
            return dq2dv1;
        } else if (variable.equals(v2Var)) {
            return dq2dv2;
        } else if (variable.equals(ph1Var)) {
            return dq2dph1;
        } else if (variable.equals(ph2Var)) {
            return dq2dph2;
        } else if (variable.equals(a1Var)) {
            return dq2da1;
        } else if (variable.equals(r1Var)) {
            return dq2dr1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_2";
    }
}
