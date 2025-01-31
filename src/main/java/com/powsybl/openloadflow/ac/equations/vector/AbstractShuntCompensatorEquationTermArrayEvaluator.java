/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.EquationTermArray;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractShuntCompensatorEquationTermArrayEvaluator implements EquationTermArray.Evaluator<AcVariableType> {

    protected final AcShuntVector shuntVector;

    protected final VariableSet<AcVariableType> variableSet;

    protected AbstractShuntCompensatorEquationTermArrayEvaluator(AcShuntVector shuntVector, VariableSet<AcVariableType> variableSet) {
        this.shuntVector = Objects.requireNonNull(shuntVector);
        this.variableSet = Objects.requireNonNull(variableSet);
    }

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int shuntNum) {
        return List.of(new Derivative<>(variableSet.getVariable(shuntVector.busNum[shuntNum], AcVariableType.BUS_V), 0));
    }
}
