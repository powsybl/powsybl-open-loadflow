/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NameSlackBusSelector implements SlackBusSelector {

    private final List<String> busesIds;

    public NameSlackBusSelector(List<String> busesIds) {
        if (busesIds.isEmpty()) {
            throw new IllegalArgumentException("Empty bus list");
        }
        this.busesIds = Objects.requireNonNull(busesIds);
    }

    public NameSlackBusSelector(String... busesIds) {
        this(List.of(busesIds));
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses) {
        Map<String, LfBus> busesById = buses.stream().collect(Collectors.toMap(LfBus::getId, Function.identity()));
        for (String busId : busesIds) {
            LfBus slackBus = busesById.get(busId);
            if (slackBus != null) {
                return new SelectedSlackBus(slackBus, "Parameter bus");
            }
        }
        throw new PowsyblException("None of the slack buses " + busesIds + " have been not found");
    }
}
