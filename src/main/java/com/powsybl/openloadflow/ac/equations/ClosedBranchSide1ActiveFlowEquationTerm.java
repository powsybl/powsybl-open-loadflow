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
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double p1;

    private double dp1dv1;

    private double dp1dv2;

    private double dp1dph1;

    private double dp1dph2;

    private double dp1da1;

    private double dp1dr1;

    public ClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dp1dph1 * dph1 + dp1dph2 * dph2 + dp1dv1 * dv1 + dp1dv2 * dv2;
    }

    @Override
    public void update(double[] x, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        double v1 = x[acBuf.v1Row(num)];
        double v2 = x[acBuf.v2Row(num)];
        double ph1 = x[acBuf.ph1Row(num)];
        double ph2 = x[acBuf.ph2Row(num)];
        double r1 = acBuf.r1Row(num) != -1 ? x[acBuf.r1Row(num)] : buf.r1(num);
        double a1 = acBuf.a1Row(num) != -1 ? x[acBuf.a1Row(num)] : buf.a1(num);
        double theta = buf.ksi(num) - a1 + A2 - ph1 + ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);
        p1 = r1 * v1 * (buf.g1(num) * r1 * v1 + buf.y(num) * r1 * v1 * buf.sinKsi(num) - buf.y(num) * R2 * v2 * sinTheta);
        dp1dv1 = r1 * (2 * buf.g1(num) * r1 * v1 + 2 * buf.y(num) * r1 * v1 * buf.sinKsi(num) - buf.y(num) * R2 * v2 * sinTheta);
        dp1dv2 = -buf.y(num) * r1 * R2 * v1 * sinTheta;
        dp1dph1 = buf.y(num) * r1 * R2 * v1 * v2 * cosTheta;
        dp1dph2 = -dp1dph1;
        if (acBuf.a1Row(num) != -1) {
            dp1da1 = dp1dph1;
        }
        if (acBuf.r1Row(num) != -1) {
            dp1dr1 = v1 * (2 * r1 * v1 * (buf.g1(num) + buf.y(num) * buf.sinKsi(num)) - buf.y(num) * R2 * v2 * sinTheta);
        }
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable<AcVariableType> variable, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        switch (variable.getType()) {
            case BUS_V:
                if (variable.getRow() == acBuf.v1Row(num)) {
                    return dp1dv1;
                } else if (variable.getRow() == acBuf.v2Row(num)) {
                    return dp1dv2;
                }
                break;
            case BUS_PHI:
                if (variable.getRow() == acBuf.ph1Row(num)) {
                    return dp1dph1;
                } else if (variable.getRow() == acBuf.ph2Row(num)) {
                    return dp1dph2;
                }
                break;
            case BRANCH_ALPHA1:
                if (variable.getRow() == acBuf.a1Row(num)) {
                    return dp1da1;
                }
                break;
            case BRANCH_RHO1:
                if (variable.getRow() == acBuf.r1Row(num)) {
                    return dp1dr1;
                }
                break;
        }
        throw new IllegalStateException("Unknown variable: " + variable);
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
