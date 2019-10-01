/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.simple.ac.outerloop.OuterLoop;
import com.powsybl.loadflow.simple.ac.outerloop.OuterLoopContext;
import com.powsybl.loadflow.simple.ac.outerloop.OuterLoopStatus;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.loadflow.simple.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.powsybl.loadflow.simple.ac.nr.DefaultNewtonRaphsonStoppingCriteria.CONV_EPS_PER_EQ;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DistributedSlackOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOuterLoop.class);

    /**
     * Slack active power residue epsilon: 10^-5 in p.u => 10^-3 in Mw
     */
    private static final double SLACK_P_RESIDUE_EPS = Math.pow(10, -5);

    static class ParticipatingBus {

        final LfBus bus;

        double factor;

        ParticipatingBus(LfBus bus, double factor) {
            this.bus = bus;
            this.factor = factor;
        }
    }

    @Override
    public String getName() {
        return "Distributed slack";
    }

    private static List<ParticipatingBus> getParticipatingBuses(LfNetwork network) {
        return network.getBuses()
                .stream()
                .map(bus -> new ParticipatingBus(bus, bus.getParticipationFactor()))
                .filter(participatingBus -> participatingBus.factor != 0)
                .collect(Collectors.toList());
    }

    private void normalizeParticipationFactors(List<ParticipatingBus> participatingBuses) {
        double factorSum = participatingBuses.stream()
                .mapToDouble(participatingBus -> participatingBus.factor)
                .sum();
        if (factorSum == 0) {
            throw new PowsyblException("No more generator participating to slack distribution");
        }
        for (ParticipatingBus participatingBus : participatingBuses) {
            participatingBus.factor /= factorSum;
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > CONV_EPS_PER_EQ) {
            LfNetwork network = context.getNetwork();

            List<ParticipatingBus> participatingBuses = getParticipatingBuses(network);

            int iteration = 0;
            double remainingMismatch = slackBusActivePowerMismatch;
            while (!participatingBuses.isEmpty()
                    && Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {

                remainingMismatch -= run(participatingBuses, iteration, remainingMismatch);

                iteration++;
            }

            if (Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {
                throw new PowsyblException("Failed to distribute slack bus active power mismatch, "
                        + remainingMismatch * PerUnit.SB + " MW remains");
            } else {
                LOGGER.debug("Slack bus active power ({} MW) distributed in {} iterations",
                        slackBusActivePowerMismatch * PerUnit.SB, iteration);
            }

            return OuterLoopStatus.UNSTABLE;
        }

        LOGGER.debug("Already balanced");

        return OuterLoopStatus.STABLE;
    }

    private double run(List<ParticipatingBus> participatingBuses, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // buses might have reach a limit and have been discarded
        normalizeParticipationFactors(participatingBuses);

        double done = 0d;
        int modifiedBuses = 0;
        int busesAtMax = 0;
        int busesAtMin = 0;
        Iterator<ParticipatingBus> it = participatingBuses.iterator();
        while (it.hasNext()) {
            ParticipatingBus participatingBus = it.next();
            LfBus bus = participatingBus.bus;
            double factor = participatingBus.factor;

            double minP = bus.getMinP();
            double maxP = bus.getMaxP();
            double generationTargetP = bus.getGenerationTargetP();

            // we don't want to change the generation sign
            if (generationTargetP < 0) {
                maxP = Math.min(maxP, 0);
            } else {
                minP = Math.max(minP, 0);
            }

            double newGenerationTargetP = generationTargetP + remainingMismatch * factor;
            if (remainingMismatch > 0 && newGenerationTargetP > maxP) {
                newGenerationTargetP = maxP;
                busesAtMax++;
                it.remove();
            } else if (remainingMismatch < 0 && newGenerationTargetP < minP) {
                newGenerationTargetP = minP;
                busesAtMin++;
                it.remove();
            }

            if (newGenerationTargetP != generationTargetP) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                            bus.getId(), generationTargetP * PerUnit.SB, newGenerationTargetP * PerUnit.SB);
                }

                bus.setGenerationTargetP(newGenerationTargetP);
                done += newGenerationTargetP - generationTargetP;
                modifiedBuses++;
            }
        }

        LOGGER.debug("{} MW / {} MW distributed at iteration {} to {} buses ({} at max power, {} at min power)",
                done * PerUnit.SB, remainingMismatch * PerUnit.SB, iteration, modifiedBuses,
                busesAtMax, busesAtMin);

        return done;
    }
}
