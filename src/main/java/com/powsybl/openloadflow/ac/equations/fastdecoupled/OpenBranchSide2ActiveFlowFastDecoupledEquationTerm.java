/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.OpenBranchSide2ActiveFlowEquationTerm.dp1dv1;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class OpenBranchSide2ActiveFlowFastDecoupledEquationTerm implements AbstractFastDecoupledEquationTerm {

    private final OpenBranchSide2ActiveFlowEquationTerm term;

    public OpenBranchSide2ActiveFlowFastDecoupledEquationTerm(OpenBranchSide2ActiveFlowEquationTerm openBranchSide2ActiveFlowEquationTerm) {
        this.term = openBranchSide2ActiveFlowEquationTerm;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(term.getV1Var())) {
            return dp1dv1(term.getY(), FastMath.cos(term.getKsi()), FastMath.sin(term.getKsi()), term.getG1(), term.getG2(), term.getB2(), 1, term.r1());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
