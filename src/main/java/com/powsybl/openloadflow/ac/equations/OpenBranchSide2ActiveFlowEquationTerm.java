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

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide2ActiveFlowEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    public OpenBranchSide2ActiveFlowEquationTerm(VectorizedBranches branches, int num, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                 boolean deriveA1, boolean deriveR1) {
        super(branches, num, AcVariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
    }

    private double v1() {
        return stateVector.get(v1Var.getRow());
    }

    private double r1() {
        return branches.r1(num);
    }

    private double p2() {
        double shunt = shunt();
        return r1() * r1() * v1() * v1() * (branches.g1(num) + branches.y(num) * branches.y(num) * branches.g2(num) / shunt + (branches.b2(num) * branches.b2(num) + branches.g2(num) * branches.g2(num)) * branches.y(num) * FastMath.sin(branches.ksi(num)) / shunt);
    }

    private double dp2dv1() {
        double shunt = shunt();
        return 2 * r1() * r1() * v1() * (branches.g1(num) + branches.y(num) * branches.y(num) * branches.g2(num) / shunt + (branches.b2(num) * branches.b2(num) + branches.g2(num) * branches.g2(num)) * branches.y(num) * FastMath.sin(branches.ksi(num)) / shunt);
    }

    @Override
    public double eval() {
        return p2();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp2dv1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_open_2";
    }
}
