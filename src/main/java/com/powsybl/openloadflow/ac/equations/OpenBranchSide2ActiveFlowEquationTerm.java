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

import java.util.Objects;

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
    public void update(double[] x, BranchVector branchVector) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getRow()];
        double r1 = branch.getPiModel().getR1();
        double shunt = getShunt(branchVector);
        p1 = r1 * r1 * v1 * v1 * (branchVector.g1[branchNum] + branchVector.y[branchNum] * branchVector.y[branchNum] * branchVector.g2[branchNum] / shunt + (branchVector.b2[branchNum] * branchVector.b2[branchNum] + branchVector.g2[branchNum] * branchVector.g2[branchNum]) * branchVector.y[branchNum] * branchVector.sinKsi[branchNum] / shunt);
        dp1dv1 = 2 * r1 * r1 * v1 * (branchVector.g1[branchNum] + branchVector.y[branchNum] * branchVector.y[branchNum] * branchVector.g2[branchNum] / shunt + (branchVector.b2[branchNum] * branchVector.b2[branchNum] + branchVector.g2[branchNum] * branchVector.g2[branchNum]) * branchVector.y[branchNum] * branchVector.sinKsi[branchNum] / shunt);
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
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
