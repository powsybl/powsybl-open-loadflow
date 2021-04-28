/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
public class ClosedBranchSide1CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double i1;

    private double di1dv1;

    private double di1dv2;

    private double di1dph1;

    private double di1dph2;

    private double di1da1;

    public ClosedBranchSide1CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di1dph1 * dph1 + di1dph2 * dph2 + di1dv1 * dv1 + di1dv2 * dv2;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getRow()];
        double v2 = x[v2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double r1 = r1Var != null ? x[r1Var.getRow()] : branch.getPiModel().getR1();
        double w1 = r1 * v1;
        double w2 = y * R2 * v2;
        double cosPh1 = FastMath.cos(ph1);
        double sinPh1 = FastMath.sin(ph1);
        double cosPh1Ksi = FastMath.cos(ph1 + ksi);
        double sinPh1Ksi = FastMath.sin(ph1 + ksi);
        double theta = ksi - (a1Var != null ? x[a1Var.getRow()] : branch.getPiModel().getA1())
                + A2 + ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);

        double interReI1 = g1 * cosPh1 - b1 * sinPh1 + y * sinPh1Ksi;
        double interImI1 = g1 * sinPh1 + b1 * cosPh1 - y * cosPh1Ksi;

        double reI1 = r1 * (w1 * interReI1 - w2 * sinTheta) * CURRENT_NORMALIZATION_FACTOR;
        double imI1 = r1 * (w1 * interImI1 + w2 * cosTheta) * CURRENT_NORMALIZATION_FACTOR;
        i1 = Math.hypot(reI1, imI1);

        double dreI1dv1 = r1 * r1 * interReI1 * CURRENT_NORMALIZATION_FACTOR;
        double dreI1dv2 = r1 * (-y * R2 * sinTheta) * CURRENT_NORMALIZATION_FACTOR;
        double dreI1dph1 = r1 * w1 * (-g1 * sinPh1 - b1 * cosPh1 + y * cosPh1Ksi) * CURRENT_NORMALIZATION_FACTOR;
        double dreI1dph2 = r1 * (-w2 * cosTheta) * CURRENT_NORMALIZATION_FACTOR;

        double dimI1dv1 = r1 * r1 * interImI1 * CURRENT_NORMALIZATION_FACTOR;
        double dimI1dv2 = r1 * (y * R2 * cosTheta) * CURRENT_NORMALIZATION_FACTOR;
        double dimI1dph1 = r1 * w1 * interReI1 * CURRENT_NORMALIZATION_FACTOR;
        double dimI1dph2 = r1 * (-w2 * sinTheta) * CURRENT_NORMALIZATION_FACTOR;

        di1dv1 = (reI1 * dreI1dv1 + imI1 * dimI1dv1) / i1;
        di1dv2 = (reI1 * dreI1dv2 + imI1 * dimI1dv2) / i1;
        di1dph1 = (reI1 * dreI1dph1 + imI1 * dimI1dph1) / i1;
        di1dph2 = (reI1 * dreI1dph2 + imI1 * dimI1dph2) / i1;

        if (a1Var != null) {
            di1da1 = -di1dph2;
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
