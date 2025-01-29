/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.VariableSet;
import gnu.trove.list.array.TIntArrayList;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator extends AbstractBranchEquationTermArrayEvaluator {

    public ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, variableSet);
    }

    @Override
    public double[] eval(TIntArrayList branchNums) {
        double[] values = new double[branchNums.size()];
        for (int i = 0; i < branchNums.size(); i++) {
            int branchNum = branchNums.getQuick(i);
            values[branchNum] = branchVector.p1[branchNum];
        }
        return values;
    }

    @Override
    public double[] evalDer(TIntArrayList branchNums) {
        int derivativeCount = AcBranchDerivativeType.values().length;
        double[] values = new double[branchVector.getSize() * derivativeCount];
        for (int i = 0; i < branchNums.size(); i++) {
            int branchNum = branchNums.getQuick(i);
            values[branchNum * derivativeCount] = branchVector.dp1dv1[branchNum];
            values[branchNum * derivativeCount + 1] = branchVector.dp1dv2[branchNum];
            values[branchNum * derivativeCount + 2] = branchVector.dp1dph1[branchNum];
            values[branchNum * derivativeCount + 3] = branchVector.dp1dph2[branchNum];
            if (branchVector.deriveA1[branchNum]) {
                values[branchNum * derivativeCount + 4] = branchVector.dp1da1[branchNum];
            }
            if (branchVector.deriveR1[branchNum]) {
                values[branchNum * derivativeCount + 5] = branchVector.dp1dr1[branchNum];
            }
        }
        return values;
    }
}
