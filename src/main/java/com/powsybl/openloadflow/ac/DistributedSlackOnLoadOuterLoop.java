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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class DistributedSlackOnLoadOuterLoop extends AbstractDistributedSlackOuterLoop<LfBus> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOnLoadOuterLoop.class);

    private final boolean distributedSlackOnConformLoad;
    private final boolean powerFactorConstant;

    public DistributedSlackOnLoadOuterLoop(boolean throwsExceptionInCaseOfFailure, boolean distributedSlackOnConformLoad, boolean powerFactorConstant) {
        super(throwsExceptionInCaseOfFailure);
        this.distributedSlackOnConformLoad = distributedSlackOnConformLoad;
        this.powerFactorConstant = powerFactorConstant;
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
                LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                        bus.getId(), loadTargetP * PerUnit.SB, newLoadTargetP * PerUnit.SB);
                // if powerFactorConstant is true, when updating P value on loads,
                // we have to keep powerFactor a constant value by updating Q value too
                // for more details, see https://github.com/powsybl/powsybl-open-loadflow/issues/110#issuecomment-726812696
                if (powerFactorConstant) {
                    double loadTargetQ = bus.getLoadTargetQ();
                    // keep power factor constant by using rule of three
                    double newLoadTargetQ = loadTargetQ * newLoadTargetP / loadTargetP;
                    LOGGER.trace("Rescale '{}' reactive power target on load: {} -> {}",
                            bus.getId(), loadTargetQ * PerUnit.SB, newLoadTargetQ * PerUnit.SB);
                    bus.setLoadTargetQ(newLoadTargetQ);
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
