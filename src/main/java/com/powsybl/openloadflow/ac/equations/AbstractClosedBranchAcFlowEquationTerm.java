/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.BranchVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final List<Variable<AcVariableType>> variables;

    protected AbstractClosedBranchAcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        Variable<AcVariableType> v1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_V);
        Variable<AcVariableType> v2Var = variableSet.create(bus2.getNum(), AcVariableType.BUS_V);
        Variable<AcVariableType> ph1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_PHI);
        Variable<AcVariableType> ph2Var = variableSet.create(bus2.getNum(), AcVariableType.BUS_PHI);
        ImmutableList.Builder<Variable<AcVariableType>> variablesBuilder = ImmutableList.<Variable<AcVariableType>>builder()
                .add(v1Var, v2Var, ph1Var, ph2Var);
        if (deriveA1) {
            Variable<AcVariableType> a1Var = variableSet.create(branch.getNum(), AcVariableType.BRANCH_ALPHA1);
            variablesBuilder.add(a1Var);
        }
        if (deriveR1) {
            Variable<AcVariableType> r1Var = variableSet.create(branch.getNum(), AcVariableType.BRANCH_RHO1);
            variablesBuilder.add(r1Var);
        }
        variables = variablesBuilder.build();
    }

    protected abstract double calculateSensi(double ph1, double ph2, double v1, double v2, double a1, double r1);

    @Override
    public double calculateSensi(DenseMatrix x, int column, BranchVector<AcVariableType, AcEquationType> vec) {
        AcBranchVector acVec = (AcBranchVector) vec;
        double ph1 = x.get(acVec.ph1Row[num], column);
        double ph2 = x.get(acVec.ph2Row[num], column);
        double v1 = x.get(acVec.v1Row[num], column);
        double v2 = x.get(acVec.v2Row[num], column);
        double a1 = getA1(x, column, acVec);
        double r1 = getR1(x, column, acVec);
        return calculateSensi(ph1, ph2, v1, v2, a1, r1);
    }

    protected double getA1(DenseMatrix x, int column, AcBranchVector vec) {
        return vec.a1Row[num] != -1 ? x.get(vec.a1Row[num], column) : branch.getPiModel().getA1();
    }

    protected double getR1(DenseMatrix x, int column, AcBranchVector vec) {
        return vec.r1Row[num] != -1 ? x.get(vec.r1Row[num], column) : branch.getPiModel().getR1();
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
