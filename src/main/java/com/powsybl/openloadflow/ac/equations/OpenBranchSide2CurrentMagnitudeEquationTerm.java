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
public class OpenBranchSide2CurrentMagnitudeEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    private final Variable<AcVariableType> ph1Var;

    private Variable<AcVariableType> r1Var;

    public OpenBranchSide2CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                       boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus1, variableSet);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        if (deriveR1) {
            r1Var = variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1);
        }
    }

    private double v1() {
        return sv.get(v1Var.getRow());
    }

    private double ph1() {
        return sv.get(ph1Var.getRow());
    }

    private double r1() {
        return r1Var != null ? sv.get(r1Var.getRow()) : element.getPiModel().getR1();
    }

    public static double di1dv1(double y, double cosKsi, double sinKsi, double g1, double b1, double g2, double b2, double v1, double ph1, double r1) {
        return OpenBranchCurrentMagnitudeFormulas.di1dv1(y, cosKsi, sinKsi, g1, b1, g2, b2, v1, ph1, r1);
    }

    @Override
    public double eval() {
        return OpenBranchCurrentMagnitudeFormulas.i1(y, FastMath.cos(ksi), FastMath.sin(ksi), g1, b1, g2, b2, v1(), ph1(), r1());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di1dv1(y, FastMath.cos(ksi), FastMath.sin(ksi), g1, b1, g2, b2, v1(), ph1(), r1());
        } else if (variable.equals(ph1Var) || variable.equals(r1Var)) {
            throw new IllegalArgumentException("Derivative with respect to ph1 or r1 not implemented");
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_2";
    }
}
