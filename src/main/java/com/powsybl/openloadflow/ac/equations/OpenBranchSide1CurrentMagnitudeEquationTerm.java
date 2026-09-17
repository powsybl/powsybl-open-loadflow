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
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 */
@SuppressWarnings("squid:S00107")
public class OpenBranchSide1CurrentMagnitudeEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    private final Variable<AcVariableType> ph2Var;

    public OpenBranchSide1CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(branch, AcVariableType.BUS_V, bus2, variableSet);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
    }

    private double v2() {
        return sv.get(v2Var.getRow());
    }

    private double ph2() {
        return sv.get(ph2Var.getRow());
    }

    public static double di2dv2(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v2, double ph2) {
        return OpenBranchCurrentMagnitudeFormulas.di2dv2(y, cosKsi, sinKsi, g1, b1, g2, b2, v2, ph2);
    }

    @Override
    public double eval() {
        return OpenBranchCurrentMagnitudeFormulas.i2(y, FastMath.cos(ksi), FastMath.sin(ksi), g1, b1, g2, b2, v2(), ph2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return di2dv2(y, FastMath.cos(ksi), FastMath.sin(ksi), g1, b1, g2, b2, v2(), ph2());
        } else if (variable.equals(ph2Var)) {
            throw new IllegalArgumentException("Derivative with respect to ph2 not implemented");
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_1";
    }
}
