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
import com.powsybl.openloadflow.network.DiscretePhaseControl;
import com.powsybl.openloadflow.network.LfBranch;
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
        if (context.getIteration() == 0) {
            // at first outer loop iteration:
            // branches with active power control are switched off and taps are rounded
            // branches with current limiter control will wait for second iteration
            return  firstIteration(context);
        } else if (context.getIteration() > 0) {
            // at second outer loop iteration:
            // flow of branches with fixed tap are recomputed
            return nextIteration(context);
        }
        return OuterLoopStatus.STABLE;
    }

    private OuterLoopStatus firstIteration(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfBranch branch : context.getNetwork().getBranches()) {
            if (branch.isPhaseControlled()) {
                switch (branch.getDiscretePhaseControl().getMode()) {
                    case CONTROLLER:
                        // all branches with active power control are switched off
                        switchOffPhaseControl(branch);
                        // if at least one phase shifter has been switched off we need to continue
                        status = OuterLoopStatus.UNSTABLE;
                        break;
                    case LIMITER:
                        status = OuterLoopStatus.UNSTABLE;
                        break;
                    default:
                        // nothing has to be done
                }
            }
        }
        return status;
    }

    private OuterLoopStatus nextIteration(OuterLoopContext context) {
        // at second outer loop iteration we switch on phase control for branches that are in limiter mode
        // and a current greater than the limit
        for (LfBranch branch : context.getNetwork().getBranches()) {
            if (branch.isPhaseControlled()) {
                switch (branch.getDiscretePhaseControl().getMode()) {
                    case LIMITER:
                        // TODO
                        LOGGER.warn("Phase shifter in limiter mode not yet implemented");
                    default:
                        // nothing has to be done
                }
            }
        }
        return OuterLoopStatus.STABLE;
    }

    private void switchOffPhaseControl(LfBranch branch) {
        // switch off phase control
        branch.getDiscretePhaseControl().setMode(DiscretePhaseControl.Mode.OFF);

        // round the phase shift to the closest tap
        LfBranch controllerBranch = branch.getDiscretePhaseControl().getController();
        PiModel piModel = controllerBranch.getPiModel();
        double a1Value = piModel.getA1();
        piModel.roundA1ToClosestTap();
        double roundedA1Value = piModel.getA1();
        LOGGER.info("Round phase shift of '{}': {} -> {}", controllerBranch.getId(), a1Value, roundedA1Value);
    }
}
