/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator;
import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm.dq2dr1;
import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm.dq2dv1;
import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm extends AbstractClosedBranchAcFlowFastDecoupledEquationTerm<ClosedBranchSide2ReactiveFlowEquationTerm, ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator> {

    public ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm(ClosedBranchSide2ReactiveFlowEquationTerm closedBranchSide2ReactiveFlowEquationTerm) {
        super(closedBranchSide2ReactiveFlowEquationTerm);
    }

    public ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm(ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator closedBranchSide2ReactiveFlowEvaluator, int branchNum) {
        super(closedBranchSide2ReactiveFlowEvaluator, branchNum);
    }

    public static double dq2dv2FastDecoupled(double y, double cosKsi, double b2, double v2, double r1, double cosTheta) {
        return R2 * v2 * (-2 * b2 * R2 - y * r1 * cosTheta + 2 * y * R2 * cosKsi);
    }

    protected static double theta2FastDecoupled(double ksi, double a1) {
        return ksi + a1 - A2;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta2FastDecoupled(ksi, a1);
        if (variable.equals(v1Var)) {
            return dq2dv1(y, r1, 1, FastMath.cos(theta));
        } else if (variable.equals(v2Var)) {
            return dq2dv2FastDecoupled(y, FastMath.cos(ksi), b2, 1, r1, FastMath.cos(theta));
        } else if (variable.equals(r1Var)) {
            return dq2dr1(y, 1, 1, FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
