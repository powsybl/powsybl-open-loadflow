/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 */
public class MultiplyByScalarFastDecoupledEquationTerm implements FastDecoupledEquationTerm {

    private final double scalar;
    private final FastDecoupledEquationTerm fastDecoupledEquationTerm;

    public MultiplyByScalarFastDecoupledEquationTerm(double scalar, FastDecoupledEquationTerm fastDecoupledEquationTerm) {
        this.scalar = scalar;
        this.fastDecoupledEquationTerm = fastDecoupledEquationTerm;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        return scalar * fastDecoupledEquationTerm.derFastDecoupled(variable);
    }
}
