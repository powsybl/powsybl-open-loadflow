/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide2CurrentMagnitudeEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    private final Variable<AcVariableType> ph1Var;

    private Variable<AcVariableType> r1Var;

    public OpenBranchSide2CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                       boolean deriveA1, boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        if (deriveR1) {
            r1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BRANCH_RHO1);
        }
    }

    private double v1() {
        return x[v1Var.getRow()];
    }

    private double ph1() {
        return x[ph1Var.getRow()];
    }

    private double r1() {
        return r1Var != null ? x[r1Var.getRow()] : branch.getPiModel().getR1();
    }

    @Override
    public void update(double[] x) {
        super.update(x);

    }

    private double gres() {
        return g1 + (y * y * g2 + (b2 * b2 + g2 * g2) * y * sinKsi) / shunt;
    }

    private double bres() {
        return b1 + (y * y * b2 - (b2 * b2 + g2 * g2) * y * cosKsi) / shunt;
    }

    private double reI1() {
        return r1() * r1() * v1() * (gres() * FastMath.cos(ph1()) - bres() * FastMath.sin(ph1()));
    }

    private double imI1() {
        return r1() * r1() * v1() * (gres() * FastMath.sin(ph1()) + bres() * FastMath.cos(ph1()));
    }

    private double i1() {
        return FastMath.hypot(reI1(), imI1());
    }

    private double dreI1dv1() {
        return r1() * r1() * (gres() * FastMath.cos(ph1()) - bres() * FastMath.sin(ph1()));
    }

    private double dimI1dv1() {
        return r1() * r1() * (gres() * FastMath.sin(ph1()) + bres() * FastMath.cos(ph1()));
    }

    private double di1dv1() {
        return (reI1() * dreI1dv1() + imI1() * dimI1dv1()) / i1();
    }

    @Override
    public double eval() {
        return i1();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di1dv1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_2";
    }
}
