/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class ClosedBranchSide2DcFlowEquationTerm extends AbstractClosedBranchDcFlowEquationTerm {

    private double p2;

    private double rhs;

    private ClosedBranchSide2DcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                boolean deriveA1) {
        super(branch, bus1, bus2, variableSet, deriveA1);
    }

    public static ClosedBranchSide2DcFlowEquationTerm create(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                             boolean deriveA1) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        return new ClosedBranchSide2DcFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1);
    }

    private double calculate(double ph1, double ph2, double a1) {
        double deltaPhase =  ph2 - ph1 + A2 - a1;
        return power * deltaPhase;
    }

    public double calculate(DenseMatrix x, int column) {
        Objects.requireNonNull(x);
        double ph1 = x.get(ph1Var.getRow(), column);
        double ph2 = x.get(ph2Var.getRow(), column);
        double a1 = a1Var != null ? x.get(a1Var.getRow(), column) : branch.getPiModel().getA1();
        return calculate(ph1, ph2, a1);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double a1 = a1Var != null ? x[a1Var.getRow()] : branch.getPiModel().getA1();
        p2 = calculate(ph1, ph2, a1);
        rhs = power * (A2 - a1);
    }

    @Override
    public double eval() {
        return p2;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(ph1Var)) {
            return -power;
        } else if (variable.equals(ph2Var)) {
            return power;
        } else if (variable.equals(a1Var)) {
            return -power;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public double rhs() {
        return rhs;
    }

    @Override
    protected String getName() {
        return "dc_p_2";
    }
}
