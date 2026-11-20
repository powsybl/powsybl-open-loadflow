/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.na.equations.EquationSystem;
import com.powsybl.na.equations.EquationVector;
import com.powsybl.na.equations.TargetVector;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class NoneStateVectorScaling implements StateVectorScaling {

    @Override
    public StateVectorScalingMode getMode() {
        return StateVectorScalingMode.NONE;
    }

    @Override
    public void apply(double[] dx, EquationSystem<AcVariableType, AcEquationType> equationSystem, ReportNode reportNode) {
        // nothing to do
    }

    @Override
    public NewtonRaphsonStoppingCriteria.TestResult applyAfter(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                               EquationVector<AcVariableType, AcEquationType> equationVector,
                                                               TargetVector<AcVariableType, AcEquationType> targetVector,
                                                               NewtonRaphsonStoppingCriteria stoppingCriteria,
                                                               NewtonRaphsonStoppingCriteria.TestResult testResult,
                                                               ReportNode reportNode) {
        return testResult;
    }
}
