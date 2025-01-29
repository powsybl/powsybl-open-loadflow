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
public class ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator extends AbstractBranchEquationTermArrayEvaluator {

    public ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, variableSet);
    }

    @Override
    public double[] eval() {
        return branchVector.p2;
    }

    @Override
    public double eval(int branchNum) {
        return branchVector.p2[branchNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            branchVector.dp2dv1,
            branchVector.dp2dv2,
            branchVector.dp2dph1,
            branchVector.dp2dph2,
            branchVector.dp2da1,
            branchVector.dp2dr1
        };
    }
}
