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
import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm.*;
import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm implements AbstractFastDecoupledEquationTerm {

    private final ClosedBranchSide2ActiveFlowEquationTerm term;

    public ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm(ClosedBranchSide2ActiveFlowEquationTerm closedBranchSide2ActiveFlowEquationTerm) {
        this.term = closedBranchSide2ActiveFlowEquationTerm;
    }

    protected static double theta2FastDecoupled(double ksi, double a1) {
        return ksi + a1 - A2;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta2FastDecoupled(term.getKsi(), term.a1());
        if (variable.equals(term.getPhi1Var())) {
            return dp2dph1(term.getY(), 1, term.r1(), term.v2(), FastMath.cos(theta));
        } else if (variable.equals(term.getPhi2Var())) {
            return dp2dph2(term.getY(), 1, term.r1(), term.v2(), FastMath.cos(theta));
        } else if (variable.equals(term.getA1Var())) {
            return dp2da1(term.getY(), 1, term.r1(), term.v2(), FastMath.cos(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
