/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.TargetVector;

import java.util.Objects;

/**
 * State vector scaling.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface StateVectorScaling {

    static StateVectorScaling fromMode(NewtonRaphsonParameters parameters, NewtonRaphsonStoppingCriteria.TestResult initialTestResult) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(initialTestResult);
        return switch (parameters.getStateVectorScalingMode()) {
            case NONE -> new NoneStateVectorScaling();
            case LINE_SEARCH -> new LineSearchStateVectorScaling(initialTestResult, parameters.getLineSearchStateVectorScalingMaxIteration(), parameters.getLineSearchStateVectorScalingStepFold());
            case MAX_VOLTAGE_CHANGE -> new MaxVoltageChangeStateVectorScaling(parameters.getMaxVoltageChangeStateVectorScalingMaxDv(), parameters.getMaxVoltageChangeStateVectorScalingMaxDphi());
        };
    }

    StateVectorScalingMode getMode();

    /**
     * Apply scaling to state vector variation before equation mismatches calculation.
     */
    void apply(double[] dx, EquationSystem<AcVariableType, AcEquationType> equationSystem, ReportNode reportNode);

    /**
     * Apply scaling to state vector after equation mismatches and norm have been calculated.
     */
    NewtonRaphsonStoppingCriteria.TestResult applyAfter(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                        EquationVector<AcVariableType, AcEquationType> equationVector,
                                                        TargetVector<AcVariableType, AcEquationType> targetVector,
                                                        NewtonRaphsonStoppingCriteria stoppingCriteria,
                                                        NewtonRaphsonStoppingCriteria.TestResult testResult,
                                                        ReportNode reportNode);
}
