/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchAcVariables;
import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

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
        var variables = new ClosedBranchAcVariables(branchNum,
                                                    branchVector.bus1Num[branchNum],
                                                    branchVector.bus2Num[branchNum],
                                                    variableSet,
                                                    branchVector.deriveA1[branchNum],
                                                    branchVector.deriveR1[branchNum],
                                                    Fortescue.SequenceType.POSITIVE)
                .getVariables();
        List<Derivative<AcVariableType>> derivatives = new ArrayList<>(variables.size());
        for (int localIndex = 0; localIndex < variables.size(); localIndex++) {
            var variable = variables.get(localIndex);
            derivatives.add(new Derivative<>(variable, localIndex));
        }
        return derivatives;
    }
}
