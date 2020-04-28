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
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PhaseControl;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PhaseControlOuterLoop implements OuterLoop {

    @Override
    public String getType() {
        return "Phase control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        if (context.getIteration() == 0) {
            // at first iteration all branches controlling phase are switched off
            for (LfBranch branch : context.getNetwork().getBranches()) {
                PhaseControl phaseControl = branch.getPhaseControl().orElse(null);
                if (phaseControl != null && phaseControl.getMode() == PhaseControl.Mode.CONTROLLER) {
                    // switch off phase shifter
                    phaseControl.setMode(PhaseControl.Mode.OFF);

                    // de-activate a1 variable for next outer loop run
                    Variable a1 = context.getVariableSet().getVariable(branch.getNum(), VariableType.BRANCH_ALPHA1);
                    a1.setActive(false);

                    // round the phase shift to the closest tap
                    double roundedA1 = phaseControl.findClosestA1(branch.getPiModel().getA1());
                    branch.getPiModel().setA1(roundedA1);
                }
            }
            return OuterLoopStatus.UNSTABLE;
        }
        return OuterLoopStatus.STABLE;
    }
}
