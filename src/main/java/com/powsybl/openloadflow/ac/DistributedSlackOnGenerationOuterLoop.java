/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

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
public class DistributedSlackOnGenerationOuterLoop extends AbstractDistributedSlackOuterLoop<LfGenerator> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedSlackOnGenerationOuterLoop.class);

    @Override
    public String getName() {
        return "Distributed slack on generation";
    }

    @Override
    protected List<ParticipatingElement<LfGenerator>> getParticipatingElements(LfNetwork network) {
        return network.getBuses()
                .stream()
                .flatMap(bus -> bus.getGenerators().stream())
                .filter(generator -> generator.isParticipating() && generator.getParticipationFactor() != 0)
                .map(generator -> new ParticipatingElement<>(generator, generator.getParticipationFactor()))
                .collect(Collectors.toList());
    }

    @Override
    protected double run(List<ParticipatingElement<LfGenerator>> participatingElements, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // generators might have reach a limit and have been discarded
        normalizeParticipationFactors(participatingElements, "generator");

        double done = 0d;
        int modifiedBuses = 0;
        int generatorsAtMax = 0;
        int generatorsAtMin = 0;
        Iterator<ParticipatingElement<LfGenerator>> it = participatingElements.iterator();
        while (it.hasNext()) {
            ParticipatingElement<LfGenerator> participatingGenerator = it.next();
            LfGenerator generator = participatingGenerator.element;
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
