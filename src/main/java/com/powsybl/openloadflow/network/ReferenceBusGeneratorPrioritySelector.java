/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ReferenceBusGeneratorPrioritySelector implements ReferenceBusSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceBusGeneratorPrioritySelector.class);
    private static final String METHOD_NAME = "Generator Reference Priority";

    private static final ReferenceBusSelector FALLBACK_SELECTOR = new ReferenceBusFirstSlackSelector();

    @Override
    public SelectedReferenceBus select(LfNetwork lfNetwork) {
        Objects.requireNonNull(lfNetwork);
        List<LfGenerator> lfGenerators = lfNetwork.getBuses().stream()
                .filter(bus -> !bus.isFictitious())
                .flatMap(bus -> bus.getGenerators().stream())
                .filter(g -> !g.isFictitious())
                .toList();
        List<LfGenerator> lfGeneratorsPrioritized = lfGenerators.stream()
                .filter(g -> g.getReferencePriority() > 0)
                .sorted(Comparator.comparingInt(LfReferencePriorityInjection::getReferencePriority))
                .toList();
        final int priority;
        if (lfGeneratorsPrioritized.isEmpty()) {
            priority = 0;
        } else {
            priority = lfGeneratorsPrioritized.get(0).getReferencePriority();
        }
        LOGGER.info("Network {}, will select reference generator among generators of priority {}", lfNetwork, priority);
        // if multiple generators of same priority, select based on highest maxP
        LfGenerator referenceGenerator = lfGenerators.stream()
                .filter(g -> g.getReferencePriority() == priority)
                .min(Comparator.comparingDouble(LfGenerator::getMaxTargetP).reversed().thenComparing(LfGenerator::getId)
                ).orElse(null);
        if (referenceGenerator != null) {
            LfBus referenceBus = referenceGenerator.getBus();
            return new SelectedGeneratorReferenceBus(referenceBus, METHOD_NAME, referenceGenerator);
        } else {
            // E.g. an island with only Vsc Hvdc Converter and/or only Static Var Compensator.
            // In this case the island doesn't have any reference generator, only a reference bus.
            // Note that SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR will fail on such islands.
            return FALLBACK_SELECTOR.select(lfNetwork);
        }
    }
}
