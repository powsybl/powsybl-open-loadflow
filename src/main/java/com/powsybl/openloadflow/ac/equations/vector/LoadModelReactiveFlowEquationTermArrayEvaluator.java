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
public class LoadModelReactiveFlowEquationTermArrayEvaluator implements EquationTermArray.Evaluator<AcVariableType> {

    private final AcLoadVector loadVector;

    private final AcBusVector busVector;

    private final VariableSet<AcVariableType> variableSet;

    public LoadModelReactiveFlowEquationTermArrayEvaluator(AcLoadVector loadVector, AcBusVector busVector, VariableSet<AcVariableType> variableSet) {
        this.loadVector = Objects.requireNonNull(loadVector);
        this.busVector = Objects.requireNonNull(busVector);
        this.variableSet = Objects.requireNonNull(variableSet);
    }

    @Override
    public String getName() {
        return "ac_load_q_array";
    }

    @Override
    public boolean isDisabled(int loadNum) {
        return busVector.disabled[loadVector.busNum[loadNum]];
    }

    @Override
    public double[] eval() {
        return loadVector.qLoadModel;
    }

    @Override
    public double eval(int busNum) {
        return loadVector.qLoadModel[busNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            loadVector.dqdvLoadModel
        };
    }

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int busNum) {
        return List.of(new Derivative<>(variableSet.getVariable(busNum, AcVariableType.BUS_V), 0));
    }
}
