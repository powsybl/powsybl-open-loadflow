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

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide1ActiveFlowEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    private double p2;

    private double dp2dv2;

    public OpenBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                 boolean deriveA1, boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus2, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
    }

    @Override
    public void update(double[] x, BranchVector branchVector) {
        Objects.requireNonNull(x);
        double v2 = x[v2Var.getRow()];
        double shunt = getShunt(branchVector);
        p2 = R2 * R2 * v2 * v2 * (branchVector.g2[branchNum] + branchVector.y[branchNum] * branchVector.y[branchNum] * branchVector.g1[branchNum] / shunt + (branchVector.b1[branchNum] * branchVector.b1[branchNum] + branchVector.g1[branchNum] * branchVector.g1[branchNum]) * branchVector.y[branchNum] * branchVector.sinKsi[branchNum] / shunt);
        dp2dv2 = 2 * R2 * R2 * v2 * (branchVector.g2[branchNum] + branchVector.y[branchNum] * branchVector.y[branchNum] * branchVector.g1[branchNum] / shunt + (branchVector.b1[branchNum] * branchVector.b1[branchNum] + branchVector.g1[branchNum] * branchVector.g1[branchNum]) * branchVector.y[branchNum] * branchVector.sinKsi[branchNum] / shunt);
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

    @Override
    protected String getName() {
        return "ac_p_open_1";
    }
}
