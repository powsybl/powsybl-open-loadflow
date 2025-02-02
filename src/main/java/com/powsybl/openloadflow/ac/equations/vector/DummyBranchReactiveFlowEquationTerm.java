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

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@SuppressWarnings("squid:S00107")
public class DummyBranchReactiveFlowEquationTerm extends AbstractBranchVectorAcFlowEquationTerm {

    private final Variable<AcVariableType> dummyQVar;

    public DummyBranchReactiveFlowEquationTerm(AcBranchVector branchVector, int branchNum,
                                               VariableSet<AcVariableType> variableSet) {
        super(branchVector, branchNum);
        dummyQVar = variableSet.getVariable(branchNum, AcVariableType.DUMMY_Q);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return List.of(dummyQVar);
    }

    @Override
    public double eval() {
        return branchVector.dummyQ[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(dummyQVar)) {
            return 1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_dummy_q";
    }
}
