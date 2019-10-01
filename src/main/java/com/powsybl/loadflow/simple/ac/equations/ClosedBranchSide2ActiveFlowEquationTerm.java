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
public class ClosedBranchSide2ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double p2;

    private double dp2dv1;

    private double dp2dv2;

    private double dp2dph1;

    private double dp2dph2;

    public ClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet) {
        super(branch, bus1, bus2, variableSet);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getColumn()];
        double v2 = x[v2Var.getColumn()];
        double ph1 = x[ph1Var.getColumn()];
        double ph2 = x[ph2Var.getColumn()];
        double theta = ksi + a1 - a2 + ph1 - ph2;
        double sinTheta = FastMath.sin(theta);
        double cosTheta = FastMath.cos(theta);
        p2 = r2 * v2 * (g2 * r2 * v2 - y * r1 * v1 * sinTheta + y * r2 * v2 * sinKsi);
        dp2dv1 = -y * r1 * r2 * v2 * sinTheta;
        dp2dv2 = r2 * (2 * g2 * r2 * v2 - y * r1 * v1 * sinTheta + 2 * y * r2 * v2 * sinKsi);
        dp2dph1 = -y * r1 * r2 * v1 * v2 * cosTheta;
        dp2dph2 = y * r1 * r2 * v1 * v2 * cosTheta;
    }

    @Override
    public double eval() {
        return p2;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp2dv1;
        } else if (variable.equals(v2Var)) {
            return dp2dv2;
        } else if (variable.equals(ph1Var)) {
            return dp2dph1;
        } else if (variable.equals(ph2Var)) {
            return dp2dph2;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
