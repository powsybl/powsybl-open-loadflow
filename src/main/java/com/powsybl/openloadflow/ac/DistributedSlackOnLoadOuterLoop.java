/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class DistributedSlackOnLoadOuterLoop extends AbstractDistributedSlackOuterLoop<LfBus> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOnLoadOuterLoop.class);

    @Override
    public String getName() {
        return "Distributed slack on load";
    }

    @Override
    protected List<ParticipatingElement<LfBus>> getParticipatingElements(LfNetwork network) {
        return network.getBuses()
                .stream()
                .filter(bus -> bus.getLoadCount() > 0 && bus.getLoadTargetP() > 0)
                .map(bus -> new ParticipatingElement<>(bus, bus.getLoadTargetP()))
                .collect(Collectors.toList());
    }

    @Override
    protected double run(List<ParticipatingElement<LfBus>> participatingElements, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // loads might have reach zero and have been discarded.
        normalizeParticipationFactors(participatingElements, "load");

        double done = 0d;
        int modifiedBuses = 0;
        int loadsAtMin = 0;
        Iterator<ParticipatingElement<LfBus>> it = participatingElements.iterator();
        while (it.hasNext()) {
            ParticipatingElement<LfBus> participatingBus = it.next();
            LfBus bus = participatingBus.element;
            double factor = participatingBus.factor;

            double targetP = bus.getLoadTargetP();

            double newTargetP = targetP - remainingMismatch * factor;

            // We stop when the load produces power.
            if (newTargetP <= 0) {
                newTargetP = 0;
                loadsAtMin++;
                it.remove();
            }

            if (newTargetP != targetP) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                            bus.getId(), targetP * PerUnit.SB, newTargetP * PerUnit.SB);
                }

                bus.setLoadTargetP(newTargetP);
                done += targetP - newTargetP;
                modifiedBuses++;
            }
        }

        LOGGER.debug("{} MW / {} MW distributed at iteration {} to {} loads ({} at min consumption)",
                done * PerUnit.SB, -remainingMismatch * PerUnit.SB, iteration, modifiedBuses, loadsAtMin);

        return done;
    }
}
