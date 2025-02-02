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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractClosedBranchEquationTermArrayEvaluator extends AbstractBranchEquationTermArrayEvaluator {

    protected AbstractClosedBranchEquationTermArrayEvaluator(AcBranchVector branchVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, variableSet);
    }

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int branchNum) {
        int bus1Num = branchVector.bus1Num[branchNum];
        int bus2Num = branchVector.bus2Num[branchNum];
        boolean deriveA1 = branchVector.deriveA1[branchNum];
        boolean deriveR1 = branchVector.deriveR1[branchNum];
        List<Derivative<AcVariableType>> derivatives = new ArrayList<>(6);
        derivatives.add(new Derivative<>(variableSet.getVariable(bus1Num, AcVariableType.BUS_V), 0));
        derivatives.add(new Derivative<>(variableSet.getVariable(bus2Num, AcVariableType.BUS_V), 1));
        derivatives.add(new Derivative<>(variableSet.getVariable(bus1Num, AcVariableType.BUS_PHI), 2));
        derivatives.add(new Derivative<>(variableSet.getVariable(bus2Num, AcVariableType.BUS_PHI), 3));
        if (deriveA1) {
            derivatives.add(new Derivative<>(variableSet.getVariable(branchNum, AcVariableType.BRANCH_ALPHA1), 4));
        }
        if (deriveR1) {
            derivatives.add(new Derivative<>(variableSet.getVariable(branchNum, AcVariableType.BRANCH_RHO1), 5));
        }
        return derivatives;
    }
}
