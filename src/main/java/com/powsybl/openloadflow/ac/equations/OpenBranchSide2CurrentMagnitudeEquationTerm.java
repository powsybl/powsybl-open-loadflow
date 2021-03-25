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
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide2CurrentMagnitudeEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable v1Var;

    private final Variable ph1Var;

    private Variable r1Var;

    private double i1;

    private double di1dv1;

    public OpenBranchSide2CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, VariableSet variableSet,
                                                       boolean deriveA1, boolean deriveR1) {
        super(branch, VariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_V);
        ph1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_PHI);
        if (deriveR1) {
            r1Var = variableSet.getVariable(bus1.getNum(), VariableType.BRANCH_RHO1);
        }
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getRow()];
        double ph1 = x[ph1Var.getRow()];
        double r1 = r1Var != null ? x[r1Var.getRow()] : branch.getPiModel().getR1();
        double w1 = r1 * v1;
        double cosPh1 = FastMath.cos(ph1);
        double sinPh1 = FastMath.sin(ph1);

        double gres = g1 + (y * y * g2 + (b2 * b2 + g2 * g2) * y * sinKsi) / shunt;
        double bres = b1 + (y * y * b2 - (b2 * b2 + g2 * g2) * y * cosKsi) / shunt;

        double reI1 = r1 * w1 * (gres * cosPh1 - bres * sinPh1) * CURRENT_NORMALIZATION_FACTOR;
        double imI1 = r1 * w1 * (gres * sinPh1 + bres * cosPh1) * CURRENT_NORMALIZATION_FACTOR;
        i1 = Math.hypot(reI1, imI1);

        double dreI1dv1 = r1 * r1 * (gres * cosPh1 - bres * sinPh1) * CURRENT_NORMALIZATION_FACTOR;

        double dimI1dv1 = r1 * r1 * (gres * sinPh1 + bres * cosPh1) * CURRENT_NORMALIZATION_FACTOR;
        di1dv1 = (reI1 * dreI1dv1 + imI1 * dimI1dv1) / i1;
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
