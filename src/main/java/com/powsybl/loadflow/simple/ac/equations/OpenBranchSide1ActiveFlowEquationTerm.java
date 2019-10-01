/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.equations;

import com.powsybl.loadflow.simple.equations.VariableSet;
import com.powsybl.loadflow.simple.equations.Variable;
import com.powsybl.loadflow.simple.equations.VariableType;
import com.powsybl.loadflow.simple.network.LfBranch;
import com.powsybl.loadflow.simple.network.LfBus;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide1ActiveFlowEquationTerm extends AbstractOpenBranchAcFlowEquationTerm {

    private final Variable v2Var;

    private double p2;

    private double dp2dv2;

    public OpenBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus2, VariableSet variableSet) {
        super(branch, VariableType.BUS_V, bus2, variableSet);
        v2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_V);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v2 = x[v2Var.getColumn()];
        p2 = r2 * r2 * v2 * v2 * (g2 + y * y * g1 / shunt + (b1 * b1 + g1 * g1) * y * sinKsi / shunt);
        dp2dv2 = 2 * r2 * r2 * v2 * (g2 + y * y * g1 / shunt + (b1 * b1 + g1 * g1) * y * sinKsi / shunt);
    }

    @Override
    public double eval() {
        return p2;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return dp2dv2;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
