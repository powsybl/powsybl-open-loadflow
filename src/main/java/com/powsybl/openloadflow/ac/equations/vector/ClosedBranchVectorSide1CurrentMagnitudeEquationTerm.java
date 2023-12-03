/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide1CurrentMagnitudeEquationTerm.*;

/**
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchVectorSide1CurrentMagnitudeEquationTerm extends AbstractClosedBranchVectorAcFlowEquationTerm {

    public ClosedBranchVectorSide1CurrentMagnitudeEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                               VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    @Override
    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        if (dr1 != 0) {
            throw new IllegalArgumentException("Derivative with respect to r1 not implemented");
        }
        double y = branchVector.y[num];
        double ksi = branchVector.ksi[num];
        double g1 = branchVector.g1[num];
        double b1 = branchVector.b1[num];
        double v1 = v1();
        double ph1 = ph1();
        double r1 = r1();
        double a1 = a1();
        double v2 = v2();
        double ph2 = ph2();
        return di1dph1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2) * dph1
                + di1dph2(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2) * dph2
                + di1dv1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2) * dv1
                + di1dv2(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2) * dv2
                + di1da1(y, ksi, g1, b1, v1, ph1, r1, a1, v2, ph2) * da1;
    }

    @Override
    public double eval() {
        return branchVector.i1[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double y = branchVector.y[num];
        double ksi = branchVector.ksi[num];
        double g1 = branchVector.g1[num];
        double b1 = branchVector.b1[num];
        if (variable.equals(v1Var)) {
            return di1dv1(y, ksi, g1, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(v2Var)) {
            return di1dv2(y, ksi, g1, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(ph1Var)) {
            return di1dph1(y, ksi, g1, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(ph2Var)) {
            return di1dph2(y, ksi, g1, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(a1Var)) {
            return di1da1(y, ksi, g1, b1, v1(), ph1(), r1(), a1(), v2(), ph2());
        } else if (variable.equals(r1Var)) {
            throw new IllegalArgumentException("Derivative with respect to r1 not implemented");
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_1";
    }
}
