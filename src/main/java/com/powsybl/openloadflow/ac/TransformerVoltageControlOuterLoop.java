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
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

        if (context.getIteration() == 0) {
            List<DiscreteVoltageControl> voltageControlsOn = context.getNetwork().getBuses().stream()
                .map(bus -> bus.getDiscreteVoltageControl().filter(dpc -> bus.isDiscreteVoltageControlled()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

            // switch off regulating transformers
            voltageControlsOn.forEach(this::switchOffVoltageControl);

            // if at least one transformer has been switched off wee need to continue
            return voltageControlsOn.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
        }

        return OuterLoopStatus.STABLE;
    }

    private void switchOffVoltageControl(DiscreteVoltageControl dvc) {
        dvc.setMode(DiscreteVoltageControl.Mode.OFF);

        for (LfBranch controllerBranch : dvc.getControllers()) {
            // round the rho shift to the closest tap
            PiModel piModel = controllerBranch.getPiModel();
            double r1Value = piModel.getR1();
            piModel.roundR1ToClosestTap();
            double roundedR1Value = piModel.getR1();
            LOGGER.trace("Round voltage shift of '{}': {} -> {}", controllerBranch.getId(), r1Value, roundedR1Value);
        }
    }
}
