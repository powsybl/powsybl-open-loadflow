/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
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
public class ShuntCompensatorReactiveFlowEquationTermArrayEvaluator extends AbstractShuntCompensatorEquationTermArrayEvaluator {

    public ShuntCompensatorReactiveFlowEquationTermArrayEvaluator(AcShuntVector shuntVector, VariableSet<AcVariableType> variableSet) {
        super(shuntVector, variableSet);
    }

    @Override
    public String getName() {
        return "ac_q_array_shunt";
    }

    @Override
    public double[] eval() {
        return shuntVector.q;
    }

    @Override
    public double eval(int shuntNum) {
        return shuntVector.q[shuntNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            shuntVector.dqdv
        };
    }
}
