/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NameSlackBusSelector implements SlackBusSelector {

    private static final String SELECTION_METHOD = "Parameter bus";

    private final List<String> busesOrVoltageLevelsIds;

    private final SlackBusSelector secondLevelSelector;

    public NameSlackBusSelector(List<String> busesOrVoltageLevelsIds, Set<Country> countriesForSlackBusSelection) {
        if (busesOrVoltageLevelsIds.isEmpty()) {
            throw new IllegalArgumentException("Empty bus or voltage level ID list");
        }
        this.busesOrVoltageLevelsIds = Objects.requireNonNull(busesOrVoltageLevelsIds);
        this.secondLevelSelector = new MostMeshedSlackBusSelector(countriesForSlackBusSelection);
    }

    public NameSlackBusSelector(String... busesOrVoltageLevelsIds) {
        this(List.of(busesOrVoltageLevelsIds), Collections.emptySet()); // FIXME
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        Map<String, LfBus> busesById = buses.stream().collect(Collectors.toMap(LfBus::getId, Function.identity()));
        Map<String, List<LfBus>> busesByVoltageLevelId = buses.stream().collect(Collectors.groupingBy(LfBus::getVoltageLevelId));

        List<LfBus> slackBuses = busesOrVoltageLevelsIds.stream().flatMap(id -> {
            // first try to search as a bus ID
            LfBus slackBus = busesById.get(id);
            if (slackBus != null) {
                return Stream.of(slackBus);
            }
            var slackBusCandidates = busesByVoltageLevelId.get(id);
            if (slackBusCandidates != null) {
                return slackBusCandidates.stream();
            }
            return Stream.empty();
        }).collect(Collectors.toList());

        if (slackBuses.isEmpty()) {
            // fallback to automatic selection among all buses
            return secondLevelSelector.select(buses, limit);
        } else if (slackBuses.size() <= limit) {
            return new SelectedSlackBus(slackBuses, SELECTION_METHOD);
        } else {
            var selectedSlackBus = secondLevelSelector.select(slackBuses, limit);
            return new SelectedSlackBus(selectedSlackBus.getBuses(), SELECTION_METHOD + " + " + selectedSlackBus.getSelectionMethod());
        }
    }
}
