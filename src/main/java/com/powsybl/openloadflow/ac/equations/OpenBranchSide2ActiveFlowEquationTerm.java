/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.BranchVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide2ActiveFlowEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    private double p1;

    private double dp1dv1;

    public OpenBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                 boolean deriveA1, boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
    }

    @Override
    public void update(double[] x, BranchVector vec) {
        double v1 = x[v1Var.getRow()];
        double r1 = vec.r1[num];
        double shunt = getShunt(vec);
        p1 = r1 * r1 * v1 * v1 * (vec.g1[num] + vec.y[num] * vec.y[num] * vec.g2[num] / shunt + (vec.b2[num] * vec.b2[num] + vec.g2[num] * vec.g2[num]) * vec.y[num] * vec.sinKsi[num] / shunt);
        dp1dv1 = 2 * r1 * r1 * v1 * (vec.g1[num] + vec.y[num] * vec.y[num] * vec.g2[num] / shunt + (vec.b2[num] * vec.b2[num] + vec.g2[num] * vec.g2[num]) * vec.y[num] * vec.sinKsi[num] / shunt);
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        if (variable.equals(v1Var)) {
            return dp1dv1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_open_2";
    }
}
