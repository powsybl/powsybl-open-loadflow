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
import com.powsybl.openloadflow.util.Evaluable;
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
        return stateVector.get(v1Var.getRow());
    }

    private double ph1() {
        return stateVector.get(ph1Var.getRow());
    }

    private double r1() {
        return r1Var != null ? stateVector.get(r1Var.getRow()) : branch.getPiModel().getR1();
    }

    private double gres(double shunt) {
        return g1 + (y * y * g2 + (b2 * b2 + g2 * g2) * y * FastMath.sin(ksi)) / shunt;
    }

    private double bres(double shunt) {
        return b1 + (y * y * b2 - (b2 * b2 + g2 * g2) * y * FastMath.cos(ksi)) / shunt;
    }

    private double reI2() {
        double shunt = shunt();
        return r1() * r1() * v1() * (gres(shunt) * FastMath.cos(ph1()) - bres(shunt) * FastMath.sin(ph1()));
    }

    private double imI2() {
        double shunt = shunt();
        return r1() * r1() * v1() * (gres(shunt) * FastMath.sin(ph1()) + bres(shunt) * FastMath.cos(ph1()));
    }

    private double i2() {
        return FastMath.hypot(reI2(), imI2());
    }

    private double dreI2dv1() {
        double shunt = shunt();
        return r1() * r1() * (gres(shunt) * FastMath.cos(ph1()) - bres(shunt) * FastMath.sin(ph1()));
    }

    private double dimI2dv1() {
        double shunt = shunt();
        return r1() * r1() * (gres(shunt) * FastMath.sin(ph1()) + bres(shunt) * FastMath.cos(ph1()));
    }

    private double di2dv1() {
        return (reI2() * dreI2dv1() + imI2() * dimI2dv1()) / i2();
    }

    @Override
    public double eval() {
        return i2();
    }

    @Override
    public Evaluable der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return this::di2dv1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_2";
    }
}
