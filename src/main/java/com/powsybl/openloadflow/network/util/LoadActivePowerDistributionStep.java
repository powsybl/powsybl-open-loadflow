/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LoadActivePowerDistributionStep implements ActivePowerDistribution.Step {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadActivePowerDistributionStep.class);

    private final boolean loadPowerFactorConstant;

    public LoadActivePowerDistributionStep(boolean loadPowerFactorConstant) {
        this.loadPowerFactorConstant = loadPowerFactorConstant;
    }

    @Override
    public String getElementType() {
        return "load";
    }

    @Override
    public List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses) {
        return buses.stream()
                .filter(bus -> bus.getLoad().getOriginalLoadCount() > 0 && bus.isParticipating() && !bus.isDisabled() && !bus.isFictitious())
                .map(bus -> new ParticipatingElement(bus, getParticipationFactor(bus)))
                .collect(Collectors.toList());
    }

    private double getParticipationFactor(LfBus bus) {
        return bus.getLoad().getAbsVariableTargetP();
    }

    @Override
    public double run(List<ParticipatingElement> participatingElements, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // loads might have reach zero and have been discarded.
        ParticipatingElement.normalizeParticipationFactors(participatingElements, "load");

        double done = 0d;
        int modifiedBuses = 0;
        int loadsAtMin = 0;
        Iterator<ParticipatingElement> it = participatingElements.iterator();
        while (it.hasNext()) {
            ParticipatingElement participatingBus = it.next();
            LfBus bus = (LfBus) participatingBus.getElement();
            double factor = participatingBus.getFactor();

            double loadTargetP = bus.getLoadTargetP();
            double diffTargetP = -remainingMismatch * factor;
            double newLoadTargetP = loadTargetP + diffTargetP;

            if (diffTargetP != 0.0) {
                LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                        bus.getId(), loadTargetP * PerUnit.SB, newLoadTargetP * PerUnit.SB);

                if (loadPowerFactorConstant) {
                    ensurePowerFactorConstant(bus, loadTargetP, diffTargetP);
                }

                bus.setLoadTargetP(newLoadTargetP);
                done -= diffTargetP;
                modifiedBuses++;
            }
        }

        LOGGER.debug("{} MW / {} MW distributed at iteration {} to {} buses ({} at min consumption)",
                -done * PerUnit.SB, -remainingMismatch * PerUnit.SB, iteration, modifiedBuses, loadsAtMin);

        return done;
    }

    private static void ensurePowerFactorConstant(LfBus bus, double loadTargetP, double diffTargetP) {
        // if loadPowerFactorConstant is true, when updating targetP on loads,
        // we have to keep the power factor constant by updating targetQ.
        double newLoadTargetQ;
        if (bus.ensurePowerFactorConstantByLoad()) {
            newLoadTargetQ = bus.getLoad().getTargetQ(loadTargetP + diffTargetP);
        } else {
            if (loadTargetP != 0) {
                double powerFactor = bus.getLoadTargetQ() / loadTargetP;
                newLoadTargetQ = bus.getLoadTargetQ() + powerFactor * diffTargetP;
            } else {
                // TODO: find a better implementation, maybe with powerfactor attached to lfBus
                throw new PowsyblException("Load target P equals 0 in ensurePowerFactorConstant - Division by zero");
            }
        }
        if (newLoadTargetQ != bus.getLoadTargetQ()) {
            LOGGER.trace("Rescale '{}' reactive power target on load: {} -> {}",
                    bus.getId(), bus.getLoadTargetQ() * PerUnit.SB, newLoadTargetQ * PerUnit.SB);
            bus.setLoadTargetQ(newLoadTargetQ);
        }
    }
}
