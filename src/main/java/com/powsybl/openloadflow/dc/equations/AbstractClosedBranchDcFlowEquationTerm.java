/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.AbstractBranchEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchDcFlowEquationTerm extends AbstractBranchEquationTerm<DcVariableType, DcEquationType> {

    protected final Variable<DcVariableType> ph1Var;

    protected final Variable<DcVariableType> ph2Var;

    protected final Variable<DcVariableType> a1Var;

    protected final List<Variable<DcVariableType>> variables;

    protected final double power;

    protected AbstractClosedBranchDcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet,
                                                     boolean deriveA1, boolean useTransformerRatio) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }
        ph1Var = variableSet.getVariable(bus1.getNum(), DcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), DcVariableType.BUS_PHI);
        a1Var = deriveA1 ? variableSet.getVariable(branch.getNum(), DcVariableType.BRANCH_ALPHA1) : null;
        power =  1 / piModel.getX() * (useTransformerRatio ? piModel.getR1() * R2 : 1);
        if (a1Var != null) {
            variables = List.of(ph1Var, ph2Var, a1Var);
        } else {
            variables = List.of(ph1Var, ph2Var);
        }
    }

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        Objects.requireNonNull(x);
        double ph1 = x.get(ph1Var.getRow(), column);
        double ph2 = x.get(ph2Var.getRow(), column);
        double a1 = a1Var != null ? x.get(a1Var.getRow(), column) : branch.getPiModel().getA1();
        return calculateSensi(ph1, ph2, a1);
    }

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected abstract double calculateSensi(double ph1, double ph2, double a1);

    protected double a1() {
        return a1Var != null ? sv.get(a1Var.getRow()) : branch.getPiModel().getA1();
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
