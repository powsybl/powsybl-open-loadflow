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
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class ClosedBranchSide2IntensityMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double i2;

    private double di2dv1;

    private double di2dv2;

    private double di2dph1;

    private double di2dph2;

    private double di2da1;

    public ClosedBranchSide2IntensityMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                           boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateDer(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di2dph1 * dph1 + di2dph2 * dph2 + di2dv1 * dv1 + di2dv2 * dv2;
    }

    @Override
    public void update(double[] x) {
        // todo
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
        double p2 = R2 * v2 * (g2 * R2 * v2 - y * r1 * v1 * sinTheta + y * R2 * v2 * sinKsi);
        double q2 = R2 * v2 * (-b2 * R2 * v2 - y * r1 * v1 * cosTheta + y * R2 * v2 * cosKsi);

        i2 = Math.hypot(p2, q2) / (Math.sqrt(3.) * v2 / 1000);

        double vv2 = R2 * v2;
        double vv1 = y * r1 * v1;
        double tv2v2 = g2 * g2 + b2 * b2 + y * y + 2 * g2 * y * sinKsi - 2 * y * b2 * cosKsi;
        double tv2v1 = -g2 * sinTheta - y * sinKsi * sinTheta + b2 * cosTheta - y * cosKsi * cosTheta;
        double tsqrt = R2 * R2 * (vv2 * vv2 * tv2v2 + vv1 * vv1 + 2 * vv2 * vv1 * tv2v1);
        double dtsqrtv2 = R2 * R2 * (2 * R2 * vv2 * tv2v2 + 2 * R2 * vv1 * tv2v1);
        double dtsqrtv1 = R2 * R2 * (2 * y * r1 * vv1 + 2 * y * r1 * vv2 * tv2v1);
        double dtv2v1ph2 = g2 * cosTheta + y * sinKsi * cosTheta + b2 * sinTheta - y * cosKsi * sinTheta;
        double dtsqrtph2 = R2 * R2 * (2 * vv2 * vv1 * dtv2v1ph2);
        double ti2 = 1000 / (2 * Math.sqrt(3 * tsqrt));

        di2dv2 = ti2 * dtsqrtv2;
        di2dv1 = ti2 * dtsqrtv1;
        di2dph2 = ti2 * dtsqrtph2;
        di2dph1 = -di2dph2;

        if (a1Var != null) {
            di2da1 = di2dph1;
        }
    }

    @Override
    public double eval() {
        return i2;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di2dv1;
        } else if (variable.equals(v2Var)) {
            return di2dv2;
        } else if (variable.equals(ph1Var)) {
            return di2dph1;
        } else if (variable.equals(ph2Var)) {
            return di2dph2;
        } else if (variable.equals(a1Var)) {
            return di2da1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_2";
    }
}
