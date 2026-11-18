/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.equations.ac.fastdecoupled;

import com.powsybl.na.equations.Variable;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.equations.ac.ShuntCompensatorReactiveFlowEquationTerm;
import java.util.Objects;

import static com.powsybl.na.equations.ac.ShuntCompensatorReactiveFlowEquationTerm.dqdv;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ShuntCompensatorReactiveFlowFastDecoupledEquationTerm implements FastDecoupledEquationTerm {

    private final ShuntCompensatorReactiveFlowEquationTerm term;

    public ShuntCompensatorReactiveFlowFastDecoupledEquationTerm(ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm) {
        this.term = shuntCompensatorReactiveFlowEquationTerm;
    }

    private static double dqdbFastDecoupled(double v) {
        return -v;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(term.getVVar())) {
            return dqdv(1, term.b());
        } else if (variable.equals(term.getbVar())) {
            return dqdbFastDecoupled(1);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
