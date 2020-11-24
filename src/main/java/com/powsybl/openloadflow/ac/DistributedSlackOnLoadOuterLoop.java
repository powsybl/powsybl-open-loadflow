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

    private final boolean distributedSlackOnConformLoad;
    private final boolean remainsLoadPowerFactorConstant;

    public DistributedSlackOnLoadOuterLoop(boolean throwsExceptionInCaseOfFailure, boolean distributedSlackOnConformLoad, boolean remainsLoadPowerFactorConstant) {
        super(throwsExceptionInCaseOfFailure);
        this.distributedSlackOnConformLoad = distributedSlackOnConformLoad;
        this.remainsLoadPowerFactorConstant = remainsLoadPowerFactorConstant;
    }

    @Override
    public String getType() {
        return "Distributed slack on load";
    }

    @Override
    public List<ParticipatingElement<LfBus>> getParticipatingElements(LfNetwork network) {
        return network.getBuses()
                .stream()
                .filter(bus -> bus.getPositiveLoadCount() > 0 && getVariableLoadTargetP(bus) > 0)
                .map(bus -> new ParticipatingElement<>(bus, getVariableLoadTargetP(bus)))
                .collect(Collectors.toList());
    }

    private double getVariableLoadTargetP(LfBus bus) {
        return distributedSlackOnConformLoad ? bus.getLoadTargetP() - bus.getFixedLoadTargetP() : bus.getLoadTargetP();
    }

    private double getVariableLoadTargetQAgainstVariableLoadTargetP(LfBus bus) {
        return distributedSlackOnConformLoad ? (bus.getLoadTargetQ() - bus.getFixedLoadTargetQ()) / (bus.getLoadTargetP() - bus.getFixedLoadTargetP())
                : bus.getLoadTargetQ() / bus.getLoadTargetP();
    }

    @Override
    public double run(List<ParticipatingElement<LfBus>> participatingElements, int iteration, double remainingMismatch) {
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

            double loadTargetP = bus.getLoadTargetP();
            double newLoadTargetP = loadTargetP - remainingMismatch * factor;

            // We stop when the load produces power.
            if (newLoadTargetP <= 0) {
                newLoadTargetP = 0;
                loadsAtMin++;
                it.remove();
            }

            if (newLoadTargetP != loadTargetP) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                            bus.getId(), loadTargetP * PerUnit.SB, newLoadTargetP * PerUnit.SB);
                }

                if (remainsLoadPowerFactorConstant) {
                    // if remainsLoadPowerFactorConstant is true, when updating targetP on loads,
                    // we have to keep the power factor constant by updating targetQ.
                    double loadTargetQ = bus.getLoadTargetQ();
                    double newLoadTargetQ;
                    newLoadTargetQ = distributedSlackOnConformLoad ? bus.getFixedLoadTargetQ() + getVariableLoadTargetQAgainstVariableLoadTargetP(bus) * (newLoadTargetP - bus.getFixedLoadTargetP())
                            : newLoadTargetP * getVariableLoadTargetQAgainstVariableLoadTargetP(bus);
                    if (newLoadTargetQ != loadTargetQ) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Rescale '{}' reactive power target on load: {} -> {}",
                                    bus.getId(), loadTargetQ * PerUnit.SB, newLoadTargetQ * PerUnit.SB);
                        }
                        bus.setLoadTargetQ(newLoadTargetQ);
                    }
                }

                bus.setLoadTargetP(newLoadTargetP);
                done += loadTargetP - newLoadTargetP;
                modifiedBuses++;
            }
        }

        LOGGER.debug("{} MW / {} MW distributed at iteration {} to {} loads ({} at min consumption)",
                done * PerUnit.SB, -remainingMismatch * PerUnit.SB, iteration, modifiedBuses, loadsAtMin);

        return done;
    }
}
