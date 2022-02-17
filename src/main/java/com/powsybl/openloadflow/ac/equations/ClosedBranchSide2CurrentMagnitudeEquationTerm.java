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
public class ClosedBranchSide2CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide2CurrentMagnitudeEquationTerm(BranchVector branchVec, int num, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branchVec, num, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di2dph1() * dph1 + di2dph2() * dph2 + di2dv1() * dv1 + di2dv2() * dv2;
    }

    private double theta() {
        return branchVec.ksi(num) + a1() - A2 + ph1();
    }

    private double interReI2() {
        return branchVec.g2(num) * FastMath.cos(ph2()) - branchVec.b2(num) * FastMath.sin(ph2()) + branchVec.y(num) * FastMath.sin(ph2() + branchVec.ksi(num));
    }

    private double interImI2() {
        return branchVec.g2(num) * FastMath.sin(ph2()) + branchVec.b2(num) * FastMath.cos(ph2()) - branchVec.y(num) * FastMath.cos(ph2() + branchVec.ksi(num));
    }

    private double reI2() {
        return R2 * (R2 * v2() * interReI2() - branchVec.y(num) * r1() * v1() * FastMath.sin(theta()));
    }

    private double imI2() {
        return R2 * (R2 * v2() * interImI2() + branchVec.y(num) * r1() * v1() * FastMath.cos(theta()));
    }

    private double i2() {
        return FastMath.hypot(reI2(), imI2());
    }

    private double dreI2dv2() {
        return R2 * R2 * interReI2();
    }

    private double dreI2dv1() {
        return R2 * (-branchVec.y(num) * r1() * FastMath.sin(theta()));
    }

    private double dreI2dph2() {
        return R2 * R2 * v2() * (-branchVec.g2(num) * FastMath.sin(ph2()) - branchVec.b2(num) * FastMath.cos(ph2()) + branchVec.y(num) * FastMath.cos(ph2() + branchVec.ksi(num)));
    }

    private double dreI2dph1() {
        return R2 * (-branchVec.y(num) * r1() * v1() * FastMath.cos(theta()));
    }

    private double dimI2dv2() {
        return R2 * R2 * interImI2();
    }

    private double dimI2dv1() {
        return R2 * (branchVec.y(num) * r1() * FastMath.cos(theta()));
    }

    private double dimI2dph2() {
        return R2 * R2 * v2() * interReI2();
    }

    private double dimI2dph1() {
        return R2 * (-branchVec.y(num) * r1() * v1() * FastMath.sin(theta()));
    }

    private double di2dv2() {
        return (reI2() * dreI2dv2() + imI2() * dimI2dv2()) / i2();
    }

    private double di2dv1() {
        return (reI2() * dreI2dv1() + imI2() * dimI2dv1()) / i2();
    }

    private double di2dph2() {
        return (reI2() * dreI2dph2() + imI2() * dimI2dph2()) / i2();
    }

    private double di2dph1() {
        return (reI2() * dreI2dph1() + imI2() * dimI2dph1()) / i2();
    }

    private double di2da1() {
        return -di2dph1();
    }

    @Override
    public double eval() {
        return i2();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di2dv1();
        } else if (variable.equals(v2Var)) {
            return di2dv2();
        } else if (variable.equals(ph1Var)) {
            return di2dph1();
        } else if (variable.equals(ph2Var)) {
            return di2dph2();
        } else if (variable.equals(a1Var)) {
            return di2da1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_2";
    }
}
