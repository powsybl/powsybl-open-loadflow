/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.ReferenceBusSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.reduction.Reducer.EQUIVALENT;
import static com.powsybl.openloadflow.reduction.ReductionUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
class WardReductionTest {

    @Test
    void testWardReductionWith4BusNetwork() {
        testWardReduction(create4BusNetwork(), "GEN1", Set.of("VL1"), Set.of("VL2", "VL3"), Set.of("VL4"));
    }

    @Test
    void testWardReductionWithIEEE118() {
        testWardReduction(IeeeCdfNetworkFactory.create118(), "B69-G", getIEEE118InternalVlIds(), getIEEE118BoundaryVlIds(), getIEEE118ExternalVlIds());
    }

    private void testWardReduction(Network network, String refGenId, Set<String> internalVlIds, Set<String> boundaryVlIds, Set<String> externalVlIds) {
        // Run a load flow before reduction and read the bus state values on internal and boundary buses
        LoadFlow.Runner loadFlowRunner = LoadFlow.find();
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(loadFlowParameters);
        parametersExt.setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
        ReferencePriority.set(network.getGenerator(refGenId), 1);
        loadFlowRunner.run(network, loadFlowParameters);
        List<BusSV> busesSVBeforeReduction = getBusesStateValues(network, internalVlIds, boundaryVlIds);

        // Reduce the network
        WardReductionParameters wardParameters = new WardReductionParameters()
            .setInternalVoltageLevelIds(internalVlIds)
            .setLoadFlowParameters(loadFlowParameters)
            .setEquivalentType(WardEquivalentType.STANDARD);
        Reducer.ReductionChanges changes = WardReduction.reduce(network, wardParameters);
        assertEquals(externalVlIds, changes.removedVoltageLevels().stream().map(VoltageLevel::getId).collect(Collectors.toSet()));

        // Run a load flow after reduction and read the bus state values on internal and boundary buses
        loadFlowRunner.run(network, loadFlowParameters);
        List<BusSV> busesSVAfterReduction = getBusesStateValues(network, internalVlIds, boundaryVlIds);

        // Compare the bus state values before and after reduction
        assertEquals(busesSVBeforeReduction.size(), busesSVAfterReduction.size());
        for (int i = 0; i < busesSVAfterReduction.size(); i++) {
            BusSV busBefore = busesSVBeforeReduction.get(i);
            BusSV busAfter = busesSVAfterReduction.get(i);
            assertEquals(busBefore.id, busAfter.id.replace(EQUIVALENT, ""));
            assertEquals(busBefore.v, busAfter.v, 1e-3);
            assertEquals(busBefore.angle, busAfter.angle, 1e-3);
        }
    }

    private List<BusSV> getBusesStateValues(Network network, Set<String> internalVlIds, Set<String> boundaryVLIds) {
        // Return a list of bus state values for internal and boundary buses
        return network.getVoltageLevelStream()
            .filter(vl -> internalVlIds.contains(vl.getId())
                || boundaryVLIds.contains(vl.getId().replace(EQUIVALENT, "")))
            .flatMap(vl -> vl.getBusView().getBusStream())
            .sorted(Comparator.comparing(Bus::getId))
            .map(bus -> new BusSV(bus.getId(), bus.getV(), bus.getAngle()))
            .toList();
    }

    private record BusSV(String id, double v, double angle) {
    }
}
