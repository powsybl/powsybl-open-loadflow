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
import com.powsybl.openloadflow.network.DiscreteVoltageControl;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class ShuntVoltageControlOuterLoop implements OuterLoop {

    @Override
    public String getType() {
        return "Shunt voltage control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
            for (LfBus bus : context.getNetwork().getBuses()) {

                if (bus.isDiscreteVoltageControlled() && bus.getDiscreteVoltageControl().getMode() == DiscreteVoltageControl.Mode.VOLTAGE_SHUNT) {
                    // switch off regulating shunts
                    bus.getDiscreteVoltageControl().setMode(DiscreteVoltageControl.Mode.OFF);

                    // if at least one shunt has been switched off wee need to continue
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }
        return status;
    }
}
