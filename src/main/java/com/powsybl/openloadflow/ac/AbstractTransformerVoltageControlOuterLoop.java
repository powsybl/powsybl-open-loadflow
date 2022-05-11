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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractTransformerVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransformerVoltageControlOuterLoop.class);

    protected OuterLoopStatus roundVoltageRatios(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfBranch branch : context.getNetwork().getBranches()) {
            if (branch.isVoltageController() && branch.isVoltageControlEnabled()) {
                branch.setVoltageControlEnabled(false);

                // round the rho shift to the closest tap
                PiModel piModel = branch.getPiModel();
                double r1Value = piModel.getR1();
                piModel.roundR1ToClosestTap();
                double roundedR1Value = piModel.getR1();
                LOGGER.trace("Round voltage ratio of '{}': {} -> {}", branch.getId(), r1Value, roundedR1Value);

                status = OuterLoopStatus.UNSTABLE;
            }
        }
        return status;
    }

    @Override
    public void cleanup(OuterLoopContext context) {
        for (LfBranch branch : context.getNetwork().getBranches()) {
            if (branch.isVoltageController()) {
                branch.setVoltageControlEnabled(false);
            }
        }
    }
}
