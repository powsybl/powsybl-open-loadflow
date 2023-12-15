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
import com.powsybl.openloadflow.util.Fortescue;
import gnu.trove.list.array.TIntArrayList;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    public ClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType);
    }

    public static double calculateSensi(double g1, double y, double ksi,
                                        double v1, double ph1, double a1, double r1, double v2, double ph2,
                                        double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double theta = theta1(ksi, ph1, a1, ph2);
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        double sinKsi = FastMath.sin(ksi);
        return dp1dph1(y, v1, r1, v2, cosTheta) * dph1
                + dp1dph2(y, v1, r1, v2, cosTheta) * dph2
                + dp1dv1(y, sinKsi, g1, v1, r1, v2, sinTheta) * dv1
                + dp1dv2(y, v1, r1, sinTheta) * dv2
                + dp1da1(y, v1, r1, v2, cosTheta) * da1
                + dp1dr1(y, sinKsi, g1, v1, r1, v2, sinTheta) * dr1;
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        return calculateSensi(g1, y, ksi, v1(), ph1(), a1(), r1(), v2(), ph2(), dph1, dph2, dv1, dv2, da1, dr1);
    }

    public static double p1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * v1 * (g1 * r1 * v1 + y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
    }

    public static double dp1dv1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return r1 * (2 * g1 * r1 * v1 + 2 * y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
    }

    public static double dp1dv2(double y, double v1, double r1, double sinTheta) {
        return -y * r1 * R2 * v1 * sinTheta;
    }

    public static double dp1dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return y * r1 * R2 * v1 * v2 * cosTheta;
    }

    public static double dp1dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return -dp1dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp1da1(double y, double v1, double r1, double v2, double cosTheta) {
        return dp1dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp1dr1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return v1 * (2 * r1 * v1 * (g1 + y * sinKsi) - y * R2 * v2 * sinTheta);
    }

    public static void eval(AcBranchVector branchVector, TIntArrayList branchNums, double[] values) {
        for (int i = 0; i < branchNums.size(); i++) {
            int branchNum = branchNums.getQuick(i);
            values[branchNum] = branchVector.p1[branchNum];
        }
    }

    @Override
    public double eval() {
        return p1(y, FastMath.sin(ksi), g1, v1(), r1(), v2(), FastMath.sin(theta1(ksi, ph1(), a1(), ph2())));
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta1(ksi, ph1(), a1(), ph2());
        if (variable.equals(v1Var)) {
            return dp1dv1(y, FastMath.sin(ksi), g1, v1(), r1(), v2(), FastMath.sin(theta));
        } else if (variable.equals(v2Var)) {
            return dp1dv2(y, v1(), r1(), FastMath.sin(theta));
        } else if (variable.equals(ph1Var)) {
            return dp1dph1(y, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(ph2Var)) {
            return dp1dph2(y, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(a1Var)) {
            return dp1da1(y, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(r1Var)) {
            return dp1dr1(y, FastMath.sin(ksi), g1, v1(), r1(), v2(), FastMath.sin(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
