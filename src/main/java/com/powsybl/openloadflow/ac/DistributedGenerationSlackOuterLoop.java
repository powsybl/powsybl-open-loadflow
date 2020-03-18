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
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DistributedGenerationSlackOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedGenerationSlackOuterLoop.class);

    /**
     * Slack active power residue epsilon: 10^-5 in p.u => 10^-3 in Mw
     */
    private static final double SLACK_P_RESIDUE_EPS = Math.pow(10, -5);

    static class ParticipatingGenerator {

        final LfGenerator generator;

        double factor;

        ParticipatingGenerator(LfGenerator generator, double factor) {
            this.generator = generator;
            this.factor = factor;
        }
    }

    @Override
    public String getName() {
        return "Distributed slack on generators";
    }

    private static List<ParticipatingGenerator> getParticipatingGenerators(LfNetwork network) {
        return network.getBuses()
                .stream()
                .flatMap(bus -> bus.getGenerators().stream())
                .filter(generator -> generator.isParticipating() && generator.getParticipationFactor() != 0)
                .map(generator -> new ParticipatingGenerator(generator, generator.getParticipationFactor()))
                .collect(Collectors.toList());
    }

    private void normalizeParticipationFactors(List<ParticipatingGenerator> participatingGenerators) {
        double factorSum = participatingGenerators.stream()
                .mapToDouble(participatingGenerator -> participatingGenerator.factor)
                .sum();
        if (factorSum == 0) {
            throw new PowsyblException("No more generator participating to slack distribution");
        }
        for (ParticipatingGenerator participatingGenerator : participatingGenerators) {
            participatingGenerator.factor /= factorSum;
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        double slackBusActivePowerMismatch = context.getLastNewtonRaphsonResult().getSlackBusActivePowerMismatch();
        if (Math.abs(slackBusActivePowerMismatch) > DefaultNewtonRaphsonStoppingCriteria.CONV_EPS_PER_EQ) {
            LfNetwork network = context.getNetwork();

            List<ParticipatingGenerator> participatingGenerators = getParticipatingGenerators(network);

            int iteration = 0;
            double remainingMismatch = slackBusActivePowerMismatch;
            while (!participatingGenerators.isEmpty()
                    && Math.abs(remainingMismatch) > SLACK_P_RESIDUE_EPS) {

                remainingMismatch -= run(participatingGenerators, iteration, remainingMismatch);

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

    private double run(List<ParticipatingGenerator> participatingGenerators, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // generators might have reach a limit and have been discarded
        normalizeParticipationFactors(participatingGenerators);

        double done = 0d;
        int modifiedBuses = 0;
        int generatorsAtMax = 0;
        int generatorsAtMin = 0;
        Iterator<ParticipatingGenerator> it = participatingGenerators.iterator();
        while (it.hasNext()) {
            ParticipatingGenerator participatingGenerator = it.next();
            LfGenerator generator = participatingGenerator.generator;
            double factor = participatingGenerator.factor;

            double minP = generator.getMinP();
            double maxP = generator.getMaxP();
            double targetP = generator.getTargetP();

            // we don't want to change the generation sign
            if (targetP < 0) {
                maxP = Math.min(maxP, 0);
            } else {
                minP = Math.max(minP, 0);
            }

            double newTargetP = targetP + remainingMismatch * factor;
            if (remainingMismatch > 0 && newTargetP > maxP) {
                newTargetP = maxP;
                generatorsAtMax++;
                it.remove();
            } else if (remainingMismatch < 0 && newTargetP < minP) {
                newTargetP = minP;
                generatorsAtMin++;
                it.remove();
            }

            if (newTargetP != targetP) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                            generator.getId(), targetP * PerUnit.SB, newTargetP * PerUnit.SB);
                }

                generator.setTargetP(newTargetP);
                done += newTargetP - targetP;
                modifiedBuses++;
            }
        }

        LOGGER.debug("{} MW / {} MW distributed at iteration {} to {} generators ({} at max power, {} at min power)",
                done * PerUnit.SB, remainingMismatch * PerUnit.SB, iteration, modifiedBuses,
                generatorsAtMax, generatorsAtMin);

        return done;
    }
}
