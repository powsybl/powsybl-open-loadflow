/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class TransformerVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerVoltageControlOuterLoop.class);

    @Override
    public String getType() {
        return "Transformer voltage control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
           // at first iteration all branches controlling voltage are switched off
            for (LfBranch branch : context.getNetwork().getBranches()) {
                VoltageControl voltageControl = branch.getVoltageControl().orElse(null);
                if (voltageControl != null && voltageControl.getMode() == VoltageControl.Mode.VOLTAGE) {
                    // switch off regulating transformers
                    voltageControl.setMode(VoltageControl.Mode.OFF);

                    // de-activate r1 variable for next outer loop run
                    Variable r1 = context.getVariableSet().getVariable(branch.getNum(), VariableType.BRANCH_RHO1);
                    r1.setActive(false);

                    // de-activate transformer voltage control equation
                    Equation t = context.getEquationSystem().createEquation(branch.getNum(), EquationType.BUS_TRANSFO_V);
                    t.setActive(false);

                    // round the rho shift to the closest tap
                    PiModel piModel = branch.getPiModel();
                    double r1Value = piModel.getR1();
                    piModel.roundR1ToClosestTap();
                    double roundedR1Value = piModel.getR1();
                    LOGGER.info("Round voltage shift of '{}': {} -> {}", branch.getId(), r1Value, roundedR1Value);

                    // if at least one transformer has been switched off wee need to continue
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }

        return status;
    }
}
