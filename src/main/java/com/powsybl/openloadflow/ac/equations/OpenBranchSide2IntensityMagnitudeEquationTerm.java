/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide2IntensityMagnitudeEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable v1Var;

    private double i1;

    private double di1dv1;

    public OpenBranchSide2IntensityMagnitudeEquationTerm(LfBranch branch, LfBus bus1, VariableSet variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, VariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_V);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getRow()];
        double r1 = branch.getPiModel().getR1();
        double p1 = r1 * r1 * v1 * v1 * (g1 + y * y * g2 / shunt + (b2 * b2 + g2 * g2) * y * sinKsi / shunt);
        double q1 = -r1 * r1 * v1 * v1 * (b1 + y * y * b2 / shunt - (b2 * b2 + g2 * g2) * y * cosKsi / shunt);

        i1 = Math.hypot(p1, q1) / (Math.sqrt(3.) * v1 / 1000);

        double tpv1 = r1 * r1 * (g1 + y * y * g2 / shunt + (b2 * b2 + g2 * g2) * y * sinKsi / shunt);
        double tqv1 = r1 * r1 * (b1 + y * y * b2 / shunt - (b2 * b2 + g2 * g2) * y * cosKsi / shunt);

        di1dv1 = 1000 / Math.sqrt(3) * Math.hypot(tpv1, tqv1);
    }

    @Override
    public double eval() {
        return i1;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di1dv1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_2";
    }
}
