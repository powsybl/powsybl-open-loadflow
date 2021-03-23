/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.math.matrix.DenseMatrix;
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
public abstract class AbstractClosedBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final Variable v1Var;

    protected final Variable v2Var;

    protected final Variable ph1Var;

    protected final Variable ph2Var;

    protected Variable a1Var;

    protected Variable r1Var;

    protected final List<Variable> variables;

    protected AbstractClosedBranchAcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        v1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_V);
        v2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_V);
        ph1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_PHI);
        ImmutableList.Builder<Variable> variablesBuilder = ImmutableList.<Variable>builder()
                .add(v1Var, v2Var, ph1Var, ph2Var);
        if (deriveA1) {
            a1Var = variableSet.getVariable(branch.getNum(), VariableType.BRANCH_ALPHA1);
            variablesBuilder.add(a1Var);
        }
        if (deriveR1) {
            r1Var = variableSet.getVariable(branch.getNum(), VariableType.BRANCH_RHO1);
            variablesBuilder.add(r1Var);
        }
        variables = variablesBuilder.build();
    }

    protected abstract double calculateSensi(double ph1, double ph2, double v1, double v2, double a1, double r1);

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        Objects.requireNonNull(x);
        double ph1 = x.get(ph1Var.getRow(), column);
        double ph2 = x.get(ph2Var.getRow(), column);
        double v1 = x.get(v1Var.getRow(), column);
        double v2 = x.get(v2Var.getRow(), column);
        double a1 = getA1(x, column);
        double r1 = getR1(x, column);
        return calculateSensi(ph1, ph2, v1, v2, a1, r1);
    }

    protected double getA1(DenseMatrix x, int column) {
        return a1Var != null && a1Var.isActive() ? x.get(a1Var.getRow(), column) : branch.getPiModel().getA1();
    }

    protected double getR1(DenseMatrix x, int column) {
        return r1Var != null && r1Var.isActive() ? x.get(r1Var.getRow(), column) : branch.getPiModel().getR1();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }
}
