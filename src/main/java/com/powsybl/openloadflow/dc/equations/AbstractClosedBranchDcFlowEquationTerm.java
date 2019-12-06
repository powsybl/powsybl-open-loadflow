/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.openloadflow.equations.AbstractEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchDcFlowEquationTerm extends AbstractEquationTerm {

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
