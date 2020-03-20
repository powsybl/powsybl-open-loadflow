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
import com.powsybl.openloadflow.network.PiModel;

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

    protected final double a1;

    protected final double a2;

    protected AbstractClosedBranchDcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet) {
        this.branch = Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }
        ph1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_PHI);
        variables = ImmutableList.of(ph1Var, ph2Var);
        power =  1 / piModel.getX() * piModel.getR1() * piModel.getR2();
        a1 = piModel.getA1();
        a2 = piModel.getA2();
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
