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
import net.jafama.FastMath;

import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@SuppressWarnings("squid:S00107")
public class ClosedBranchVectorSide1ReactiveFlowEquationTerm extends AbstractClosedBranchVectorAcFlowEquationTerm {

    public ClosedBranchVectorSide1ReactiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                           VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    public ClosedBranchVectorSide1ReactiveFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                           VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1,
                                                           Fortescue.SequenceType sequenceType) {
        super(branchVector, branchNum, bus1Num, bus2Num, variableSet, deriveA1, deriveR1, sequenceType);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double y = branchVector.y[num];
        double ksi = branchVector.ksi[num];
        double b1 = branchVector.b1[num];
        double v1 = v1();
        double r1 = r1();
        double v2 = v2();
        double theta = theta1(ksi, ph1(), a1(), ph2());
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        double cosKsi = FastMath.cos(ksi);
        return dq1dph1(y, v1, r1, v2, sinTheta) * dph1
                + dq1dph2(y, v1, r1, v2, sinTheta) * dph2
                + dq1dv1(y, cosKsi, b1, v1, r1, v2, cosTheta) * dv1
                + dq1dv2(y, v1, r1, cosTheta) * dv2
                + dq1da1(y, v1, r1, v2, sinTheta) * da1
                + dq1dr1(y, cosKsi, b1, v1, r1, v2, cosTheta) * dr1;
    }

    @Override
    public double eval() {
        return branchVector.q1[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return branchVector.dq1dv1[num];
        } else if (variable.equals(v2Var)) {
            return branchVector.dq1dv2[num];
        } else if (variable.equals(ph1Var)) {
            return branchVector.dq1dph1[num];
        } else if (variable.equals(ph2Var)) {
            return branchVector.dq1dph2[num];
        } else if (variable.equals(a1Var)) {
            return branchVector.dq1da1[num];
        } else if (variable.equals(r1Var)) {
            return branchVector.dq1dr1[num];
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_closed_1";
    }
}
