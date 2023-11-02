/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Gaël Macherel {@literal <gael.macherel at artelys.com>}
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class NetworkConnectivityTest {

    private LfNetwork lfNetwork;

    @BeforeEach
    void setup() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        lfNetwork = lfNetworks.get(0);
    }

    @Test
    void testConnectivity() {
        testConnectivity(new NaiveGraphConnectivity<>(LfBus::getNum));
        testConnectivity(new EvenShiloachGraphDecrementalConnectivity<>());
    }

    @Test
    void testReducedMainComponent() {
        // Testing the connectivity when the main component is suffering cuts so that it becomes smaller than a newly
        // created connected component.
        testReducedMainComponent(new NaiveGraphConnectivity<>(LfBus::getNum));
        testReducedMainComponent(new EvenShiloachGraphDecrementalConnectivity<>());
    }

    @Test
    void testReadEdge() {
        // Testing cutting an edge then adding it back
        testReaddEdge(new NaiveGraphConnectivity<>(LfBus::getNum), true);
        testReaddEdge(new EvenShiloachGraphDecrementalConnectivity<>(), false);
        testReaddEdge(new MinimumSpanningTreeGraphConnectivity<>(), true);
    }

    @Test
    void testNonConnected() {
        // Testing with a non-connected graph
        GraphConnectivity<LfBus, LfBranch> connectivity = new EvenShiloachGraphDecrementalConnectivity<>();
        for (LfBus lfBus : lfNetwork.getBuses()) {
            connectivity.addVertex(lfBus);
        }
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            if (!lfBranch.getId().equals("l48")) {
                connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
            }
        }
        PowsyblException e = assertThrows(PowsyblException.class, connectivity::startTemporaryChanges);
        assertEquals("This implementation does not support saving a graph with several connected components", e.getMessage());
    }

    @Test
    void testNonConnectedComponents() {
        testNonConnectedComponents(new NaiveGraphConnectivity<>(LfBus::getNum));
        testNonConnectedComponents(new EvenShiloachGraphDecrementalConnectivity<>());
        testNonConnectedComponents(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void testConnectedComponents() {
        testConnectedComponents(new NaiveGraphConnectivity<>(LfBus::getNum));
        testConnectedComponents(new EvenShiloachGraphDecrementalConnectivity<>());
        testConnectedComponents(new MinimumSpanningTreeGraphConnectivity<>());
    }

    private void testConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l48");

        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
        assertEquals(2, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
        assertEquals(3, connectivity.getNbConnectedComponents());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> connectivity.getComponentNumber(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private void testReducedMainComponent(GraphConnectivity<LfBus, LfBranch> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l48", "l56", "l57", "l67");

        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(2, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
        assertEquals(5, connectivity.getNbConnectedComponents());
    }

    private void testReaddEdge(GraphConnectivity<LfBus, LfBranch> connectivity, boolean supported) {
        updateConnectivity(connectivity);

        String branchId = "l34";
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        cutBranches(connectivity, branchId);

        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b1_vl_0")));
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));

        LfBus bus1 = lfBranch.getBus1();
        LfBus bus2 = lfBranch.getBus2();
        if (supported) {
            connectivity.addEdge(bus1, bus2, lfBranch);
            assertEquals(1, connectivity.getNbConnectedComponents());
            assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b1_vl_0")));
            assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));

            cutBranches(connectivity, "l48");
            assertEquals(2, connectivity.getNbConnectedComponents());
            assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
            assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
        } else {
            PowsyblException e = assertThrows(PowsyblException.class, () -> connectivity.addEdge(bus1, bus2, lfBranch));
            assertEquals("This implementation does not support incremental connectivity: edges cannot be added once that connectivity is saved", e.getMessage());
        }
    }

    private void testNonConnectedComponents(AbstractGraphConnectivity<LfBus, LfBranch> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l48");

        assertEquals(createVerticesSet("b4_vl_0", "b5_vl_0", "b6_vl_0", "b7_vl_0", "b8_vl_0", "b9_vl_0", "b10_vl_0"),
            connectivity.getNonConnectedVertices(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(createVerticesSet("b1_vl_0", "b2_vl_0", "b3_vl_0", "b8_vl_0", "b9_vl_0", "b10_vl_0"),
            connectivity.getNonConnectedVertices(lfNetwork.getBusById("b6_vl_0")));
        assertEquals(createVerticesSet("b4_vl_0", "b5_vl_0", "b6_vl_0", "b7_vl_0", "b1_vl_0", "b2_vl_0", "b3_vl_0"),
            connectivity.getNonConnectedVertices(lfNetwork.getBusById("b10_vl_0")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> connectivity.getNonConnectedVertices(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private void testConnectedComponents(GraphConnectivity<LfBus, LfBranch> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l56", "l57");

        assertEquals(createVerticesSet("b1_vl_0", "b2_vl_0", "b3_vl_0"),
            connectivity.getConnectedComponent(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(createVerticesSet("b6_vl_0", "b7_vl_0"),
            connectivity.getConnectedComponent(lfNetwork.getBusById("b6_vl_0")));
        assertEquals(createVerticesSet("b4_vl_0", "b5_vl_0", "b8_vl_0", "b9_vl_0", "b10_vl_0"),
            connectivity.getConnectedComponent(lfNetwork.getBusById("b10_vl_0")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> connectivity.getConnectedComponent(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private void cutBranches(GraphConnectivity<LfBus, LfBranch> connectivity, String... branches) {
        connectivity.startTemporaryChanges();
        Arrays.stream(branches).map(lfNetwork::getBranchById).forEach(connectivity::removeEdge);
    }

    private void updateConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        for (LfBus lfBus : lfNetwork.getBuses()) {
            connectivity.addVertex(lfBus);
        }
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
        }
    }

    private Set<LfBus> createVerticesSet(String... busIds) {
        return Arrays.stream(busIds).map(lfNetwork::getBusById).collect(Collectors.toSet());
    }
}
