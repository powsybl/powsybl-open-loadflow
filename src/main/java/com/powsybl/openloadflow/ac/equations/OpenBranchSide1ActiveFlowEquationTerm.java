/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide1ActiveFlowEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    public OpenBranchSide1ActiveFlowEquationTerm(VectorizedBranches branches, int num, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                 boolean deriveA1, boolean deriveR1) {
        super(branches, num, AcVariableType.BUS_V, bus2, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
    }

    private double v2() {
        return stateVector.get(v2Var.getRow());
    }

    private double p2() {
        double shunt = shunt();
        return R2 * R2 * v2() * v2() * (branches.g2(num) + branches.y(num) * branches.y(num) * branches.g1(num) / shunt + (branches.b1(num) * branches.b1(num) + branches.g1(num) * branches.g1(num)) * branches.y(num) * FastMath.sin(branches.ksi(num)) / shunt);
    }

    private double dp2dv2() {
        double shunt = shunt();
        return 2 * R2 * R2 * v2() * (branches.g2(num) + branches.y(num) * branches.y(num) * branches.g1(num) / shunt + (branches.b1(num) * branches.b1(num) + branches.g1(num) * branches.g1(num)) * branches.y(num) * FastMath.sin(branches.ksi(num)) / shunt);
    }

    @Override
    public double eval() {
        return p2();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return dp2dv2();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_open_1";
    }
}
