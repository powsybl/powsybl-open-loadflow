/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchSide2ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1, BranchAcDataVector branchAcDataVector) {
        this(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE, branchAcDataVector);
    }

    public ClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType, BranchAcDataVector branchAcDataVector) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType, branchAcDataVector);
    }

    @Override
    public void updateVectorSuppliers() {
        super.updateVectorSuppliers();
        // TODO: Change by a single method for N and 4 der
        branchAcDataVector.vecToP2[element.getNum()] = ClosedBranchSide2ActiveFlowEquationTerm::vec2p2;
        branchAcDataVector.vecToDP2dv1[element.getNum()] = ClosedBranchSide2ActiveFlowEquationTerm::vec2dp2dv1;
        branchAcDataVector.vecToDP2dv2[element.getNum()] = ClosedBranchSide2ActiveFlowEquationTerm::vec2dp2dv2;
        branchAcDataVector.vecToDP2dph1[element.getNum()] = ClosedBranchSide2ActiveFlowEquationTerm::vec2dp2dph1;
    }

    public static double calculateSensi(double y, double ksi, double g2,
                                        double v1, double ph1, double r1, double a1, double v2, double ph2,
                                        double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double theta = theta2(ksi, ph1, a1, ph2);
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        return dp2dph1(y, v1, r1, v2, cosTheta) * dph1
                + dp2dph2(y, v1, r1, v2, cosTheta) * dph2
                + dp2dv1(y, r1, v2, sinTheta) * dv1
                + dp2dv2(y, FastMath.sin(ksi), g2, v1, r1, v2, sinTheta) * dv2
                + dp2da1(y, v1, r1, v2, cosTheta) * da1
                + dp2dr1(y, v1, v2, sinTheta) * dr1;
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        return calculateSensi(y(), ksi(), g2(), v1(), ph1(), r1(), a1(), v2(), ph2(), dph1, dph2, dv1, dv2, da1, dr1);
    }

    public static double vec2p2(double v1, double v2, double sinKsi, double sinTheta2, double cosTheta2, double b1, double b2, double g1, double g2, double y,
                                double g12, double b12, double a1, double r1) {
        return p2(y, sinKsi, g2, v1, r1, v2, sinTheta2);
    }

    public static double p2(double y, double sinKsi, double g2, double v1, double r1, double v2, double sinTheta) {
        return R2 * v2 * (g2 * R2 * v2 - y * r1 * v1 * sinTheta + y * R2 * v2 * sinKsi);
    }

    public static double vec2dp2dv1(double v1, double v2, double sinKsi, double sinTheta2, double cosTheta2,
                                    double b1, double b2, double g1, double g2, double y,
                                    double g12, double b12, double a1, double r1) {
        return dp2dv1(y, r1, v2, sinTheta2);
    }

    public static double dp2dv1(double y, double r1, double v2, double sinTheta) {
        return -y * r1 * R2 * v2 * sinTheta;
    }

    public static double vec2dp2dv2(double v1, double v2, double sinKsi, double sinTheta2, double cosTheta2,
                                    double b1, double b2, double g1, double g2, double y,
                                    double g12, double b12, double a1, double r1) {
        return dp2dv2(y, sinKsi, g2, v1, r1, v2, sinTheta2);
    }

    public static double dp2dv2(double y, double sinKsi, double g2, double v1, double r1, double v2, double sinTheta) {
        return R2 * (2 * g2 * R2 * v2 - y * r1 * v1 * sinTheta + 2 * y * R2 * v2 * sinKsi);
    }

    public static double vec2dp2dph1(double v1, double v2, double sinKsi, double sinTheta2, double cosTheta2, double b1, double b2, double g1, double g2, double y,
                                    double g12, double b12, double a1, double r1) {
        return dp2dph1(y, v1, r1, v2, cosTheta2);
    }

    public static double dp2dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return -y * r1 * R2 * v1 * v2 * cosTheta;
    }

    public static double dp2dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return -dp2dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp2da1(double y, double v1, double r1, double v2, double cosTheta) {
        return dp2dph1(y, v1, r1, v2, cosTheta);
    }

    public static double dp2dr1(double y, double v1, double v2, double sinTheta) {
        return -y * R2 * v1 * v2 * sinTheta;
    }

    @Override
    public double eval() {
        if (!p2Valid()) {
            // To avoid code duplication, use the vectorized fonction with standard arguments. Pass Nan for variables that are not used.
            setP2(vec2p2(v1(), v2(), FastMath.sin(ksi()), FastMath.sin(theta2(ksi(), ph1(), a1(), ph2())), Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, g2(), y(), Double.NaN, Double.NaN, a1(), r1()));
        }
        return p2();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            if (!dp2dp1Valid()) {
                setDp2dv1(vec2dp2dv1(Double.NaN, v2(), Double.NaN, FastMath.sin(theta2(ksi(), ph1(), a1(), ph2())), Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, y(), Double.NaN, Double.NaN, a1(), r1()));
            }
            return dp2dv1();
        } else if (variable.equals(v2Var)) {
            if (!dp2dv2Valid()) {
                setDp2dv2(vec2dp2dv2(v1(), v2(), FastMath.sin(ksi()), FastMath.sin(theta2(ksi(), ph1(), a1(), ph2())), Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, g2(), y(), Double.NaN, Double.NaN, a1(), r1()));
            }
            return dp2dv2();
        }

        if (variable.equals(ph1Var)) {
            if (!dp2dph1Valid()) {
                setDp2dph1(vec2dp2dph1(v1(), v2(), Double.NaN, Double.NaN, FastMath.cos(theta2(ksi(), ph1(), a1(), ph2())), Double.NaN, Double.NaN, Double.NaN, Double.NaN, y(), Double.NaN, Double.NaN, Double.NaN, r1()));
            }
            return dp2dph1();
        }
        // Do not compute theta before as it can be useless if the result is cached
        double theta = theta2(ksi(), ph1(), a1(), ph2());
        if (variable.equals(ph2Var)) {
            return dp2dph2(y(), v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(a1Var)) {
            return dp2da1(y(), v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(r1Var)) {
            return dp2dr1(y(), v1(), v2(), FastMath.sin(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    // eval variables

    protected boolean p2Valid() {
        return branchAcDataVector.p2Valid[branchNum];
    }

    protected double p2() {
        return branchAcDataVector.p2[branchNum];
    }

    protected void setP2(double value) {
        branchAcDataVector.p2[branchNum] = value;
        branchAcDataVector.p2Valid[branchNum] = true;
    }

    protected boolean dp2dp1Valid() {
        return branchAcDataVector.dp2dv1Valid[branchNum];
    }

    protected double dp2dv1() {
        return branchAcDataVector.dp2dv1[branchNum];
    }

    protected void setDp2dv1(double value) {
        branchAcDataVector.dp2dv1[branchNum] = value;
        branchAcDataVector.dp2dv1Valid[branchNum] = true;
    }

    protected boolean dp2dv2Valid() {
        return branchAcDataVector.dp2dv2Valid[branchNum];
    }

    protected double dp2dv2() {
        return branchAcDataVector.dp2dv2[branchNum];
    }

    protected void setDp2dv2(double value) {
        branchAcDataVector.dp2dv2[branchNum] = value;
        branchAcDataVector.dp2dv2Valid[branchNum] = true;
    }

    protected boolean dp2dph1Valid() {
        return branchAcDataVector.dp2dph1Valid[branchNum];
    }

    protected double dp2dph1() {
        return branchAcDataVector.dp2dph1[branchNum];
    }

    protected void setDp2dph1(double value) {
        branchAcDataVector.dp2dph1[branchNum] = value;
        branchAcDataVector.dp2dph1Valid[branchNum] = true;
    }

    @Override
    protected String getName() {
        return "ac_p_closed_2";
    }
}
