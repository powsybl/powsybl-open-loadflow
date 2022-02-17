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

    public OpenBranchSide2ActiveFlowEquationTerm(BranchVector branchVec, int num, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                 boolean deriveA1, boolean deriveR1) {
        super(branchVec, num, AcVariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
    }

    private double v1() {
        return stateVector.get(v1Var.getRow());
    }

    private double r1() {
        return branchVec.r1(num);
    }

    private double p2() {
        double shunt = shunt();
        return r1() * r1() * v1() * v1() * (branchVec.g1(num) + branchVec.y(num) * branchVec.y(num) * branchVec.g2(num) / shunt + (branchVec.b2(num) * branchVec.b2(num) + branchVec.g2(num) * branchVec.g2(num)) * branchVec.y(num) * FastMath.sin(branchVec.ksi(num)) / shunt);
    }

    private double dp2dv1() {
        double shunt = shunt();
        return 2 * r1() * r1() * v1() * (branchVec.g1(num) + branchVec.y(num) * branchVec.y(num) * branchVec.g2(num) / shunt + (branchVec.b2(num) * branchVec.b2(num) + branchVec.g2(num) * branchVec.g2(num)) * branchVec.y(num) * FastMath.sin(branchVec.ksi(num)) / shunt);
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
