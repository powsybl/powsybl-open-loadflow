/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.AbstractDiscreteVoltageControl;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.ShuntVoltageControl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class ShuntVoltageControlOuterLoop implements OuterLoop {

    @Override
    public String getType() {
        return "Shunt voltage control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {

            List<ShuntVoltageControl> shuntVoltageControls = context.getNetwork().getBuses().stream()
                    .flatMap(bus -> bus.getShuntVoltageControl().filter(vc -> bus.isShuntVoltageControlled()).stream())
                    .collect(Collectors.toList());

            shuntVoltageControls.forEach(this::switchOffVoltageControl);

            // if at least one shunt has been switched off wee need to continue

            return shuntVoltageControls.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
        }
        return status;
    }

    private void switchOffVoltageControl(ShuntVoltageControl vc) {

        for (LfBus controllerBus : vc.getControllers()) {
            // round the rho shift to the closest tap
            Optional<LfShunt> shunt = controllerBus.getControllerShunt();
            double bToDispatch = shunt.get().getB();
            shunt.get().dispatchB(bToDispatch);
        }

        vc.setMode(AbstractDiscreteVoltageControl.Mode.OFF);
    }
}
