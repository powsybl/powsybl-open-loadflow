/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OpenBranchSide2ReactiveFlowEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    public OpenBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, VariableSet<AcVariableType> variableSet) {
        super(branch, AcVariableType.BUS_V, bus1, variableSet);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
    }

    private double v1() {
        return sv.get(v1Var.getRow());
    }

    private double r1() {
        return element.getPiModel().getR1();
    }

    public static double q1(double y, double cosKsi, double sinKsi, double b1, double g2, double b2, double v1, double r1) {
        double shunt = shunt(y, cosKsi, sinKsi, g2, b2);
        return -r1 * r1 * v1 * v1 * (b1 + y * y * b2 / shunt - (b2 * b2 + g2 * g2) * y * cosKsi / shunt);
    }

    public static double dq1dv1(double y, double cosKsi, double sinKsi, double b1, double g2, double b2, double v1, double r1) {
        double shunt = shunt(y, cosKsi, sinKsi, g2, b2);
        return -2 * v1 * r1 * r1 * (b1 + y * y * b2 / shunt - (b2 * b2 + g2 * g2) * y * cosKsi / shunt);
    }

    @Override
    public double eval() {
        return q1(y, FastMath.cos(ksi), FastMath.sin(ksi), b1, g2, b2, v1(), r1());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dq1dv1(y, FastMath.cos(ksi), FastMath.sin(ksi), b1, g2, b2, v1(), r1());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_open_2";
    }
}
