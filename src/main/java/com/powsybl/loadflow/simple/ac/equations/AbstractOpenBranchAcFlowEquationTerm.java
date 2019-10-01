/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.equations;

import com.powsybl.loadflow.simple.equations.VariableSet;
import com.powsybl.loadflow.simple.equations.Variable;
import com.powsybl.loadflow.simple.equations.VariableType;
import com.powsybl.loadflow.simple.network.LfBranch;
import com.powsybl.loadflow.simple.network.LfBus;

import java.util.Collections;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractOpenBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final List<Variable> variables;

    protected double shunt;

    protected AbstractOpenBranchAcFlowEquationTerm(LfBranch branch, VariableType variableType,
                                                   LfBus bus, VariableSet variableSet) {
        super(branch);
        variables = Collections.singletonList(variableSet.getVariable(bus.getNum(), variableType));
        shunt = (g1 + y * sinKsi) * (g1 + y * sinKsi) + (-b1 + y * cosKsi) * (-b1 + y * cosKsi);
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }
}
