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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ClosedBranchSide1ReactiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double a1, double r1) {
        return dq1dph1(stateVector) * dph1 + dq1dph2(stateVector) * dph2 + dq1dv1(stateVector) * dv1 + dq1dv2(stateVector) * dv2;
    }

    protected double theta(double ph1, double a1, double ph2) {
        return ksi - a1 + A2 - ph1 + ph2;
    }

    private double q1(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        return r1 * v1 * (-b1 * r1 * v1 + y * r1 * v1 * FastMath.cos(ksi)
                - y * R2 * v2(sv) * FastMath.cos(theta(ph1(sv), a1(sv), ph2(sv))));
    }

    private double dq1dv1(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        return r1 * (-2 * b1 * r1 * v1 + 2 * y * r1 * v1 * FastMath.cos(ksi)
                - y * R2 * v2(sv) * FastMath.cos(theta(ph1(sv), a1(sv), ph2(sv))));
    }

    private double dq1dv2(StateVector sv) {
        return -y * r1(sv) * R2 * v1(sv) * FastMath.cos(theta(ph1(sv), a1(sv), ph2(sv)));
    }

    private double dq1dph1(StateVector sv) {
        return -y * r1(sv) * R2 * v1(sv) * v2(sv) * FastMath.sin(theta(ph1(sv), a1(sv), ph2(sv)));
    }

    private double dq1dph2(StateVector sv) {
        return -dq1dph1(sv);
    }

    private double dq1da1(StateVector sv) {
        return dq1dph1(sv);
    }

    private double dq1dr1(StateVector sv) {
        double v1 = v1(sv);
        double r1 = r1(sv);
        return v1 * (2 * r1 * v1 * (-b1 + y * FastMath.cos(ksi)) - y * R2 * v2(sv) * FastMath.cos(theta(ph1(sv), a1(sv), ph2(sv))));
    }

    @Override
    public double eval() {
        return q1(stateVector);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dq1dv1(stateVector);
        } else if (variable.equals(v2Var)) {
            return dq1dv2(stateVector);
        } else if (variable.equals(ph1Var)) {
            return dq1dph1(stateVector);
        } else if (variable.equals(ph2Var)) {
            return dq1dph2(stateVector);
        } else if (variable.equals(a1Var)) {
            return dq1da1(stateVector);
        } else if (variable.equals(r1Var)) {
            return dq1dr1(stateVector);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_1";
    }
}
