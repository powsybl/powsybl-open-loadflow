/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
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
    void testZoneDecomposition() {
        Network network = IeeeCdfNetworkFactory.create118();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        LfNetwork lfNetwork = lfNetworks.getFirst();
        Set<String> internalVlIds = getIEEE118InternalVlIds();

        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, internalVlIds);

        Set<String> boundaryVl = zones.getBoundaryBuses().stream()
            .map(LfBus::getVoltageLevelId)
            .collect(Collectors.toSet());
        Set<String> expectedBoundaryVL = getIEEE118BoundaryVlIds();
        assertEquals(expectedBoundaryVL, boundaryVl);

        Set<String> externalVL = zones.getExternalBuses().stream()
            .map(LfBus::getVoltageLevelId)
            .collect(Collectors.toSet());
        Set<String> expectedExternalVL = Set.of(
            "VL1", "VL2", "VL3", "VL4", "VL5", "VL6", "VL7", "VL8", "VL9", "VL10", "VL11", "VL12", "VL13", "VL14",
            "VL15", "VL16", "VL17", "VL18", "VL19", "VL20", "VL21", "VL22", "VL23", "VL25", "VL26", "VL27", "VL28",
            "VL29", "VL30", "VL31", "VL32", "VL33", "VL34", "VL35", "VL36", "VL38", "VL113", "VL114", "VL115", "VL117"
        );
        assertEquals(expectedExternalVL, externalVL);
    }
}
