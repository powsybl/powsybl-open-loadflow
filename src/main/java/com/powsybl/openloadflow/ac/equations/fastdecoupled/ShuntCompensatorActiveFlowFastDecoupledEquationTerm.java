/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import java.util.Objects;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class ShuntCompensatorActiveFlowFastDecoupledEquationTerm implements AbstractFastDecoupledEquationTerm {

    private final ShuntCompensatorActiveFlowEquationTerm term;

    public ShuntCompensatorActiveFlowFastDecoupledEquationTerm(ShuntCompensatorActiveFlowEquationTerm shuntCompensatorActiveFlowEquationTerm) {
        this.term = shuntCompensatorActiveFlowEquationTerm;
    }

    public static double dpdv(double v, double g) {
        return 2 * g * v;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(term.getVVar())) {
            return dpdv(1, term.g());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
