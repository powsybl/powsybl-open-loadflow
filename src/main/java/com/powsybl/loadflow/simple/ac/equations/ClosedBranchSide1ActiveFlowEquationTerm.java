/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.equations;

import com.powsybl.loadflow.simple.equations.VariableSet;
import com.powsybl.loadflow.simple.equations.Variable;
import com.powsybl.loadflow.simple.network.LfBranch;
import com.powsybl.loadflow.simple.network.LfBus;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double p1;

    private double dp1dv1;

    private double dp1dv2;

    private double dp1dph1;

    private double dp1dph2;

    public ClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet) {
        super(branch, bus1, bus2, variableSet);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getColumn()];
        double v2 = x[v2Var.getColumn()];
        double ph1 = x[ph1Var.getColumn()];
        double ph2 = x[ph2Var.getColumn()];
        double theta = ksi - a1 + a2 - ph1 + ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);
        p1 = r1 * v1 * (g1 * r1 * v1 + y * r1 * v1 * sinKsi - y * r2 * v2 * sinTheta);
        dp1dv1 = r1 * (2 * g1 * r1 * v1 + 2 * y * r1 * v1 * sinKsi - y * r2 * v2 * sinTheta);
        dp1dv2 = -y * r1 * r2 * v1 * sinTheta;
        dp1dph1 = y * r1 * r2 * v1 * v2 * cosTheta;
        dp1dph2 = -y * r1 * r2 * v1 * v2 * cosTheta;
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1;
        } else if (variable.equals(v2Var)) {
            return dp1dv2;
        } else if (variable.equals(ph1Var)) {
            return dp1dph1;
        } else if (variable.equals(ph2Var)) {
            return dp1dph2;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
