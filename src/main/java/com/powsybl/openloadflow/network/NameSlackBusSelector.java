/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NameSlackBusSelector implements SlackBusSelector {

    private static final String SELECTION_METHOD = "Parameter bus";

    private final List<String> busesOrVoltageLevelsIds;

    private final SlackBusSelector secondLevelSelector = new MostMeshedSlackBusSelector();

    public NameSlackBusSelector(List<String> busesOrVoltageLevelsIds) {
        if (busesOrVoltageLevelsIds.isEmpty()) {
            throw new IllegalArgumentException("Empty bus or voltage level ID list");
        }
        this.busesOrVoltageLevelsIds = Objects.requireNonNull(busesOrVoltageLevelsIds);
    }

    public NameSlackBusSelector(String... busesOrVoltageLevelsIds) {
        this(List.of(busesOrVoltageLevelsIds));
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses) {
        Map<String, LfBus> busesById = buses.stream().collect(Collectors.toMap(LfBus::getId, Function.identity()));
        Map<String, List<LfBus>> busesByVoltageLevelId = buses.stream().collect(Collectors.groupingBy(LfBus::getVoltageLevelId));
        for (String busOrVoltageLevelId : busesOrVoltageLevelsIds) {
            // first try to search as a bus ID
            LfBus slackBus = busesById.get(busOrVoltageLevelId);
            if (slackBus != null) {
                return new SelectedSlackBus(slackBus, SELECTION_METHOD);
            }
            // then as a voltage level ID
            var slackBusCandidates = busesByVoltageLevelId.get(busOrVoltageLevelId);
            if (slackBusCandidates != null) {
                if (slackBusCandidates.size() == 1) {
                    return new SelectedSlackBus(slackBusCandidates.get(0), SELECTION_METHOD);
                } else {
                    var selectedSlackBus = secondLevelSelector.select(slackBusCandidates);
                    return new SelectedSlackBus(selectedSlackBus.getBus(), SELECTION_METHOD + " + " + selectedSlackBus.getSelectionMethod());
                }
            }
        }
        // fallback to automatic selection among all buses
        var slackBusSelector = secondLevelSelector.select(buses);
        return new SelectedSlackBus(slackBusSelector.getBus(), SELECTION_METHOD + " + " + slackBusSelector.getSelectionMethod());
    }
}
