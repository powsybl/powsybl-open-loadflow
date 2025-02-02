/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.VariableSet;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator extends AbstractClosedBranchEquationTermArrayEvaluator {

    public ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator(AcBranchVector branchVector, AcBusVector busVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, busVector, variableSet);
    }

    @Override
    public String getName() {
        return "ac_p_array_closed_1";
    }

    protected double calculateSensi(int branchNum, double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double y = branchVector.y[branchNum];
        double ksi = branchVector.ksi[branchNum];
        double g1 = branchVector.g1[branchNum];
        double v1 = busVector.v[branchVector.bus1Num[branchNum]];
        double v2 = busVector.v[branchVector.bus2Num[branchNum]];
        double ph1 = busVector.ph[branchVector.bus1Num[branchNum]];
        double ph2 = busVector.ph[branchVector.bus2Num[branchNum]];
        double a1 = branchVector.a1[branchNum];
        double r1 = branchVector.r1[branchNum];
        return ClosedBranchSide1ActiveFlowEquationTerm.calculateSensi(g1, y, ksi, v1, ph1, a1, r1, v2, ph2,
                dph1, dph2, dv1, dv2, da1, dr1);
    }

    @Override
    public double[] eval() {
        return branchVector.p1;
    }

    @Override
    public double eval(int branchNum) {
        return branchVector.p1[branchNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            branchVector.dp1dv1,
            branchVector.dp1dv2,
            branchVector.dp1dph1,
            branchVector.dp1dph2,
            branchVector.dp1da1,
            branchVector.dp1dr1
        };
    }
}
