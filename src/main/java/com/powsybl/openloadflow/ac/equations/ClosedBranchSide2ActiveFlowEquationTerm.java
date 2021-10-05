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
public class ClosedBranchSide2ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double p2;

    private double dp2dv1;

    private double dp2dv2;

    private double dp2dph1;

    private double dp2dph2;

    private double dp2da1;

    private double dp2dr1;

    public ClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dp2dph1 * dph1 + dp2dph2 * dph2 + dp2dv1 * dv1 + dp2dv2 * dv2;
    }

    @Override
    public void update(double[] x, BranchVector vec) {
        double v1 = x[v1Var.getRow()];
        double v2 = x[v2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double theta = vec.ksi[num] + (a1Var != null ? x[a1Var.getRow()] : branch.getPiModel().getA1())
                - A2 + ph1 - ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);
        double r1 = r1Var != null ? x[r1Var.getRow()] : branch.getPiModel().getR1();
        p2 = R2 * v2 * (vec.g2[num] * R2 * v2 - vec.y[num] * r1 * v1 * sinTheta + vec.y[num] * R2 * v2 * vec.sinKsi[num]);
        dp2dv1 = -vec.y[num] * r1 * R2 * v2 * sinTheta;
        dp2dv2 = R2 * (2 * vec.g2[num] * R2 * v2 - vec.y[num] * r1 * v1 * sinTheta + 2 * vec.y[num] * R2 * v2 * vec.sinKsi[num]);
        dp2dph1 = -vec.y[num] * r1 * R2 * v1 * v2 * cosTheta;
        dp2dph2 = -dp2dph1;
        if (a1Var != null) {
            dp2da1 = dp2dph1;
        }
        if (r1Var != null) {
            dp2dr1 = -vec.y[num] * R2 * v1 * v2 * sinTheta;
        }
    }

    @Override
    public double eval() {
        return p2;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        if (variable.equals(v1Var)) {
            return dp2dv1;
        } else if (variable.equals(v2Var)) {
            return dp2dv2;
        } else if (variable.equals(ph1Var)) {
            return dp2dph1;
        } else if (variable.equals(ph2Var)) {
            return dp2dph2;
        } else if (variable.equals(a1Var)) {
            return dp2da1;
        } else if (variable.equals(r1Var)) {
            return dp2dr1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_2";
    }
}
