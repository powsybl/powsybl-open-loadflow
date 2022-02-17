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
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class ClosedBranchSide1CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1CurrentMagnitudeEquationTerm(BranchVector branchVec, int num, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branchVec, num, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di1dph1() * dph1 + di1dph2() * dph2 + di1dv1() * dv1 + di1dv2() * dv2;
    }

    private double theta() {
        return branchVec.ksi(num) - a1() + A2 + ph2();
    }

    private double interReI1() {
        return branchVec.g1(num) * FastMath.cos(ph1()) - branchVec.b1(num) * FastMath.sin(ph1()) + branchVec.y(num) * FastMath.sin(ph1() + branchVec.ksi(num));
    }

    private double interImI1() {
        return branchVec.g1(num) * FastMath.sin(ph1()) + branchVec.b1(num) * FastMath.cos(ph1()) - branchVec.y(num) * FastMath.cos(ph1() + branchVec.ksi(num));
    }

    private double reI1() {
        return r1() * (r1() * v1() * interReI1() - branchVec.y(num) * R2 * v2() * FastMath.sin(theta()));
    }

    private double imI1() {
        return r1() * (r1() * v1() * interImI1() + branchVec.y(num) * R2 * v2() * FastMath.cos(theta()));
    }

    private double i1() {
        return FastMath.hypot(reI1(), imI1());
    }

    private double dreI1dv1() {
        return r1() * r1() * interReI1();
    }

    private double dreI1dv2() {
        return r1() * (-branchVec.y(num) * R2 * FastMath.sin(theta()));
    }

    private double dreI1dph1() {
        return r1() * r1() * v1() * (-branchVec.g1(num) * FastMath.sin(ph1()) - branchVec.b1(num) * FastMath.cos(ph1()) + branchVec.y(num) * FastMath.cos(ph1() + branchVec.ksi(num)));
    }

    private double dreI1dph2() {
        return r1() * (-branchVec.y(num) * R2 * v2() * FastMath.cos(theta()));
    }

    private double dimI1dv1() {
        return r1() * r1() * interImI1();
    }

    private double dimI1dv2() {
        return r1() * (branchVec.y(num) * R2 * FastMath.cos(theta()));
    }

    private double dimI1dph1() {
        return r1() * r1() * v1() * interReI1();
    }

    private double dimI1dph2() {
        return r1() * (-branchVec.y(num) * R2 * v2() * FastMath.sin(theta()));
    }

    private double di1dv1() {
        return (reI1() * dreI1dv1() + imI1() * dimI1dv1()) / i1();
    }

    private double di1dv2() {
        return (reI1() * dreI1dv2() + imI1() * dimI1dv2()) / i1();
    }

    private double di1dph1() {
        return (reI1() * dreI1dph1() + imI1() * dimI1dph1()) / i1();
    }

    private double di1dph2() {
        return (reI1() * dreI1dph2() + imI1() * dimI1dph2()) / i1();
    }

    private double di1da1() {
        return -di1dph2();
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
        } else if (variable.equals(v2Var)) {
            return di1dv2();
        } else if (variable.equals(ph1Var)) {
            return di1dph1();
        } else if (variable.equals(ph2Var)) {
            return di1dph2();
        } else if (variable.equals(a1Var)) {
            return di1da1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_1";
    }
}
