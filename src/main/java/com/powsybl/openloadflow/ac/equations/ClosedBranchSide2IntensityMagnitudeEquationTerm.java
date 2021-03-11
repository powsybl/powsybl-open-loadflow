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
        Objects.requireNonNull(x);
        double v2 = x[v2Var.getRow()];
        double v1 = x[v1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double r1 = r1Var != null && r1Var.isActive() ? x[r1Var.getRow()] : branch.getPiModel().getR1();
        double w2 = R2 * v2;
        double w1 = y * r1 * v1;
        double cosPh2 = FastMath.cos(ph2);
        double sinPh2 = FastMath.sin(ph2);
        double cosPh2Ksi = FastMath.cos(ph2 + ksi);
        double sinPh2Ksi = FastMath.sin(ph2 + ksi);
        double theta = ksi - (a1Var != null && a1Var.isActive() ? x[a1Var.getRow()] : branch.getPiModel().getA1())
            + A2 + ph1;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);

        double reI2 = R2 * (w2 * (g2 * cosPh2 - b2 * sinPh2 + y * sinPh2Ksi) - w1 * sinTheta) * CURRENT_NORMALIZATION_FACTOR;
        double imI2 = R2 * (w2 * (g2 * sinPh2 + b2 * cosPh2 - y * cosPh2Ksi) + w1 * cosTheta) * CURRENT_NORMALIZATION_FACTOR;
        i2 = Math.hypot(reI2, imI2);

        double dreI2dv2 = R2 * R2 * (g2 * cosPh2 - b2 * sinPh2 + y * sinPh2Ksi) * CURRENT_NORMALIZATION_FACTOR;
        double dreI2dv1 = R2 * (-y * r1 * sinTheta) * CURRENT_NORMALIZATION_FACTOR;
        double dreI2dph2 = R2 * w2 * (-g2 * sinPh2 - b2 * cosPh2 + y * cosPh2Ksi) * CURRENT_NORMALIZATION_FACTOR;
        double dreI2dph1 = R2 * (-w1 * cosTheta) * CURRENT_NORMALIZATION_FACTOR;

        double dimI2dv2 = R2 * R2 * (g2 * sinPh2 + b2 * cosPh2 - y * cosPh2Ksi) * CURRENT_NORMALIZATION_FACTOR;
        double dimI2dv1 = R2 * (y * r1 * cosTheta) * CURRENT_NORMALIZATION_FACTOR;
        double dimI2dph2 = R2 * w2 * (g2 * cosPh2 - b2 * sinPh2 + y * sinPh2Ksi) * CURRENT_NORMALIZATION_FACTOR;
        double dimI2dph1 = R2 * (-w1 * sinTheta) * CURRENT_NORMALIZATION_FACTOR;

        di2dv2 = (reI2 * dreI2dv2 + imI2 * dimI2dv2) / i2;
        di2dv1 = (reI2 * dreI2dv1 + imI2 * dimI2dv1) / i2;
        di2dph2 = (reI2 * dreI2dph2 + imI2 * dimI2dph2) / i2;
        di2dph1 = (reI2 * dreI2dph1 + imI2 * dimI2dph1) / i2;

        if (a1Var != null) {
            di2da1 = -di2dph1;
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
