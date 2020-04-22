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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ClosedBranchSide2ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    private double q2;

    private double dq2dv1;

    private double dq2dv2;

    private double dq2dph1;

    private double dq2dph2;

    private double dq2da1;

    private double dq2da2;

    public ClosedBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet,
                                                     AcEquationTermDerivativeParameters derivativeParameters) {
        super(branch, bus1, bus2, variableSet, derivativeParameters);
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getColumn()];
        double v2 = x[v2Var.getColumn()];
        double ph1 = x[ph1Var.getColumn()];
        double ph2 = x[ph2Var.getColumn()];
        double theta = ksi + (a1Var != null ? x[a1Var.getColumn()] : a1) - (a2Var != null ? x[a2Var.getColumn()] : a2) + ph1 - ph2;
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        q2 = r2 * v2 * (-b2 * r2 * v2 - y * r1 * v1 * cosTheta + y * r2 * v2 * cosKsi);
        dq2dv1 = -y * r1 * r2 * v2 * cosTheta;
        dq2dv2 = r2 * (-2 * b2 * r2 * v2 - y * r1 * v1 * cosTheta + 2 * y * r2 * v2 * cosKsi);
        dq2dph1 = y * r1 * r2 * v1 * v2 * sinTheta;
        dq2dph2 = -dq2dph1;
        if (a1Var != null) {
            dq2da1 = dq2dph1;
        }
        if (a2Var != null) {
            dq2da2 = dq2dph2;
        }
    }

    @Override
    public double eval() {
        return q2;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dq2dv1;
        } else if (variable.equals(v2Var)) {
            return dq2dv2;
        } else if (variable.equals(ph1Var)) {
            return dq2dph1;
        } else if (variable.equals(ph2Var)) {
            return dq2dph2;
        } else if (variable.equals(a1Var)) {
            return dq2da1;
        } else if (variable.equals(a2Var)) {
            return dq2da2;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_2";
    }
}
