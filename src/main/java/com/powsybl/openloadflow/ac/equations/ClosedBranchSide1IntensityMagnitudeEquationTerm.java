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
public class ClosedBranchSide1IntensityMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double i1;

    private double di1dv1;

    private double di1dv2;

    private double di1dph1;

    private double di1dph2;

    private double di1da1;

    public ClosedBranchSide1IntensityMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                           boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateDer(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di1dph1 * dph1 + di1dph2 * dph2 + di1dv1 * dv1 + di1dv2 * dv2;
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
        double p1 = r1 * v1 * (g1 * r1 * v1 + y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
        double q1 = r1 * v1 * (-b1 * r1 * v1 + y * r1 * v1 * cosKsi - y * R2 * v2 * cosTheta);

        i1 = Math.hypot(p1, q1) / (Math.sqrt(3.) * v1 / 1000);

        double vv1 = r1 * v1;
        double vv2 = y * R2 * v2;
        double tv1v1 = g1 * g1 + b1 * b1 + y * y + 2 * g1 * y * sinKsi - 2 * y * b1 * cosKsi;
        double tv1v2 = -g1 * sinTheta - y * sinKsi * sinTheta + b1 * cosTheta - y * cosKsi * cosTheta;
        double tsqrt = r1 * r1 * (vv1 * vv1 * tv1v1 + vv2 * vv2 + 2 * vv1 * vv2 * tv1v2);
        double dtsqrtv1 = r1 * r1 * (2 * r1 * vv1 * tv1v1 + 2 * r1 * vv2 * tv1v2);
        double dtsqrtv2 = r1 * r1 * (2 * y * R2 * vv2 + 2 * y * R2 * vv1 * tv1v2);
        double dtv1v2ph1 = g1 * cosTheta + y * sinKsi * cosTheta + b1 * sinTheta - y * cosKsi * sinTheta;
        double dtsqrtph1 = r1 * r1 * (2 * vv1 * vv2 * dtv1v2ph1);
        double ti1 = 1000 / (2 * Math.sqrt(3 * tsqrt));

        di1dv1 = ti1 * dtsqrtv1;
        di1dv2 = ti1 * dtsqrtv2;
        di1dph1 = ti1 * dtsqrtph1;
        di1dph2 = -di1dph1;

        if (a1Var != null) {
            di1da1 = di1dph1;
        }
    }

    @Override
    public double eval() {
        return i1;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di1dv1;
        } else if (variable.equals(v2Var)) {
            return di1dv2;
        } else if (variable.equals(ph1Var)) {
            return di1dph1;
        } else if (variable.equals(ph2Var)) {
            return di1dph2;
        } else if (variable.equals(a1Var)) {
            return di1da1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_1";
    }
}
