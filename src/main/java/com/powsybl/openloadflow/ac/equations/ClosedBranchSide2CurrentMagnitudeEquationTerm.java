/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class ClosedBranchSide2CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide2CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di2dph1(stateVector) * dph1 + di2dph2(stateVector) * dph2 + di2dv1(stateVector) * dv1 + di2dv2(stateVector) * dv2;
    }

    private double theta(double ph1, double a1) {
        return ksi + a1 - A2 + ph1;
    }

    private double interReI2(double ph2) {
        return g2 * FastMath.cos(ph2) - b2 * FastMath.sin(ph2) + y * FastMath.sin(ph2 + ksi);
    }

    private double interImI2(double ph2) {
        return g2 * FastMath.sin(ph2) + b2 * FastMath.cos(ph2) - y * FastMath.cos(ph2 + ksi);
    }

    private double reI2(double v1, double r1, double v2, double ph2, double theta) {
        return R2 * (R2 * v2 * interReI2(ph2) - y * r1 * v1 * FastMath.sin(theta));
    }

    private double imI2(double v1, double r1, double v2, double ph2, double theta) {
        return R2 * (R2 * v2 * interImI2(ph2) + y * r1 * v1 * FastMath.cos(theta));
    }

    private double i2(double v1, double r1, double v2, double ph2, double theta) {
        return FastMath.hypot(reI2(v1, r1, v2, ph2, theta), imI2(v1, r1, v2, ph2, theta));
    }

    private double dreI2dv2(double ph2) {
        return R2 * R2 * interReI2(ph2);
    }

    private double dreI2dv1(double r1, double theta) {
        return R2 * (-y * r1 * FastMath.sin(theta));
    }

    private double dreI2dph2(double v2, double ph2) {
        return R2 * R2 * v2 * (-g2 * FastMath.sin(ph2) - b2 * FastMath.cos(ph2) + y * FastMath.cos(ph2 + ksi));
    }

    private double dreI2dph1(double v1, double ph1, double r1, double a1) {
        return R2 * (-y * r1 * v1 * FastMath.cos(theta(ph1, a1)));
    }

    private double dimI2dv2(double ph2) {
        return R2 * R2 * interImI2(ph2);
    }

    private double dimI2dv1(double ph1, double r1, double a1) {
        return R2 * (y * r1 * FastMath.cos(theta(ph1, a1)));
    }

    private double dimI2dph2(double v2, double ph2) {
        return R2 * R2 * v2 * interReI2(ph2);
    }

    private double dimI2dph1(double v1, double r1, double theta) {
        return R2 * (-y * r1 * v1 * FastMath.sin(theta));
    }

    private double di2dv2(StateVector sv) {
        double v1 = v1(sv);
        double ph1 = ph1(sv);
        double r1 = r1(sv);
        double a1 = a1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double theta = theta(ph1, a1);
        return (reI2(v1, r1, v2, ph2, theta) * dreI2dv2(ph2) + imI2(v1, r1, v2, ph2, theta) * dimI2dv2(ph2)) / i2(v1, r1, v2, ph2, theta);
    }

    private double di2dv1(StateVector sv) {
        double v1 = v1(sv);
        double ph1 = ph1(sv);
        double r1 = r1(sv);
        double a1 = a1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double theta = theta(ph1, a1);
        return (reI2(v1, r1, v2, ph2, theta) * dreI2dv1(r1, theta) + imI2(v1, r1, v2, ph2, theta) * dimI2dv1(ph1, r1, a1)) / i2(v1, r1, v2, ph2, theta);
    }

    private double di2dph2(StateVector sv) {
        double v1 = v1(sv);
        double ph1 = ph1(sv);
        double r1 = r1(sv);
        double a1 = a1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double theta = theta(ph1, a1);
        return (reI2(v1, r1, v2, ph2, theta) * dreI2dph2(v2, ph2) + imI2(v1, r1, v2, ph2, theta) * dimI2dph2(v2, ph2)) / i2(v1, r1, v2, ph2, theta);
    }

    private double di2dph1(StateVector sv) {
        double v1 = v1(sv);
        double ph1 = ph1(sv);
        double r1 = r1(sv);
        double a1 = a1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double theta = theta(ph1, a1);
        return (reI2(v1, r1, v2, ph2, theta) * dreI2dph1(v1, ph1, r1, a1) + imI2(v1, r1, v2, ph2, theta) * dimI2dph1(v1, r1, theta)) / i2(v1, r1, v2, ph2, theta);
    }

    private double di2da1(StateVector sv) {
        return -di2dph1(sv);
    }

    @Override
    public double eval() {
        double v1 = v1(stateVector);
        double ph1 = ph1(stateVector);
        double r1 = r1(stateVector);
        double a1 = a1(stateVector);
        double v2 = v2(stateVector);
        double ph2 = ph2(stateVector);
        double theta = theta(ph1, a1);
        return i2(v1, r1, v2, ph2, theta);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di2dv1(stateVector);
        } else if (variable.equals(v2Var)) {
            return di2dv2(stateVector);
        } else if (variable.equals(ph1Var)) {
            return di2dph1(stateVector);
        } else if (variable.equals(ph2Var)) {
            return di2dph2(stateVector);
        } else if (variable.equals(a1Var)) {
            return di2da1(stateVector);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_2";
    }
}
