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
public class OpenBranchSide1ReactiveFlowEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    public OpenBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(branch, AcVariableType.BUS_V, bus2, variableSet);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
    }

    public Variable<AcVariableType> getV2Var() {
        return v2Var;
    }

    private double v2() {
        return sv.get(v2Var.getRow());
    }

    public static double q2(double y, double cosKsi, double sinKsi, double g1, double b1, double b2, double v2) {
        return OpenBranchFormulas.q2(y, cosKsi, sinKsi, g1, b1, b2, v2);
    }

    public static double dq2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double b2, double v2) {
        return OpenBranchFormulas.dq2dv2(y, cosKsi, sinKsi, g1, b1, b2, v2);
    }

    @Override
    public double eval() {
        return q2(y, FastMath.cos(ksi), FastMath.sin(ksi), g1, b1, b2, v2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return dq2dv2(y, FastMath.cos(ksi), FastMath.sin(ksi), g1, b1, b2, v2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_open_1";
    }
}
