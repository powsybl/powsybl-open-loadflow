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
public class LoadModelActiveFlowEquationTermArrayEvaluator extends AbstractLoadModelEquationTermArrayEvaluator {

    public LoadModelActiveFlowEquationTermArrayEvaluator(AcLoadVector loadVector, AcBusVector busVector, VariableSet<AcVariableType> variableSet) {
        super(loadVector, busVector, variableSet);
    }

    @Override
    public String getName() {
        return "ac_load_p_array";
    }

    @Override
    public double[] eval() {
        return loadVector.pLoadModel;
    }

    @Override
    public double eval(int busNum) {
        return loadVector.pLoadModel[busNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            loadVector.dpdvLoadModel
        };
    }
}
