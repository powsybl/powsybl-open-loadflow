/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PhaseControl;
import com.powsybl.openloadflow.network.PiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PhaseControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseControlOuterLoop.class);

    @Override
    public String getType() {
        return "Phase control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
            // at first iteration all branches controlling phase are switched off
            for (LfBranch branch : context.getNetwork().getBranches()) {
                if (branch.isPhaseControlled()) {
                    PhaseControl phaseControl = branch.getPhaseControl();
                    LfBranch controllerBranch = phaseControl.getController();
                    if (phaseControl.getMode() == PhaseControl.Mode.CONTROLLER) {
                        // switch off phase shifter
                        phaseControl.setMode(PhaseControl.Mode.OFF);

                        // de-activate a1 variable for next outer loop run
                        Variable a1 = context.getVariableSet().getVariable(controllerBranch.getNum(), VariableType.BRANCH_ALPHA1);
                        a1.setActive(false);

                        // de-activate phase control equation
                        Equation t = context.getEquationSystem().createEquation(branch.getNum(), EquationType.BRANCH_P);
                        t.setActive(false);

                        // round the phase shift to the closest tap
                        PiModel piModel = controllerBranch.getPiModel();
                        double a1Value = piModel.getA1();
                        piModel.roundA1ToClosestTap();
                        double roundedA1Value = piModel.getA1();
                        LOGGER.info("Round phase shift of '{}': {} -> {}", controllerBranch.getId(), a1Value, roundedA1Value);

                        // if at least one phase shifter has been switched off wee need to continue
                        status = OuterLoopStatus.UNSTABLE;
                    }
                }
            }
        } else if (context.getIteration() == 1) {
            // at second iteration we switch on phase control for branches that are in limiter mode
            // and a current greater than the limit
            for (LfBranch branch : context.getNetwork().getBranches()) {
                if (branch.isPhaseControlled()) {
                    PhaseControl phaseControl = branch.getPhaseControl();
                    if (phaseControl.getMode() == PhaseControl.Mode.LIMITER) {
                        // TODO
                        LOGGER.warn("Phase shifter in limiter mode not yet implemented");
                    }
                }
            }
        }

        return status;
    }
}
