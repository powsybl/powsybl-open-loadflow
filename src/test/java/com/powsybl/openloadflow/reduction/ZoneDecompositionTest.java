/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.reduction.ReductionUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
class ZoneDecompositionTest {

    @Test
    void testZoneDecompositionWith4BusNetwork() {
        Network network = create4BusNetwork();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        LfNetwork lfNetwork = lfNetworks.getFirst();
        Set<String> internalVlIds = Set.of("VL1");

        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, internalVlIds);

        Set<String> boundaryVlIds = getVoltageLevelsIds(zones.getBoundaryBuses());
        Set<String> expectedBoundaryVLIds = Set.of("VL2", "VL3");
        assertEquals(expectedBoundaryVLIds, boundaryVlIds);

        Set<String> externalVLIds = getVoltageLevelsIds(zones.getExternalBuses());
        Set<String> expectedExternalVLIds = Set.of("VL4");
        assertEquals(expectedExternalVLIds, externalVLIds);
    }

    @Test
    void testZoneDecompositionWithIEEE118() {
        Network network = IeeeCdfNetworkFactory.create118();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        LfNetwork lfNetwork = lfNetworks.getFirst();
        Set<String> internalVlIds = getIEEE118InternalVlIds();

        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, internalVlIds);

        Set<String> boundaryVlIds = getVoltageLevelsIds(zones.getBoundaryBuses());
        Set<String> expectedBoundaryVLIds = getIEEE118BoundaryVlIds();
        assertEquals(expectedBoundaryVLIds, boundaryVlIds);

        Set<String> externalVLIds = getVoltageLevelsIds(zones.getExternalBuses());
        Set<String> expectedExternalVLIds = getIEEE118ExternalVlIds();
        assertEquals(expectedExternalVLIds, externalVLIds);
    }

    @Test
    void testGetInternalVoltageLevelIdsByCountry() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Set<String> internalVlIds = ZoneDecomposition.getInternalVoltageLevelIds(network, Country.FR);
        Set<String> expectedInternalVlIds = Set.of("b1_vl", "b2_vl", "b3_vl", "b4_vl");
        assertEquals(expectedInternalVlIds, internalVlIds);

        network.getSubstation("b1_s").setCountry(Country.BE);
        network.getSubstation("b2_s").setCountry(Country.BE);
        internalVlIds = ZoneDecomposition.getInternalVoltageLevelIds(network, Country.FR);
        expectedInternalVlIds = Set.of("b3_vl", "b4_vl");
        assertEquals(expectedInternalVlIds, internalVlIds);
    }

    @Test
    void testGetInternalVoltageLevelIdsByTso() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        network.getSubstation("b1_s").setTso("ELIA");
        network.getSubstation("b2_s").setTso("ELIA");
        network.getSubstation("b3_s").setTso("RTE");
        network.getSubstation("b4_s").setTso("RTE");

        Set<String> internalVlIds = ZoneDecomposition.getInternalVoltageLevelIds(network, "ELIA");
        Set<String> expectedInternalVlIds = Set.of("b1_vl", "b2_vl");
        assertEquals(expectedInternalVlIds, internalVlIds);

        internalVlIds = ZoneDecomposition.getInternalVoltageLevelIds(network, "RTE");
        expectedInternalVlIds = Set.of("b3_vl", "b4_vl");
        assertEquals(expectedInternalVlIds, internalVlIds);
    }

    private Set<String> getVoltageLevelsIds(List<LfBus> lfBuses) {
        return lfBuses.stream()
            .map(LfBus::getVoltageLevelId)
            .collect(Collectors.toSet());
    }
}
