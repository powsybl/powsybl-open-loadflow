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
import com.powsybl.openloadflow.network.DiscreteVoltageControl;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
                Optional<DiscreteVoltageControl> vc = bus.getDiscreteVoltageControl().filter(vc0 -> bus.isDiscreteVoltageControlled());
                if (vc.isPresent()) {
                    // switch off regulating transformers
                    vc.get().setMode(DiscreteVoltageControl.Mode.OFF);

                    for (LfBranch controllerBranch : vc.get().getControllers()) {
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
