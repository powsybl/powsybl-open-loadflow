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
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class ClosedBranchSide2CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double i2;

    private double di2dv1;

    private double di2dv2;

    private double di2dph1;

    private double di2dph2;

    private double di2da1;

    public ClosedBranchSide2CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di2dph1 * dph1 + di2dph2 * dph2 + di2dv1 * dv1 + di2dv2 * dv2;
    }

    @Override
    public void update(double[] x, BranchVector vec) {
        double v2 = x[v2Var.getRow()];
        double v1 = x[v1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double r1 = r1Var != null ? x[r1Var.getRow()] : vec.r1[num];
        double a1 = a1Var != null ? x[a1Var.getRow()] : vec.a1[num];
        updateCurrent(v1, v2, ph1, ph2, r1, a1, vec);
    }

    private void updateCurrent(double v1, double v2, double ph1, double ph2, double r1, double a1, BranchVector vec) {
        double w2 = R2 * v2;
        double w1 = vec.y[num] * r1 * v1;
        double cosPh2 = FastMath.cos(ph2);
        double sinPh2 = FastMath.sin(ph2);
        double cosPh2Ksi = FastMath.cos(ph2 + vec.ksi[num]);
        double sinPh2Ksi = FastMath.sin(ph2 + vec.ksi[num]);
        double theta = vec.ksi[num] + a1 - A2 + ph1;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);

        double interReI2 = vec.g2[num] * cosPh2 - vec.b2[num] * sinPh2 + vec.y[num] * sinPh2Ksi;
        double interImI2 = vec.g2[num] * sinPh2 + vec.b2[num] * cosPh2 - vec.y[num] * cosPh2Ksi;

        double reI2 = R2 * (w2 * interReI2 - w1 * sinTheta);
        double imI2 = R2 * (w2 * interImI2 + w1 * cosTheta);
        i2 = FastMath.hypot(reI2, imI2);

        double dreI2dv2 = R2 * R2 * interReI2;
        double dreI2dv1 = R2 * (-vec.y[num] * r1 * sinTheta);
        double dreI2dph2 = R2 * w2 * (-vec.g2[num] * sinPh2 - vec.b2[num] * cosPh2 + vec.y[num] * cosPh2Ksi);
        double dreI2dph1 = R2 * (-w1 * cosTheta);

        double dimI2dv2 = R2 * R2 * interImI2;
        double dimI2dv1 = R2 * (vec.y[num] * r1 * cosTheta);
        double dimI2dph2 = R2 * w2 * interReI2;
        double dimI2dph1 = R2 * (-w1 * sinTheta);

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
    public double der(Variable<AcVariableType> variable) {
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
