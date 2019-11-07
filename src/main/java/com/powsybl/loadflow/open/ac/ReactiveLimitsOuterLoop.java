/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.ac;

import com.powsybl.loadflow.open.ac.outerloop.OuterLoop;
import com.powsybl.loadflow.open.ac.outerloop.OuterLoopContext;
import com.powsybl.loadflow.open.ac.outerloop.OuterLoopStatus;
import com.powsybl.loadflow.open.equations.Equation;
import com.powsybl.loadflow.open.equations.EquationSystem;
import com.powsybl.loadflow.open.equations.EquationType;
import com.powsybl.loadflow.open.network.LfBus;
import com.powsybl.loadflow.open.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    @Override
    public String getName() {
        return "Reactive limits";
    }

    private void switchPvPq(LfBus bus, EquationSystem equationSystem, double newGenerationTargetQ) {
        Equation vEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V);
        Equation qEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
        vEq.setActive(false);
        qEq.setActive(true);
        bus.setGenerationTargetQ(newGenerationTargetQ);
        bus.setVoltageControl(false);
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        List<LfBus> pvToPqBuses = new ArrayList<>();
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.hasVoltageControl()) { // PV bus
                double q = bus.getCalculatedQ() + bus.getLoadTargetQ();
                double minQ = bus.getMinQ();
                double maxQ = bus.getMaxQ();
                if (q < minQ) {
                    // switch PV -> PQ
                    switchPvPq(bus, context.getEquationSystem(), minQ);
                    LOGGER.trace("Switch bus {} PV -> PQ, q={} < minQ={}", bus.getId(), q * PerUnit.SB, minQ * PerUnit.SB);
                    pvToPqBuses.add(bus);
                    status = OuterLoopStatus.UNSTABLE;
                } else if (q > maxQ) {
                    // switch PV -> PQ
                    switchPvPq(bus, context.getEquationSystem(), maxQ);
                    LOGGER.trace("Switch bus {} PV -> PQ, q={} MVar > maxQ={}", bus.getId(), q * PerUnit.SB, maxQ * PerUnit.SB);
                    pvToPqBuses.add(bus);
                    status = OuterLoopStatus.UNSTABLE;
                }
            } else { // PQ bus
                // TODO
            }
        }
        if (!pvToPqBuses.isEmpty()) {
            LOGGER.debug("{} buses switched PV -> PQ", pvToPqBuses.size());
        }
        return status;
    }
}
