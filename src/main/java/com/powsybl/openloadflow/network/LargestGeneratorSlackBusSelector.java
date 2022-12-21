/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.PerUnit;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LargestGeneratorSlackBusSelector implements SlackBusSelector {

    private final double plausibleActivePowerLimit;

    public LargestGeneratorSlackBusSelector(double plausibleActivePowerLimit) {
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
    }

    private static double getMaxP(LfBus bus) {
        return bus.getGenerators().stream().mapToDouble(LfGenerator::getMaxP).sum();
    }

    private boolean isGeneratorInvalid(LfGenerator generator) {
        return generator.isFictitious() || generator.getMaxP() > plausibleActivePowerLimit / PerUnit.SB;
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        List<LfBus> slackBuses = buses.stream()
                .filter(bus -> !bus.getGenerators().isEmpty() && bus.getGenerators().stream().noneMatch(this::isGeneratorInvalid))
                .sorted(Comparator.comparingDouble(LargestGeneratorSlackBusSelector::getMaxP).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        return new SelectedSlackBus(slackBuses, "Largest generator bus");
    }
}
