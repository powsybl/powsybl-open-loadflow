/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;
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
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
            for (LfBus bus : context.getNetwork().getBuses()) {
                if (bus.isDiscreteVoltageControlled()) {
                    // switch off regulating transformers
                    bus.getDiscreteVoltageControl().setControlEnabled(false);

                    for (LfBranch controllerBranch : bus.getDiscreteVoltageControl().getControllers()) {
                        // round the rho shift to the closest tap
                        PiModel piModel = controllerBranch.getPiModel();
                        double r1Value = piModel.getR1();
                        piModel.roundR1ToClosestTap();
                        double roundedR1Value = piModel.getR1();
                        LOGGER.trace("Round voltage shift of '{}': {} -> {}", controllerBranch.getId(), r1Value, roundedR1Value);
                    }

                    // if at least one transformer has been switched off wee need to continue
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }
        return status;
    }
}
