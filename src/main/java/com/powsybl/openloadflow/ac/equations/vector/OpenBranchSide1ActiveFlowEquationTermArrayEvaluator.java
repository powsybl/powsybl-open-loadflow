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
public class OpenBranchSide1ActiveFlowEquationTermArrayEvaluator extends AbstractOpenSide1BranchEquationTermArrayEvaluator {

    public OpenBranchSide1ActiveFlowEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, variableSet);
    }

    @Override
    public String getName() {
        return "ac_p_array_open_1";
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
            branchVector.dp2dv2,
        };
    }
}
