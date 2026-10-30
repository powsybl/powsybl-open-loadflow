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
 * Decompose a {@link LfNetwork} into lists of internal, boundary, and external {@link LfBus}
 * based on specified internal {@link VoltageLevel} IDs.
 *
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public final class ZoneDecomposition {

    private final LfNetwork lfNetwork;
    private final Set<String> internalVoltageLevelIds;
    private final List<LfBus> internalBuses;
    private final List<LfBus> boundaryBuses;
    private final List<LfBus> externalBuses;

    /**
     * Create a new ZoneDecomposition instance for the given network and set of internal voltage level IDs.
     *
     * @param lfNetwork the {@link LfNetwork} to decompose
     * @param internalVoltageLevelIds the set of internal {@link VoltageLevel} IDs
     */
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

    /**
     * Get the list of internal buses.
     *
     * @return the list of internal buses
     */
    public List<LfBus> getInternalBuses() {
        return internalBuses;
    }

    /**
     * Get the list of boundary buses.
     *
     * @return the list of boundary buses
     */
    public List<LfBus> getBoundaryBuses() {
        return boundaryBuses;
    }

    /**
     * Get the list of external buses.
     *
     * @return the list of external buses
     */
    public List<LfBus> getExternalBuses() {
        return externalBuses;
    }

    /**
     * Get the set of internal voltage level IDs for a specified country within a given network.
     *
     * @param network the {@link Network} from which to retrieve the internal voltage level IDs
     * @param country the {@link Country} whose internal voltage level IDs are to be retrieved
     * @return a set of voltage level IDs corresponding to the specified country within the provided network
     * @throws NullPointerException if the provided country is null
     */
    public static Set<String> getInternalVoltageLevelIds(Network network, Country country) {
        Objects.requireNonNull(country);
        return network.getSubstationStream()
            .filter(s -> s.getNullableCountry() == country)
            .flatMap(Substation::getVoltageLevelStream)
            .map(VoltageLevel::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Get the set of internal voltage level IDs for a specified TSO within a given network.
     *
     * @param network the {@link Network} from which to retrieve the internal voltage level IDs
     * @param tso the TSO whose internal voltage level IDs are to be retrieved
     * @return a set of voltage level IDs corresponding to the specified TSO within the provided network
     * @throws NullPointerException if the provided TSO is null
     */
    public static Set<String> getInternalVoltageLevelIds(Network network, String tso) {
        Objects.requireNonNull(tso);
        return network.getSubstationStream()
            .filter(s -> tso.equals(s.getTso()))
            .flatMap(Substation::getVoltageLevelStream)
            .map(VoltageLevel::getId)
            .collect(Collectors.toSet());
    }
}
