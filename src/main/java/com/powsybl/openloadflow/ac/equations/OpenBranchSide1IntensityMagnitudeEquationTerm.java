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

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide1IntensityMagnitudeEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable v2Var;

    private double i2;

    private double di2dv2;

    public OpenBranchSide1IntensityMagnitudeEquationTerm(LfBranch branch, LfBus bus2, VariableSet variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, VariableType.BUS_V, bus2, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_V);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v2 = x[v2Var.getRow()];
        double p2 = R2 * R2 * v2 * v2 * (g2 + y * y * g1 / shunt + (b1 * b1 + g1 * g1) * y * sinKsi / shunt);
        double q2 = -R2 * R2 * v2 * v2 * (b2 + y * y * b1 / shunt - (b1 * b1 + g1 * g1) * y * cosKsi / shunt);

        i2 = Math.hypot(p2, q2) / (Math.sqrt(3.) * v2 / 1000);

        double tpv2 = R2 * R2 * (g2 + y * y * g1 / shunt + (b1 * b1 + g1 * g1) * y * sinKsi / shunt);
        double tqv2 = R2 * R2 * (b2 + y * y * b1 / shunt - (b1 * b1 + g1 * g1) * y * cosKsi / shunt);

        di2dv2 = 1000 / Math.sqrt(3) * Math.hypot(tpv2, tqv2);
    }

    @Override
    public double eval() {
        return i2;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return di2dv2;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_1";
    }
}
