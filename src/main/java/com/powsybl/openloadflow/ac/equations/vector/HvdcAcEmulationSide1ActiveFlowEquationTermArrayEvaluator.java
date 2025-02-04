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
@SuppressWarnings("squid:S00107")
public class HvdcAcEmulationSide1ActiveFlowEquationTermArrayEvaluator extends AbstractHvdcAcEmulationActiveFlowEquationTermArrayEvaluator {

    public HvdcAcEmulationSide1ActiveFlowEquationTermArrayEvaluator(AcHvdcVector hvdcVector, VariableSet<AcVariableType> variableSet) {
        super(hvdcVector, variableSet);
    }

    @Override
    public String getName() {
        return "ac_emulation_p_array_1";
    }

    @Override
    public double[] eval() {
        return hvdcVector.p1;
    }

    @Override
    public double eval(int hvdcNum) {
        return hvdcVector.p1[hvdcNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            hvdcVector.dp1dph1,
            hvdcVector.dp1dph2
        };
    }
}
