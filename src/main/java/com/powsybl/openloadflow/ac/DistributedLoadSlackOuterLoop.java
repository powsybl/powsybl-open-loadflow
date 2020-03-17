/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.nr.DefaultNewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
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
public class DistributedLoadSlackOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedLoadSlackOuterLoop.class);

    /**
     * Slack active power residue epsilon: 10^-5 in p.u => 10^-3 in Mw
     */
    private static final double SLACK_P_RESIDUE_EPS = Math.pow(10, -5);

    static class ParticipatingLoad {

        final LfBus load;

        double factor;

        ParticipatingLoad(LfBus load, double factor) {
            this.load = load;
            this.factor = factor;
        }
    }

    @Override
    public String getName() {
        return "Distributed slack";
    }

    private static List<ParticipatingLoad> getParticipatingLoads(LfNetwork network) {
        return network.getBuses()
                .stream()
                .filter(bus -> bus.getLoadTargetP() > 0 && bus.isParticipating())
                .map(bus -> new ParticipatingLoad(bus, bus.getParticipationFactor()))
                .collect(Collectors.toList());
    }

    private void normalizeLoadParticipationFactors(List<ParticipatingLoad> participatingLoads) {
        double factorSum = participatingLoads.stream()
                .mapToDouble(participatingLoad -> participatingLoad.factor)
                .sum();
        if (factorSum == 0) {
            throw new PowsyblException("No more load participating to slack distribution");
        }
        for (ParticipatingLoad participatingLoad : participatingLoads) {
            participatingLoad.factor /= factorSum;
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > DefaultNewtonRaphsonStoppingCriteria.CONV_EPS_PER_EQ) {
            LfNetwork network = context.getNetwork();

            List<ParticipatingLoad> participatingLoads = getParticipatingLoads(network);

            int iteration = 0;
            double remainingMismatch = slackBusActivePowerMismatch;
            while (!participatingLoads.isEmpty()
                    && Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {

                remainingMismatch -= run(participatingLoads, iteration, remainingMismatch);

                iteration++;
            }

            if (Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {
                throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                        + remainingMismatch * PerUnit.SB + " MW remains");
            } else {
                LOGGER.info("Slack bus active power ({} MW) distributed in {} iterations",
                        slackBusActivePowerMismatch * PerUnit.SB, iteration);
            }

            return OuterLoopStatus.UNSTABLE;
        }

        LOGGER.debug("Already balanced");

        return OuterLoopStatus.STABLE;
    }

    private double run(List<ParticipatingLoad> participatingLoads, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // loads might have reach zero and have been discarded.
        normalizeLoadParticipationFactors(participatingLoads);

        double done = 0d;
        int modifiedBuses = 0;
        int loadsAtMin = 0;
        Iterator<ParticipatingLoad> it = participatingLoads.iterator();
        while (it.hasNext()) {
            ParticipatingLoad participatingLoad = it.next();
            LfBus load = participatingLoad.load;
            double factor = participatingLoad.factor;

            double targetP = load.getLoadTargetP();

            double newTargetP = targetP + remainingMismatch * factor;

            // We stop when the load produces power.
            if (remainingMismatch < 0 && newTargetP <= 0) {
                newTargetP = 0;
                loadsAtMin++;
                it.remove();
            }

            if (newTargetP != targetP) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                            load.getId(), targetP * PerUnit.SB, newTargetP * PerUnit.SB);
                }

                load.setLoadTargetP(newTargetP);
                done += newTargetP - targetP;
                modifiedBuses++;
            }
        }

        LOGGER.debug("{} MW / {} MW distributed at iteration {} to {} loads ({} at min consumption)",
                done * PerUnit.SB, remainingMismatch * PerUnit.SB, iteration, modifiedBuses, loadsAtMin);

        return done;
    }
}
