/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.VariableSet;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator extends AbstractBranchEquationTermArrayEvaluator {

    public ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, variableSet);
    }

    @Override
    public double[] eval() {
        return branchVector.q1;
    }

    @Override
    public double eval(int branchNum) {
        return branchVector.q1[branchNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            branchVector.dq1dv1,
            branchVector.dq1dv2,
            branchVector.dq1dph1,
            branchVector.dq1dph2,
            branchVector.dq1da1,
            branchVector.dq1dr1
        };
    }
}
