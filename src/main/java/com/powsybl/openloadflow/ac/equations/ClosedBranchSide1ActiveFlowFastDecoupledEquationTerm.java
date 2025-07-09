/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm.*;
import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm {

    private ClosedBranchSide1ActiveFlowEquationTerm closedBranchSide1ActiveFlowEquationTerm;

    public ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm(ClosedBranchSide1ActiveFlowEquationTerm closedBranchSide1ActiveFlowEquationTerm) {
        this.closedBranchSide1ActiveFlowEquationTerm = closedBranchSide1ActiveFlowEquationTerm;
    }

    protected static double theta1FastDecoupled(double ksi, double a1) {
        return ksi - a1 + A2;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta1FastDecoupled(closedBranchSide1ActiveFlowEquationTerm.ksi, closedBranchSide1ActiveFlowEquationTerm.a1());
        if (variable.equals(closedBranchSide1ActiveFlowEquationTerm.ph1Var)) {
            return dp1dph1(closedBranchSide1ActiveFlowEquationTerm.y, closedBranchSide1ActiveFlowEquationTerm.v1(), closedBranchSide1ActiveFlowEquationTerm.r1(), 1, FastMath.cos(theta));
        } else if (variable.equals(closedBranchSide1ActiveFlowEquationTerm.ph2Var)) {
            return dp1dph2(closedBranchSide1ActiveFlowEquationTerm.y, closedBranchSide1ActiveFlowEquationTerm.v1(), closedBranchSide1ActiveFlowEquationTerm.r1(), 1, FastMath.cos(theta));
        } else if (variable.equals(closedBranchSide1ActiveFlowEquationTerm.a1Var)) {
            return dp1da1(closedBranchSide1ActiveFlowEquationTerm.y, closedBranchSide1ActiveFlowEquationTerm.v1(), closedBranchSide1ActiveFlowEquationTerm.r1(), 1, FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
