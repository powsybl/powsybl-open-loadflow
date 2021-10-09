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
    public void update(double[] x, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        double v1 = x[acBuf.v1Row[num]];
        double v2 = x[acBuf.v2Row[num]];
        double ph1 = x[acBuf.ph1Row[num]];
        double ph2 = x[acBuf.ph2Row[num]];
        double r1 = acBuf.r1Row[num] != -1 ? x[acBuf.r1Row[num]] : buf.r1[num];
        double a1 = acBuf.a1Row[num] != -1 ? x[acBuf.a1Row[num]] : buf.a1[num];
        double theta = buf.ksi[num] + a1 - A2 + ph1 - ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);
        p2 = R2 * v2 * (buf.g2[num] * R2 * v2 - buf.y[num] * r1 * v1 * sinTheta + buf.y[num] * R2 * v2 * buf.sinKsi[num]);
        dp2dv1 = -buf.y[num] * r1 * R2 * v2 * sinTheta;
        dp2dv2 = R2 * (2 * buf.g2[num] * R2 * v2 - buf.y[num] * r1 * v1 * sinTheta + 2 * buf.y[num] * R2 * v2 * buf.sinKsi[num]);
        dp2dph1 = -buf.y[num] * r1 * R2 * v1 * v2 * cosTheta;
        dp2dph2 = -dp2dph1;
        if (acBuf.a1Row[num] != -1) {
            dp2da1 = dp2dph1;
        }
        if (acBuf.r1Row[num] != -1) {
            dp2dr1 = -buf.y[num] * R2 * v1 * v2 * sinTheta;
        }
    }

    @Override
    public double eval() {
        return p2;
    }

    @Override
    public double der(Variable<AcVariableType> variable, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        switch (variable.getType()) {
            case BUS_V:
                if (variable.getRow() == acBuf.v1Row[num]) {
                    return dp2dv1;
                } else if (variable.getRow() == acBuf.v2Row[num]) {
                    return dp2dv2;
                }
                break;
            case BUS_PHI:
                if (variable.getRow() == acBuf.ph1Row[num]) {
                    return dp2dph1;
                } else if (variable.getRow() == acBuf.ph2Row[num]) {
                    return dp2dph2;
                }
                break;
            case BRANCH_ALPHA1:
                if (variable.getRow() == acBuf.a1Row[num]) {
                    return dp2da1;
                }
                break;
            case BRANCH_RHO1:
                if (variable.getRow() == acBuf.r1Row[num]) {
                    return dp2dr1;
                }
                break;
        }
        throw new IllegalStateException("Unknown variable: " + variable);
    }

    @Override
    protected String getName() {
        return "ac_p_closed_2";
    }
}
