/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class BranchDummyReactivePowerEquationTermArrayEvaluator extends AbstractBranchEquationTermArrayEvaluator {

    private final boolean neg;

    public BranchDummyReactivePowerEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet,
                                                              boolean neg) {
        super(branchVector, variableSet);
        this.neg = neg;
    }

    @Override
    public String getName() {
        return neg ? "ac_neg_q_array_dummy" : "ac_q_array_dummy";
    }

    @Override
    public double[] eval() {
        return neg ? branchVector.negDummyQ : branchVector.dummyQ;
    }

    @Override
    public double eval(int branchNum) {
        return neg ? branchVector.negDummyQ[branchNum] : branchVector.dummyQ[branchNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            neg ? branchVector.derNegDummyQ : branchVector.derDummyQ
        };
    }

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int branchNum) {
        return List.of(new Derivative<>(variableSet.getVariable(branchNum, AcVariableType.DUMMY_Q), 0));
    }
}
