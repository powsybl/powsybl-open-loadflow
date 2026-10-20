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
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.ReferenceBusSelectionMode;
import com.powsybl.openloadflow.reduction.WardReductionParameters.ReductionType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.powsybl.openloadflow.reduction.Reducer.EQUIVALENT;
import static com.powsybl.openloadflow.reduction.ReductionUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
class WardEquivalentTest {

    @Test
    void testWardEquivalentWith4BusNetwork() {
        testWardEquivalent(create4BusNetwork(), "GEN1", Set.of("VL1"), Set.of("VL2", "VL3"));
    }

    @Test
    void testWardEquivalentWithIEEE118() {
        testWardEquivalent(IeeeCdfNetworkFactory.create118(), "B69-G", getIEEE118InternalVlIds(), getIEEE118BoundaryVlIds());
    }

    private void testWardEquivalent(Network network, String refGenId, Set<String> internalVlIds, Set<String> boundaryVLIds) {
        // Run a load flow before reduction and read the bus state values on internal and boundary buses
        LoadFlow.Runner loadFlowRunner = LoadFlow.find();
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(loadFlowParameters);
        parametersExt.setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
        ReferencePriority.set(network.getGenerator(refGenId), 1);
        loadFlowRunner.run(network, loadFlowParameters);
        List<BusSV> busesSVBeforeReduction = getBusesStateValues(network, internalVlIds, boundaryVLIds);

        // Reduce the network
        WardReductionParameters parameters = new WardReductionParameters()
            .setInternalVoltageLevelIds(internalVlIds)
            .setLoadFlowParameters(loadFlowParameters)
            .setMatrixFactory(new DenseMatrixFactory())
            .setReductionType(ReductionType.STANDARD_WARD);
        new WardEquivalent().reduce(network, parameters);

        // Run a load flow after reduction and read the bus state values on internal and boundary buses
        loadFlowRunner.run(network, loadFlowParameters);
        List<BusSV> busesSVAfterReduction = getBusesStateValues(network, internalVlIds, boundaryVLIds);

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
            .filter(vl -> internalVlIds.contains(vl.getId().replace(EQUIVALENT, ""))
                || boundaryVLIds.contains(vl.getId().replace(EQUIVALENT, "")))
            .flatMap(vl -> vl.getBusView().getBusStream())
            .sorted(Comparator.comparing(Bus::getId))
            .map(bus -> new BusSV(bus.getId(), bus.getV(), bus.getAngle()))
            .toList();
    }

    private record BusSV(String id, double v, double angle) {
    }
}
