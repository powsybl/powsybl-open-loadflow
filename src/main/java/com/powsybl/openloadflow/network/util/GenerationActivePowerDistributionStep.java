/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class GenerationActivePowerDistributionStep implements ActivePowerDistribution.Step {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationActivePowerDistributionStep.class);

    public enum ParticipationType {
        MAX,
        TARGET,
        PARTICIPATION_FACTOR,
        REMAINING_MARGIN
    }

    private final ParticipationType participationType;

    public GenerationActivePowerDistributionStep(ParticipationType pParticipationType) {
        this.participationType = pParticipationType;
    }

    @Override
    public String getElementType() {
        return "generation";
    }

    @Override
    public List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses) {
        return buses.stream()
                .filter(bus -> bus.isParticipating() && !bus.isDisabled() && !bus.isFictitious())
                .flatMap(bus -> bus.getGenerators().stream())
                .filter(generator -> isParticipating(generator) && getParticipationFactor(generator) != 0)
                .map(generator -> new ParticipatingElement(generator, getParticipationFactor(generator)))
                .collect(Collectors.toList());
    }

    @Override
    public double run(List<ParticipatingElement> participatingElements, int iteration, double remainingMismatch) {
        // normalize participation factors at each iteration start as some
        // generators might have reach a limit and have been discarded
        ParticipatingElement.normalizeParticipationFactors(participatingElements, "generator");

        double done = 0d;
        int modifiedBuses = 0;
        int generatorsAtMax = 0;
        int generatorsAtMin = 0;
        Iterator<ParticipatingElement> it = participatingElements.iterator();
        while (it.hasNext()) {
            ParticipatingElement participatingGenerator = it.next();
            LfGenerator generator = (LfGenerator) participatingGenerator.getElement();
            double factor = participatingGenerator.getFactor();

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
                LOGGER.trace("Rescale '{}' active power target: {} -> {}",
                        generator.getId(), targetP * PerUnit.SB, newTargetP * PerUnit.SB);
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

    private double getParticipationFactor(LfGenerator generator) {
        switch (participationType) {
            case MAX:
                return generator.getMaxP() / generator.getDroop();
            case TARGET:
                return Math.abs(generator.getTargetP());
            case PARTICIPATION_FACTOR:
                return generator.getParticipationFactor();
            case REMAINING_MARGIN:
                return Math.max(0.0, generator.getMaxP() - generator.getTargetP());
            default:
                throw new UnsupportedOperationException("Unknown balance type mode: " + participationType);
        }
    }

    private boolean isParticipating(LfGenerator generator) {
        // check first if generator is set to be participating
        if (!generator.isParticipating()) {
            return false;
        }
        // then depending on participation type, a generator may be found to not participate
        switch (participationType) {
            case MAX:
                return generator.getDroop() != 0;
            case PARTICIPATION_FACTOR:
                return generator.getParticipationFactor() > 0;
            case TARGET:
            case REMAINING_MARGIN:
                // nothing more to do here: the check whether TargetP is within Pmin-Pmax range
                // was already made in AbstractLfGenerator#checkActivePowerControl
                // whose result is reflected in generator.isParticipating()
                return true;
            default:
                throw new UnsupportedOperationException("Unknown balance type mode: " + participationType);
        }
    }
}
