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

import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ActiveFlowEquationTerm(VectorizedBranches branches, int num, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branches, num, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dp1dph1() * dph1 + dp1dph2() * dph2 + dp1dv1() * dv1 + dp1dv2() * dv2;
    }

    protected double theta() {
        return branches.ksi(num) - (a1Var != null ? stateVector.get(a1Var.getRow()) : branches.a1(num)) + A2 - ph1() + ph2();
    }

    private double p1() {
        return r1() * v1() * (branches.g1(num) * r1() * v1() + branches.y(num) * r1() * v1() * FastMath.sin(branches.ksi(num)) - branches.y(num) * R2 * v2() * FastMath.sin(theta()));
    }

    private double dp1dv1() {
        return r1() * (2 * branches.g1(num) * r1() * v1() + 2 * branches.y(num) * r1() * v1() * FastMath.sin(branches.ksi(num)) - branches.y(num) * R2 * v2() * FastMath.sin(theta()));
    }

    private double dp1dv2() {
        return -branches.y(num) * r1() * R2 * v1() * FastMath.sin(theta());
    }

    private double dp1dph1() {
        return branches.y(num) * r1() * R2 * v1() * v2() * FastMath.cos(theta());
    }

    private double dp1dph2() {
        return -dp1dph1();
    }

    private double dp1da1() {
        return dp1dph1();
    }

    private double dp1dr1() {
        return v1() * (2 * r1() * v1() * (branches.g1(num) + branches.y(num) * FastMath.sin(branches.ksi(num))) - branches.y(num) * R2 * v2() * FastMath.sin(theta()));
    }

    @Override
    public double eval() {
        return p1();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1();
        } else if (variable.equals(v2Var)) {
            return dp1dv2();
        } else if (variable.equals(ph1Var)) {
            return dp1dph1();
        } else if (variable.equals(ph2Var)) {
            return dp1dph2();
        } else if (variable.equals(a1Var)) {
            return dp1da1();
        } else if (variable.equals(r1Var)) {
            return dp1dr1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
