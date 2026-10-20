/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public final class ZoneDecomposition {

    private final LfNetwork lfNetwork;
    private final Set<String> internalVoltageLevelIds;
    private final List<LfBus> internalBuses;
    private final List<LfBus> boundaryBuses;
    private final List<LfBus> externalBuses;

    public ZoneDecomposition(LfNetwork lfNetwork, Set<String> internalVoltageLevelIds) {
        this.lfNetwork = Objects.requireNonNull(lfNetwork);
        this.internalVoltageLevelIds = Objects.requireNonNull(internalVoltageLevelIds);
        internalBuses = new ArrayList<>();
        boundaryBuses = new ArrayList<>();
        externalBuses = new ArrayList<>();
        decompose();
    }

    private void decompose() {
        // Decompose the network into internal, boundary and external buses
        lfNetwork.getBuses().stream()
            .sorted(Comparator.comparing(LfBus::getNum))
            .forEach(b -> {
                if (isInternalBus(b)) {
                    internalBuses.add(b);
                } else if (isBoundaryBus(b)) {
                    boundaryBuses.add(b);
                } else {
                    externalBuses.add(b);
                }
            });
    }

    private boolean isBoundaryBus(LfBus lfBus) {
        // A boundary bus is an external bus having a branch linking it to an internal bus
        return lfBus != null
            && !internalVoltageLevelIds.contains(lfBus.getVoltageLevelId())
            && lfBus.getBranches().stream()
            .anyMatch(br -> br.getBus1() == lfBus && isInternalBus(br.getBus2())
                || br.getBus2() == lfBus && isInternalBus(br.getBus1()));
    }

    private boolean isInternalBus(LfBus bus) {
        return bus != null && internalVoltageLevelIds.contains(bus.getVoltageLevelId());
    }

    public List<LfBus> getInternalBuses() {
        return internalBuses;
    }

    public List<LfBus> getBoundaryBuses() {
        return boundaryBuses;
    }

    public List<LfBus> getExternalBuses() {
        return externalBuses;
    }

    public static Set<String> getInternalVoltageLevelIds(Network network, Country country) {
        Objects.requireNonNull(country);
        return network.getSubstationStream()
            .filter(s -> s.getNullableCountry() == country)
            .flatMap(Substation::getVoltageLevelStream)
            .map(VoltageLevel::getId)
            .collect(Collectors.toSet());
    }

    public static Set<String> getInternalVoltageLevelIds(Network network, String tso) {
        Objects.requireNonNull(tso);
        return network.getSubstationStream()
            .filter(s -> tso.equals(s.getTso()))
            .flatMap(Substation::getVoltageLevelStream)
            .map(VoltageLevel::getId)
            .collect(Collectors.toSet());
    }
}
