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

    private final ClosedBranchSide1ReactiveFlowEquationTerm term;

    public ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm(ClosedBranchSide1ReactiveFlowEquationTerm closedBranchSide1ReactiveFlowEquationTerm) {
        this.term = closedBranchSide1ReactiveFlowEquationTerm;
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
        double theta = theta1FastDecoupled(term.getKsi(), term.a1());
        if (variable.equals(term.getV1Var())) {
            return dq1dv1FastDecoupled(term.getY(), FastMath.cos(term.getKsi()), term.getB1(), term.v1(), term.r1(), FastMath.cos(theta));
        } else if (variable.equals(term.getV2Var())) {
            return dq1dv2(term.getY(), term.v1(), term.r1(), FastMath.cos(theta));
        } else if (variable.equals(term.getR1Var())) {
            return dq1dr1FastDecoupled(term.getY(), FastMath.cos(term.getKsi()), term.getB1(), term.v1(), term.r1(), FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
