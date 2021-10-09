/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.NetworkBuffer;
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
public class ClosedBranchSide1CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double i1;

    private double di1dv1;

    private double di1dv2;

    private double di1dph1;

    private double di1dph2;

    private double di1da1;

    public ClosedBranchSide1CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di1dph1 * dph1 + di1dph2 * dph2 + di1dv1 * dv1 + di1dv2 * dv2;
    }

    @Override
    public void update(double[] x, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        double v1 = x[acBuf.v1Row[num]];
        double v2 = x[acBuf.v2Row[num]];
        double ph1 = x[acBuf.ph1Row[num]];
        double ph2 = x[acBuf.ph2Row[num]];
        double r1 = acBuf.r1Row[num] != -1 ? x[acBuf.r1Row[num]] : buf.r1[num];
        double a1 = acBuf.a1Row[num] != -1 ? x[acBuf.a1Row[num]] : buf.a1[num];
        updateCurrent(v1, v2, ph1, ph2, r1, a1, acBuf);
    }

    private void updateCurrent(double v1, double v2, double ph1, double ph2, double r1, double a1, AcNetworkBuffer buf) {
        double w1 = r1 * v1;
        double w2 = buf.y[num] * R2 * v2;
        double cosPh1 = FastMath.cos(ph1);
        double sinPh1 = FastMath.sin(ph1);
        double cosPh1Ksi = FastMath.cos(ph1 + buf.ksi[num]);
        double sinPh1Ksi = FastMath.sin(ph1 + buf.ksi[num]);
        double theta = buf.ksi[num] - a1 + A2 + ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);

        double interReI1 = buf.g1[num] * cosPh1 - buf.b1[num] * sinPh1 + buf.y[num] * sinPh1Ksi;
        double interImI1 = buf.g1[num] * sinPh1 + buf.b1[num] * cosPh1 - buf.y[num] * cosPh1Ksi;

        double reI1 = r1 * (w1 * interReI1 - w2 * sinTheta);
        double imI1 = r1 * (w1 * interImI1 + w2 * cosTheta);
        i1 = FastMath.hypot(reI1, imI1);

        double dreI1dv1 = r1 * r1 * interReI1;
        double dreI1dv2 = r1 * (-buf.y[num] * R2 * sinTheta);
        double dreI1dph1 = r1 * w1 * (-buf.g1[num] * sinPh1 - buf.b1[num] * cosPh1 + buf.y[num] * cosPh1Ksi);
        double dreI1dph2 = r1 * (-w2 * cosTheta);

        double dimI1dv1 = r1 * r1 * interImI1;
        double dimI1dv2 = r1 * (buf.y[num] * R2 * cosTheta);
        double dimI1dph1 = r1 * w1 * interReI1;
        double dimI1dph2 = r1 * (-w2 * sinTheta);

        di1dv1 = (reI1 * dreI1dv1 + imI1 * dimI1dv1) / i1;
        di1dv2 = (reI1 * dreI1dv2 + imI1 * dimI1dv2) / i1;
        di1dph1 = (reI1 * dreI1dph1 + imI1 * dimI1dph1) / i1;
        di1dph2 = (reI1 * dreI1dph2 + imI1 * dimI1dph2) / i1;

        if (buf.a1Row[num] != -1) {
            di1da1 = -di1dph2;
        }
    }

    @Override
    public double eval() {
        return i1;
    }

    @Override
    public double der(Variable<AcVariableType> variable, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        switch (variable.getType()) {
            case BUS_V:
                if (variable.getRow() == acBuf.v1Row[num]) {
                    return di1dv1;
                } else if (variable.getRow() == acBuf.v2Row[num]) {
                    return di1dv2;
                }
                break;
            case BUS_PHI:
                if (variable.getRow() == acBuf.ph1Row[num]) {
                    return di1dph1;
                } else if (variable.getRow() == acBuf.ph2Row[num]) {
                    return di1dph2;
                }
                break;
            case BRANCH_ALPHA1:
                if (variable.getRow() == acBuf.a1Row[num]) {
                    return di1da1;
                }
                break;
        }
        throw new IllegalStateException("Unknown variable: " + variable);
    }

    @Override
    protected String getName() {
        return "ac_i_closed_1";
    }
}
