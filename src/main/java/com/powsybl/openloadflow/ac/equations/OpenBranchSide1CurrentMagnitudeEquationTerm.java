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

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide1CurrentMagnitudeEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable v2Var;

    private final Variable ph2Var;

    private double i2;

    private double di2dv2;

    public OpenBranchSide1CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus2, VariableSet variableSet,
                                                       boolean deriveA1, boolean deriveR1) {
        super(branch, VariableType.BUS_V, bus2, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_V);
        ph2Var = variableSet.getVariable(bus2.getNum(), VariableType.BUS_PHI);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v2 = x[v2Var.getRow()];
        double ph2 = x[ph2Var.getRow()];
        double w2 = R2 * v2;
        double cosPh2 = FastMath.cos(ph2);
        double sinPh2 = FastMath.sin(ph2);

        double gres = g2 + (y * y * g1 + (b1 * b1 + g1 * g1) * y * sinKsi) / shunt;
        double bres = b2 + (y * y * b1 - (b1 * b1 + g1 * g1) * y * cosKsi) / shunt;

        double reI2 = R2 * w2 * (gres * cosPh2 - bres * sinPh2) * CURRENT_NORMALIZATION_FACTOR;
        double imI2 = R2 * w2 * (gres * sinPh2 + bres * cosPh2) * CURRENT_NORMALIZATION_FACTOR;
        i2 = FastMath.hypot(reI2, imI2);

        double dreI2dv2 = R2 * R2 * (gres * cosPh2 - bres * sinPh2) * CURRENT_NORMALIZATION_FACTOR;

        double dimI2dv2 = R2 * R2 * (gres * sinPh2 + bres * cosPh2) * CURRENT_NORMALIZATION_FACTOR;
        di2dv2 = (reI2 * dreI2dv2 + imI2 * dimI2dv2) / i2;
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
