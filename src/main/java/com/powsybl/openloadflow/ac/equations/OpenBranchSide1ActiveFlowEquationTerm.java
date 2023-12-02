/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OpenBranchSide1ActiveFlowEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    public OpenBranchSide1ActiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus2Num,
                                                 VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1) {
        super(branchVector, branchNum, AcVariableType.BUS_V, bus2Num, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.getVariable(bus2Num, AcVariableType.BUS_V);
    }

    public static double p2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double v2) {
        double shunt = shunt(y, cosKsi, sinKsi, g1, b1);
        return R2 * R2 * v2 * v2 * (g2 + y * y * g1 / shunt + (b1 * b1 + g1 * g1) * y * sinKsi / shunt);
    }

    public static double dp2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double v2) {
        double shunt = shunt(y, cosKsi, sinKsi, g1, b1);
        return 2 * R2 * R2 * v2 * (g2 + y * y * g1 / shunt + (b1 * b1 + g1 * g1) * y * sinKsi / shunt);
    }

    @Override
    public double eval() {
        return branchVector.p2[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return branchVector.dp2dv2[num];
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_open_1";
    }
}
