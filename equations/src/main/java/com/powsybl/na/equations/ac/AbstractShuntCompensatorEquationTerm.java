/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.equations.ac;

import com.powsybl.na.equations.AbstractElementEquationTerm;
import com.powsybl.na.equations.Variable;
import com.powsybl.na.equations.VariableSet;
import com.powsybl.na.network.LfBus;
import com.powsybl.na.network.LfShunt;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractShuntCompensatorEquationTerm extends AbstractElementEquationTerm<LfShunt, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> vVar;

    protected AbstractShuntCompensatorEquationTerm(LfShunt shunt, LfBus bus, VariableSet<AcVariableType> variableSet) {
        super(shunt);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
    }

    public double v() {
        return sv.get(vVar.getRow());
    }

    public Variable<AcVariableType> getVVar() {
        return vVar;
    }
}
