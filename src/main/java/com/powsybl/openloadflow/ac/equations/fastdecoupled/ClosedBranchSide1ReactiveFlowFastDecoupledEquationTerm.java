/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator;
import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;
import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm.*;
import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm implements AbstractFastDecoupledEquationTerm {

    private final double y;
    private final double ksi;
    private final double b1;
    private final double a1;
    private final double r1;
    private final Variable<AcVariableType> v1Var;
    private final Variable<AcVariableType> v2Var;
    private final Variable<AcVariableType> r1Var;

    public ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm(ClosedBranchSide1ReactiveFlowEquationTerm closedBranchSide1ReactiveFlowEquationTerm) {
        y = closedBranchSide1ReactiveFlowEquationTerm.getY();
        ksi = closedBranchSide1ReactiveFlowEquationTerm.getKsi();
        b1 = closedBranchSide1ReactiveFlowEquationTerm.getB1();
        a1 = closedBranchSide1ReactiveFlowEquationTerm.a1();
        r1 = closedBranchSide1ReactiveFlowEquationTerm.r1();
        v1Var = closedBranchSide1ReactiveFlowEquationTerm.getV1Var();
        v2Var = closedBranchSide1ReactiveFlowEquationTerm.getV2Var();
        r1Var = closedBranchSide1ReactiveFlowEquationTerm.getR1Var();
    }

    public ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm(ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator closedBranchSide1ReactiveFlowEvaluator, int branchNum) {
        // If vectorized, we use EquationTermArrayEvaluator to get the term data
        y = closedBranchSide1ReactiveFlowEvaluator.getY(branchNum);
        ksi = closedBranchSide1ReactiveFlowEvaluator.getKsi(branchNum);
        b1 = closedBranchSide1ReactiveFlowEvaluator.getB1(branchNum);
        a1 = closedBranchSide1ReactiveFlowEvaluator.getA1(branchNum);
        r1 = closedBranchSide1ReactiveFlowEvaluator.getR1(branchNum);
        v1Var = closedBranchSide1ReactiveFlowEvaluator.getV1Var(branchNum);
        v2Var = closedBranchSide1ReactiveFlowEvaluator.getV2Var(branchNum);
        r1Var = closedBranchSide1ReactiveFlowEvaluator.getR1Var(branchNum);
    }

    public static double dq1dv1FastDecoupled(double y, double cosKsi, double b1, double v1, double r1, double cosTheta) {
        return r1 * v1 * (-2 * b1 * r1 + 2 * y * r1 * cosKsi
                - y * R2 * cosTheta);
    }

    public static double dq1dr1FastDecoupled(double y, double cosKsi, double b1, double v1, double r1, double cosTheta) {
        return v1 * (2 * r1 * (-b1 + y * cosKsi) - y * R2 * cosTheta);
    }

    protected static double theta1FastDecoupled(double ksi, double a1) {
        return ksi - a1 + A2;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta1FastDecoupled(ksi, a1);
        if (variable.equals(v1Var)) {
            return dq1dv1FastDecoupled(y, FastMath.cos(ksi), b1, 1, r1, FastMath.cos(theta));
        } else if (variable.equals(v2Var)) {
            return dq1dv2(y, 1, r1, FastMath.cos(theta));
        } else if (variable.equals(r1Var)) {
            return dq1dr1FastDecoupled(y, FastMath.cos(ksi), b1, 1, r1, FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
