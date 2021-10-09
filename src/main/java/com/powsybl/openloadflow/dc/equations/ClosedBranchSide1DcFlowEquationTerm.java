/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.NetworkBuffer;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class ClosedBranchSide1DcFlowEquationTerm extends AbstractClosedBranchDcFlowEquationTerm {

    private double p1;

    private double rhs;

    private ClosedBranchSide1DcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet, boolean deriveA1, boolean useTransformerRatio) {
        super(branch, bus1, bus2, variableSet, deriveA1, useTransformerRatio);
    }

    public static ClosedBranchSide1DcFlowEquationTerm create(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet,
                                                             boolean deriveA1, boolean useTransformerRatio) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        return new ClosedBranchSide1DcFlowEquationTerm(branch, bus1, bus2, variableSet, deriveA1, useTransformerRatio);
    }

    @Override
    protected double calculateSensi(double ph1, double ph2, double a1) {
        double deltaPhase =  ph2 - ph1 + A2 - a1;
        return -power * deltaPhase;
    }

    @Override
    public void update(double[] x, NetworkBuffer<DcVariableType, DcEquationType> buf) {
        double ph1 = x[ph1Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double a1 = getA1(x);
        p1 = calculateSensi(ph1, ph2, a1);
        if (a1Var != null) {
            rhs = -power * A2;
        } else {
            rhs = -power * (A2 - a1);
        }
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable<DcVariableType> variable, NetworkBuffer<DcVariableType, DcEquationType> buf) {
        if (variable.equals(ph1Var)) {
            return power;
        } else if (variable.equals(ph2Var)) {
            return -power;
        } else if (variable.equals(a1Var)) {
            return power;
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
        return "dc_p_1";
    }
}
