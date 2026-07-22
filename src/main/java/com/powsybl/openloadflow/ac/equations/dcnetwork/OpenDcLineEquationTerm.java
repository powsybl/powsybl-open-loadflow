/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.dcnetwork;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfDcLine;

import java.util.List;
import java.util.Objects;

/**
 * Equation term returning 0 for the current in an open DC line.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class OpenDcLineEquationTerm extends AbstractElementEquationTerm<LfDcLine, AcVariableType, AcEquationType> {

    public OpenDcLineEquationTerm(LfDcLine dcLine) {
        super(dcLine);
    }

    @Override
    public double eval() {
        return 0.0;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return 0.0;
    }

    @Override
    public String getName() {
        return "dc_open";
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return List.of();
    }
}
