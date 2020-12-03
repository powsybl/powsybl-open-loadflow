/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchDcFlowEquationTerm extends AbstractNamedEquationTerm {

    protected final LfBranch branch;

    protected final Variable ph1Var;

    protected final Variable ph2Var;

    protected Variable a1Var;

    protected final List<Variable> variables;

    protected final double power;

    protected AbstractClosedBranchDcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                     boolean deriveA1, boolean useTransformerRatio) {
        this.branch = Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }
        ph1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_PHI);
        ImmutableList.Builder<Variable> variablesBuilder = ImmutableList.<Variable>builder().add(ph1Var, ph2Var);
        power =  1 / piModel.getX() * (useTransformerRatio ? piModel.getR1() * R2 : 1);
        if (deriveA1) {
            a1Var = variableSet.getVariable(branch.getNum(), VariableType.BRANCH_ALPHA1);
            variablesBuilder.add(a1Var);
        }
        variables = variablesBuilder.build();
    }

    @Override
    public SubjectType getSubjectType() {
        return SubjectType.BRANCH;
    }

    @Override
    public int getSubjectNum() {
        return branch.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return true;
    }

    public double getPower() {
        return power;
    }
}
