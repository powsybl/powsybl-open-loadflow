/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.equations.ac.asym;

import com.powsybl.na.commons.Fortescue;
import com.powsybl.na.equations.Variable;
import com.powsybl.na.equations.VariableSet;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.LfBus;

import java.util.List;

/**
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public abstract class AbstractShuntFortescueCurrentEquationTerm extends AbstractShuntFortescueEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    protected AbstractShuntFortescueCurrentEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        variables = List.of(vVar, phVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
