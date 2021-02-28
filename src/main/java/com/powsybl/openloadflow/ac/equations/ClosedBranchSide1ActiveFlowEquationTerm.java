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
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double p1;

    private double dp1dv1;

    private double dp1dv2;

    private double dp1dph1;

    private double dp1dph2;

    private double dp1da1;

    private double dp1dr1;

    public ClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateDer(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dp1dph1 * dph1 + dp1dph2 * dph2 + dp1dv1 * dv1 + dp1dv2 * dv2;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getRow()];
        double v2 = x[v2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double theta = ksi - (a1Var != null && a1Var.isActive() ? x[a1Var.getRow()] : branch.getPiModel().getA1())
                + A2 - ph1 + ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);
        double r1 = r1Var != null && r1Var.isActive() ? x[r1Var.getRow()] : branch.getPiModel().getR1();
        p1 = r1 * v1 * (g1 * r1 * v1 + y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
        dp1dv1 = r1 * (2 * g1 * r1 * v1 + 2 * y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
        dp1dv2 = -y * r1 * R2 * v1 * sinTheta;
        dp1dph1 = y * r1 * R2 * v1 * v2 * cosTheta;
        dp1dph2 = -dp1dph1;
        if (a1Var != null) {
            dp1da1 = dp1dph1;
        }
        if (r1Var != null) {
            dp1dr1 = v1 * (2 * r1 * v1 * (g1 + y * sinKsi) - y * R2 * v2 * sinTheta);
        }
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1;
        } else if (variable.equals(v2Var)) {
            return dp1dv2;
        } else if (variable.equals(ph1Var)) {
            return dp1dph1;
        } else if (variable.equals(ph2Var)) {
            return dp1dph2;
        } else if (variable.equals(a1Var)) {
            return dp1da1;
        } else if (variable.equals(r1Var)) {
            return dp1dr1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
