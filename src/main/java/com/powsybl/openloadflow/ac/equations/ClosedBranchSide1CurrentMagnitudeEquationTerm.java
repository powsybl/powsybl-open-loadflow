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
public class ClosedBranchSide1CurrentMagnitudeEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1CurrentMagnitudeEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                         boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return di1dph1(stateVector) * dph1 + di1dph2(stateVector) * dph2 + di1dv1(stateVector) * dv1 + di1dv2(stateVector) * dv2;
    }

    private double theta(double a1, double ph2) {
        return ksi - a1 + A2 + ph2;
    }

    private double interReI1(double ph1) {
        return g1 * FastMath.cos(ph1) - b1 * FastMath.sin(ph1) + y * FastMath.sin(ph1 + ksi);
    }

    private double interImI1(double ph1) {
        return g1 * FastMath.sin(ph1) + b1 * FastMath.cos(ph1) - y * FastMath.cos(ph1 + ksi);
    }

    private double reI1(double v1, double ph1, double r1, double v2, double theta) {
        return r1 * (r1 * v1 * interReI1(ph1) - y * R2 * v2 * FastMath.sin(theta));
    }

    private double imI1(double v1, double ph1, double r1, double v2, double theta) {
        return r1 * (r1 * v1 * interImI1(ph1) + y * R2 * v2 * FastMath.cos(theta));
    }

    private double i1(double v1, double ph1, double r1, double v2, double theta) {
        return FastMath.hypot(reI1(v1, ph1, r1, v2, theta), imI1(v1, ph1, r1, v2, theta));
    }

    private double dreI1dv1(double ph1, double r1) {
        return r1 * r1 * interReI1(ph1);
    }

    private double dreI1dv2(double r1, double a1, double ph2) {
        return r1 * (-y * R2 * FastMath.sin(theta(a1, ph2)));
    }

    private double dreI1dph1(double v1, double ph1, double r1) {
        return r1 * r1 * v1 * (-g1 * FastMath.sin(ph1) - b1 * FastMath.cos(ph1) + y * FastMath.cos(ph1 + ksi));
    }

    private double dreI1dph2(double r1, double a1, double v2, double ph2) {
        return r1 * (-y * R2 * v2 * FastMath.cos(theta(a1, ph2)));
    }

    private double dimI1dv1(double ph1, double r1) {
        return r1 * r1 * interImI1(ph1);
    }

    private double dimI1dv2(double r1, double a1, double ph2) {
        return r1 * (y * R2 * FastMath.cos(theta(a1, ph2)));
    }

    private double dimI1dph1(double v1, double ph1, double r1) {
        return r1 * r1 * v1 * interReI1(ph1);
    }

    private double dimI1dph2(double r1, double a1, double v2, double ph2) {
        return r1 * (-y * R2 * v2 * FastMath.sin(theta(a1, ph2)));
    }

    private double di1dv1(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        double ph1 = ph1(sv);
        double v2 = v2(sv);
        double theta = theta(a1(sv), ph2(sv));
        return (reI1(v1, ph1, r1, v2, theta) * dreI1dv1(ph1, r1) + imI1(v1, ph1, r1, v2, theta) * dimI1dv1(ph1, r1)) / i1(v1, ph1, r1, v2, theta);
    }

    private double di1dv2(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        double ph1 = ph1(sv);
        double a1 = a1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double theta = theta(a1, ph2);
        return (reI1(v1, ph1, r1, v2, theta) * dreI1dv2(r1, a1, ph2) + imI1(v1, ph1, r1, v2, theta) * dimI1dv2(r1, a1, ph2)) / i1(v1, ph1, r1, v2, theta);
    }

    private double di1dph1(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        double ph1 = ph1(sv);
        double a1 = a1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double theta = theta(a1, ph2);
        return (reI1(v1, ph1, r1, v2, theta) * dreI1dph1(v1, ph1, r1) + imI1(v1, ph1, r1, v2, theta) * dimI1dph1(v1, ph1, r1)) / i1(v1, ph1, r1, v2, theta);
    }

    private double di1dph2(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        double ph1 = ph1(sv);
        double v2 = v2(sv);
        double ph2 = ph2(sv);
        double a1 = a1(sv);
        double theta = theta(a1, ph2);
        return (reI1(v1, ph1, r1, v2, theta) * dreI1dph2(r1, a1, v2, ph2) + imI1(v1, ph1, r1, v2, theta) * dimI1dph2(r1, a1, v2, ph2)) / i1(v1, ph1, r1, v2, theta);
    }

    private double di1da1(StateVector sv) {
        return -di1dph2(sv);
    }

    @Override
    public double eval() {
        double v1 = v1(stateVector);
        double r1 = r1(stateVector);
        double ph1 = ph1(stateVector);
        double v2 = v2(stateVector);
        double theta = theta(a1(stateVector), ph2(stateVector));
        return i1(v1, ph1, r1, v2, theta);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di1dv1(stateVector);
        } else if (variable.equals(v2Var)) {
            return di1dv2(stateVector);
        } else if (variable.equals(ph1Var)) {
            return di1dph1(stateVector);
        } else if (variable.equals(ph2Var)) {
            return di1dph2(stateVector);
        } else if (variable.equals(a1Var)) {
            return di1da1(stateVector);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_1";
    }
}
