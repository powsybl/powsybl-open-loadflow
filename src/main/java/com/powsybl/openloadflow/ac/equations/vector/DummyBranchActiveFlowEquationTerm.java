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
public class DummyBranchActiveFlowEquationTerm extends AbstractBranchVectorAcFlowEquationTerm {

    private final Variable<AcVariableType> dummyPVar;

    public DummyBranchActiveFlowEquationTerm(AcBranchVector branchVector, int branchNum,
                                             VariableSet<AcVariableType> variableSet) {
        super(branchVector, branchNum);
        dummyPVar = variableSet.getVariable(branchNum, AcVariableType.DUMMY_P);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return List.of(dummyPVar);
    }

    @Override
    public double eval() {
        return branchVector.dummyP[num];
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(dummyPVar)) {
            return 1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_dummy_p";
    }
}
