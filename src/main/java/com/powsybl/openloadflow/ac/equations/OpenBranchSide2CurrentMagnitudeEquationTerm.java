/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.BranchVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class OpenBranchSide2CurrentMagnitudeEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    private final Variable<AcVariableType> ph1Var;

    private Variable<AcVariableType> r1Var;

    private double i1;

    private double di1dv1;

    public OpenBranchSide2CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                       boolean deriveA1, boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_V);
        ph1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_PHI);
        if (deriveR1) {
            r1Var = variableSet.create(bus1.getNum(), AcVariableType.BRANCH_RHO1);
        }
    }

    @Override
    public void update(double[] x, BranchVector<AcVariableType, AcEquationType> vec) {
        AcBranchVector acVec = (AcBranchVector) vec;
        double v1 = x[acVec.v1Row[num]];
        double ph1 = x[acVec.ph1Row[num]];
        double r1 = acVec.r1Row[num] != -1 ? x[acVec.r1Row[num]] : vec.r1[num];
        double w1 = r1 * v1;
        double cosPh1 = FastMath.cos(ph1);
        double sinPh1 = FastMath.sin(ph1);

        double shunt = getShunt(vec);
        double gres = vec.g1[num] + (vec.y[num] * vec.y[num] * vec.g2[num] + (vec.b2[num] * vec.b2[num] + vec.g2[num] * vec.g2[num]) * vec.y[num] * vec.sinKsi[num]) / shunt;
        double bres = vec.b1[num] + (vec.y[num] * vec.y[num] * vec.b2[num] - (vec.b2[num] * vec.b2[num] + vec.g2[num] * vec.g2[num]) * vec.y[num] * vec.cosKsi[num]) / shunt;

        double reI1 = r1 * w1 * (gres * cosPh1 - bres * sinPh1);
        double imI1 = r1 * w1 * (gres * sinPh1 + bres * cosPh1);
        i1 = FastMath.hypot(reI1, imI1);

        double dreI1dv1 = r1 * r1 * (gres * cosPh1 - bres * sinPh1);

        double dimI1dv1 = r1 * r1 * (gres * sinPh1 + bres * cosPh1);
        di1dv1 = (reI1 * dreI1dv1 + imI1 * dimI1dv1) / i1;
    }

    @Override
    public double eval() {
        return i1;
    }

    @Override
    public double der(Variable<AcVariableType> variable, BranchVector<AcVariableType, AcEquationType> vec) {
        AcBranchVector acVec = (AcBranchVector) vec;
        if (variable.getType() == AcVariableType.BUS_V && variable.getRow() == acVec.v1Row[num]) {
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
