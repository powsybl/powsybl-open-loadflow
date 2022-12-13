/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.TargetVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageMagnitudeStateVectorRescaler implements StateVectorRescaler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageMagnitudeStateVectorRescaler.class);

    private static final double DEFAULT_MAX_DV = 0.1;

    private final double maxDv;

    public VoltageMagnitudeStateVectorRescaler() {
        this(DEFAULT_MAX_DV);
    }

    public VoltageMagnitudeStateVectorRescaler(double maxDv) {
        this.maxDv = maxDv;
    }

    @Override
    public void rescale(double[] dx, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        int vCutCount = 0;
        for (var variable : equationSystem.getIndex().getSortedVariablesToFind()) {
            double value = dx[variable.getRow()];
            if (variable.getType() == AcVariableType.BUS_V && value > maxDv) {
                dx[variable.getRow()] = maxDv;
                vCutCount++;
            }
        }
        if (vCutCount > 0) {
            LOGGER.debug("{} voltage magnitudes have been cut", vCutCount);
        }
    }

    @Override
    public NewtonRaphsonStoppingCriteria.TestResult rescaleAfter(StateVector stateVector,
                                                                 EquationVector<AcVariableType, AcEquationType> equationVector,
                                                                 TargetVector<AcVariableType, AcEquationType> targetVector,
                                                                 NewtonRaphsonStoppingCriteria stoppingCriteria,
                                                                 NewtonRaphsonStoppingCriteria.TestResult testResult) {
        // nothing to do
        return testResult;
    }
}
