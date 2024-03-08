/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.TargetVector;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NoneStateVectorScaling implements StateVectorScaling {

    @Override
    public StateVectorScalingMode getMode() {
        return StateVectorScalingMode.NONE;
    }

    @Override
    public void apply(double[] dx, EquationSystem<AcVariableType, AcEquationType> equationSystem, Reporter reporter) {
        // nothing to do
    }

    @Override
    public NewtonRaphsonStoppingCriteria.TestResult applyAfter(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                               EquationVector<AcVariableType, AcEquationType> equationVector,
                                                               TargetVector<AcVariableType, AcEquationType> targetVector,
                                                               NewtonRaphsonStoppingCriteria stoppingCriteria,
                                                               NewtonRaphsonStoppingCriteria.TestResult testResult,
                                                               Reporter reporter) {
        return testResult;
    }
}
