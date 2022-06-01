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

import static com.powsybl.openloadflow.network.PiModel.A2;
import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ClosedBranchSide1ActiveFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double v1 = v1(stateVector);
        double ph1 = ph1(stateVector);
        double r1 = r1(stateVector);
        double a1 = a1(stateVector);
        double v2 = v2(stateVector);
        double ph2 = ph2(stateVector);
        return dp1dph1(y, ksi, v1, ph1, r1, a1, v2, ph2) * dph1 + dp1dph2(y, ksi, v1, ph1, r1, a1, v2, ph2) * dph2
                + dp1dv1(y, ksi, g1, v1, ph1, r1, a1, v2, ph2) * dv1 + dp1dv2(y, ksi, v1, ph1, r1, a1, ph2) * dv2;
    }

    protected static double theta(double ksi, double ph1, double a1, double ph2) {
        return ksi - a1 + A2 - ph1 + ph2;
    }

    private static double p1(double y, double ksi, double g1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return r1 * v1 * (g1 * r1 * v1 + y * r1 * v1 * FastMath.sin(ksi) - y * R2 * v2 * FastMath.sin(theta(ksi, ph1, a1, ph2)));
    }

    private static double dp1dv1(double y, double ksi, double g1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return r1 * (2 * g1 * r1 * v1 + 2 * y * r1 * v1 * FastMath.sin(ksi) - y * R2 * v2 * FastMath.sin(theta(ksi, ph1, a1, ph2)));
    }

    private static double dp1dv2(double y, double ksi, double v1, double ph1, double r1, double a1, double ph2) {
        return -y * r1 * R2 * v1 * FastMath.sin(theta(ksi, ph1, a1, ph2));
    }

    private static double dp1dph1(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return y * r1 * R2 * v1 * v2 * FastMath.cos(theta(ksi, ph1, a1, ph2));
    }

    private static double dp1dph2(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return -dp1dph1(y, ksi, v1, ph1, r1, a1, v2, ph2);
    }

    private static double dp1da1(double y, double ksi, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return dp1dph1(y, ksi, v1, ph1, r1, a1, v2, ph2);
    }

    private static double dp1dr1(double y, double ksi, double g1, double v1, double ph1, double r1, double a1, double v2, double ph2) {
        return v1 * (2 * r1 * v1 * (g1 + y * FastMath.sin(ksi)) - y * R2 * v2 * FastMath.sin(theta(ksi, ph1, a1, ph2)));
    }

    @Override
    public double eval() {
        return p1(y, ksi, g1, v1(stateVector), ph1(stateVector),
                r1(stateVector), a1(stateVector), v2(stateVector), ph2(stateVector));
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1(y, ksi, g1, v1(stateVector), ph1(stateVector),
                    r1(stateVector), a1(stateVector), v2(stateVector), ph2(stateVector));
        } else if (variable.equals(v2Var)) {
            return dp1dv2(y, ksi, v1(stateVector), ph1(stateVector), r1(stateVector),
                    a1(stateVector), ph2(stateVector));
        } else if (variable.equals(ph1Var)) {
            return dp1dph1(y, ksi, v1(stateVector), ph1(stateVector), r1(stateVector), a1(stateVector), v2(stateVector), ph2(stateVector));
        } else if (variable.equals(ph2Var)) {
            return dp1dph2(y, ksi, v1(stateVector), ph1(stateVector), r1(stateVector),
                    a1(stateVector), v2(stateVector), ph2(stateVector));
        } else if (variable.equals(a1Var)) {
            return dp1da1(y, ksi, v1(stateVector), ph1(stateVector), r1(stateVector),
                    a1(stateVector), v2(stateVector), ph2(stateVector));
        } else if (variable.equals(r1Var)) {
            return dp1dr1(y, ksi, g1, v1(stateVector), ph1(stateVector),
                    r1(stateVector), a1(stateVector), v2(stateVector), ph2(stateVector));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
