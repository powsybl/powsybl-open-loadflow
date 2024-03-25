/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.SlackTerminal;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class NetworkSlackBusSelector extends AbstractSlackBusSelector {

    private static final String SELECTION_METHOD = "Network extension bus";

    private final SlackBusSelector fallbackSelector;

    private final Set<String> slackBusIds = new HashSet<>();

    public NetworkSlackBusSelector(Network network, Set<Country> countries, SlackBusSelector fallbackSelector) {
        super(countries);
        Objects.requireNonNull(network);
        this.fallbackSelector = Objects.requireNonNull(fallbackSelector);
        for (VoltageLevel vl : network.getVoltageLevels()) {
            SlackTerminal slackTerminal = vl.getExtension(SlackTerminal.class);
            if (slackTerminal != null && slackTerminal.getTerminal() != null) {
                Bus bus = slackTerminal.getTerminal().getBusView().getBus();
                if (bus != null) {
                    slackBusIds.add(bus.getId());
                }
                bus = slackTerminal.getTerminal().getBusBreakerView().getBus();
                if (bus != null) {
                    slackBusIds.add(bus.getId());
                }
            }
        }
    }

    @Override
    public SelectedSlackBus select(List<LfBus> buses, int limit) {
        List<LfBus> slackBuses = buses.stream()
                .filter(bus -> !bus.isFictitious())
                .filter(this::filterByCountry)
                .filter(bus -> slackBusIds.contains(bus.getId()))
                .collect(Collectors.toList());
        if (slackBuses.isEmpty()) {
            // fallback to automatic selection
            return fallbackSelector.select(buses, limit);
        } else if (slackBuses.size() <= limit) {
            return new SelectedSlackBus(slackBuses, SELECTION_METHOD);
        } else {
            // fallback to automatic selection among slack buses
            var slackBusSelector = fallbackSelector.select(slackBuses, limit);
            return new SelectedSlackBus(slackBusSelector.getBuses(), SELECTION_METHOD + " + " + slackBusSelector.getSelectionMethod());
        }
    }
}
