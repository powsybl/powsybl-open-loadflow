/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator;
import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm.*;
import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm extends AbstractClosedBranchAcFlowFastDecoupledEquationTerm<ClosedBranchSide2ActiveFlowEquationTerm, ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator> {

    public ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm(ClosedBranchSide2ActiveFlowEquationTerm closedBranchSide2ActiveFlowEquationTerm) {
        super(closedBranchSide2ActiveFlowEquationTerm);
    }

    public ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm(ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator closedBranchSide2ActiveFlowEvaluator, int branchNum) {
        super(closedBranchSide2ActiveFlowEvaluator, branchNum);
    }

    protected static double theta2FastDecoupled(double ksi, double a1) {
        return ksi + a1 - A2;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta2FastDecoupled(ksi, a1);
        if (variable.equals(phi1Var)) {
            return dp2dph1(y, 1, r1, 1, FastMath.cos(theta));
        } else if (variable.equals(phi2Var)) {
            return dp2dph2(y, 1, r1, 1, FastMath.cos(theta));
        } else if (variable.equals(a1Var)) {
            return dp2da1(y, 1, r1, 1, FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
