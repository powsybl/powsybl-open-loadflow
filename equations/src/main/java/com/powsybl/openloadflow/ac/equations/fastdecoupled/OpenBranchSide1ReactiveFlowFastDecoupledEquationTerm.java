/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.OpenBranchSide1ReactiveFlowEquationTerm.dq2dv2;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class OpenBranchSide1ReactiveFlowFastDecoupledEquationTerm implements FastDecoupledEquationTerm {

    private final OpenBranchSide1ReactiveFlowEquationTerm term;

    public OpenBranchSide1ReactiveFlowFastDecoupledEquationTerm(OpenBranchSide1ReactiveFlowEquationTerm openBranchSide1ReactiveFlowEquationTerm) {
        this.term = openBranchSide1ReactiveFlowEquationTerm;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(term.getV2Var())) {
            return dq2dv2(term.y(), FastMath.cos(term.ksi()), FastMath.sin(term.ksi()), term.g1(), term.b1(), term.b2(), 1);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
