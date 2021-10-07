/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchDcFlowEquationTerm extends AbstractNamedEquationTerm<DcVariableType, DcEquationType> {

    protected final LfBranch branch;

    protected final Variable<DcVariableType> ph1Var;

    protected final Variable<DcVariableType> ph2Var;

    protected Variable<DcVariableType> a1Var;

    protected final List<Variable<DcVariableType>> variables;

    protected final double power;

    protected AbstractClosedBranchDcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet,
                                                     boolean deriveA1, boolean useTransformerRatio) {
        this.branch = Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }
        ph1Var = variableSet.create(bus1.getNum(), DcVariableType.BUS_PHI);
        ph2Var = variableSet.create(bus2.getNum(), DcVariableType.BUS_PHI);
        ImmutableList.Builder<Variable<DcVariableType>> variablesBuilder = ImmutableList.<Variable<DcVariableType>>builder().add(ph1Var, ph2Var);
        power =  1 / piModel.getX() * (useTransformerRatio ? piModel.getR1() * R2 : 1);
        if (deriveA1) {
            a1Var = variableSet.create(branch.getNum(), DcVariableType.BRANCH_ALPHA1);
            variablesBuilder.add(a1Var);
        }
        variables = variablesBuilder.build();
    }

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        Objects.requireNonNull(x);
        double ph1 = x.get(ph1Var.getRow(), column);
        double ph2 = x.get(ph2Var.getRow(), column);
        double a1 = getA1(x, column);
        return calculateSensi(ph1, ph2, a1);
    }

    private double getA1(DenseMatrix x, int column) {
        return a1Var != null ? x.get(a1Var.getRow(), column) : branch.getPiModel().getA1();
    }

    protected abstract double calculateSensi(double ph1, double ph2, double a1);

    protected double getA1(double[] stateVector) {
        return a1Var != null ? stateVector[a1Var.getRow()] : branch.getPiModel().getA1();
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BRANCH;
    }

    @Override
    public int getElementNum() {
        return branch.getNum();
    }

    @Override
    public List<Variable<DcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return true;
    }
}
