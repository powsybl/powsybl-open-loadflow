/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop extends AbstractLimitsOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    @Override
    public String getType() {
        return "Reactive limits";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<PvToPqBus> pvToPqBuses = new ArrayList<>();
        List<PqToPvBus> pqToPvBuses = new ArrayList<>();
        MutableInt remainingPvBusCount = new MutableInt();
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.isVoltageControlEnabled() && !bus.isDisabled()) {
                checkPvBus(bus, pvToPqBuses, remainingPvBusCount);
            } else if (bus.hasVoltageControllerCapability() && !bus.isDisabled()) {
                if (!bus.hasGeneratorsWithSlope()) {
                    checkPqBus(bus, pqToPvBuses);
                } else {
                    // we don't support switching PQ to PV for bus with one controller with slope.
                    LOGGER.warn("Controller bus '{}' wants to control back voltage with slope: not supported", bus.getId());
                }
            }
        }

        if (!pvToPqBuses.isEmpty() && switchPvPq(pvToPqBuses, remainingPvBusCount.intValue(), reporter)) {
            status = OuterLoopStatus.UNSTABLE;
        }
        if (!pqToPvBuses.isEmpty() && switchPqPv(pqToPvBuses, reporter)) {
            status = OuterLoopStatus.UNSTABLE;
        }

        return status;
    }
}
