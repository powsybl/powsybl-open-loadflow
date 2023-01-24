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
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractTransformerVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransformerVoltageControlOuterLoop.class);

    private static final double MIN_TARGET_DEADBAND_KV = 0.1; // kV

    protected static List<LfBranch> getControllerBranches(LfNetwork network) {
        return network.getBranches()
                .stream().filter(branch -> !branch.isDisabled() && branch.isVoltageController())
                .collect(Collectors.toList());
    }

    protected OuterLoopStatus roundVoltageRatios(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        for (LfBranch controllerBranch : getControllerBranches(context.getNetwork())) {
            controllerBranch.setVoltageControlEnabled(false);

            // round the rho shift to the closest tap
            PiModel piModel = controllerBranch.getPiModel();
            double r1Value = piModel.getR1();
            piModel.roundR1ToClosestTap();
            double roundedR1Value = piModel.getR1();
            LOGGER.trace("Round voltage ratio of '{}': {} -> {}", controllerBranch.getId(), r1Value, roundedR1Value);

            status = OuterLoopStatus.UNSTABLE;
        }
        return status;
    }

    protected static double getHalfTargetDeadband(TransformerVoltageControl voltageControl) {
        return voltageControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_KV / voltageControl.getControlled().getNominalV()) / 2;
    }
}
