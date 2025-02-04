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
public abstract class AbstractHvdcAcEmulationActiveFlowEquationTermArrayEvaluator implements EquationTermArray.Evaluator<AcVariableType> {

    protected final AcHvdcVector hvdcVector;

    protected final VariableSet<AcVariableType> variableSet;

    protected AbstractHvdcAcEmulationActiveFlowEquationTermArrayEvaluator(AcHvdcVector hvdcVector, VariableSet<AcVariableType> variableSet) {
        this.hvdcVector = Objects.requireNonNull(hvdcVector);
        this.variableSet = Objects.requireNonNull(variableSet);
    }

    @Override
    public boolean isDisabled(int hvdcNum) {
        return hvdcVector.disabled[hvdcNum];
    }

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int hvdcNum) {
        int bus1Num = hvdcVector.bus1Num[hvdcNum];
        int bus2Num = hvdcVector.bus2Num[hvdcNum];
        return List.of(new Derivative<>(variableSet.getVariable(bus1Num, AcVariableType.BUS_PHI), 0),
                       new Derivative<>(variableSet.getVariable(bus2Num, AcVariableType.BUS_PHI), 1));
    }
}
