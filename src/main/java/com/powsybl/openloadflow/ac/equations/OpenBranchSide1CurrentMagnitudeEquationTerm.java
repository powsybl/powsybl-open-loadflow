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

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide1CurrentMagnitudeEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    private final Variable<AcVariableType> ph2Var;

    public OpenBranchSide1CurrentMagnitudeEquationTerm(VectorizedBranches branches, int num, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                       boolean deriveA1, boolean deriveR1) {
        super(branches, num, AcVariableType.BUS_V, bus2, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
    }

    private double v2() {
        return stateVector.get(v2Var.getRow());
    }

    private double ph2() {
        return stateVector.get(ph2Var.getRow());
    }

    private double gres(double shunt) {
        return branches.g2(num) + (branches.y(num) * branches.y(num) * branches.g1(num) + (branches.b1(num) * branches.b1(num) + branches.g1(num) * branches.g1(num)) * branches.y(num) * FastMath.sin(branches.ksi(num))) / shunt;
    }

    private double bres(double shunt) {
        return branches.b2(num) + (branches.y(num) * branches.y(num) * branches.b1(num) - (branches.b1(num) * branches.b1(num) + branches.g1(num) * branches.g1(num)) * branches.y(num) * FastMath.cos(branches.ksi(num))) / shunt;
    }

    private double reI2() {
        double shunt = shunt();
        return R2 * R2 * v2() * (gres(shunt) * FastMath.cos(ph2()) - bres(shunt) * FastMath.sin(ph2()));
    }

    private double imI2() {
        double shunt = shunt();
        return R2 * R2 * v2() * (gres(shunt) * FastMath.sin(ph2()) + bres(shunt) * FastMath.cos(ph2()));
    }

    private double i2() {
        return FastMath.hypot(reI2(), imI2());
    }

    private double dreI2dv2() {
        double shunt = shunt();
        return R2 * R2 * (gres(shunt) * FastMath.cos(ph2()) - bres(shunt) * FastMath.sin(ph2()));
    }

    private double dimI2dv2() {
        double shunt = shunt();
        return R2 * R2 * (gres(shunt) * FastMath.sin(ph2()) + bres(shunt) * FastMath.cos(ph2()));
    }

    private double di2dv2() {
        return (reI2() * dreI2dv2() + imI2() * dimI2dv2()) / i2();
    }

    @Override
    public double eval() {
        return i2();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v2Var)) {
            return di2dv2();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_open_1";
    }
}
