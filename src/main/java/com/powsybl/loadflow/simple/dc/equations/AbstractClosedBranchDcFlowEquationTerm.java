/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.dc.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.loadflow.simple.equations.*;
import com.powsybl.loadflow.simple.network.LfBranch;
import com.powsybl.loadflow.simple.network.LfBus;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchDcFlowEquationTerm implements EquationTerm {

    protected final LfBranch branch;

    protected final Variable ph1Var;

    protected final Variable ph2Var;

    protected final List<Variable> variables;

    protected final double power;

    protected AbstractClosedBranchDcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet) {
        this.branch = Objects.requireNonNull(branch);
        ph1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_PHI);
        variables = ImmutableList.of(ph1Var, ph2Var);
        power =  1 / this.branch.x() * this.branch.r1() * this.branch.r2();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return true;
    }
}
