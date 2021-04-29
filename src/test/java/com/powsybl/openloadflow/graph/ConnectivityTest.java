/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.ComponentConstants;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Gaël Macherel <gael.macherel at artelys.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class ConnectivityTest {

    private LfNetwork lfNetwork;

    @BeforeEach
    void setup() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new FirstSlackBusSelector());
        lfNetwork = lfNetworks.stream().filter(n -> n.getNum() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();
    }

    @Test
    void testConnectivity() {
        testConnectivity(new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        testConnectivity(new EvenShiloachGraphDecrementalConnectivity<>());
    }

    @Test
    void testReducedMainComponent() {
        // Testing the connectivity when the main component is suffering cuts so that it becomes smaller than a newly
        // created connected component.
        testReducedMainComponent(new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        testReducedMainComponent(new EvenShiloachGraphDecrementalConnectivity<>());
    }

    @Test
    void testReaddEdge() {
        // Testing cutting an edge then adding it back
        testReaddEdge(new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        testReaddEdge(new EvenShiloachGraphDecrementalConnectivity<>());
    }

    @Test
    void testNonConnected() {
        // Testing with a non-connected graph
        GraphDecrementalConnectivity<LfBus> connectivity = new EvenShiloachGraphDecrementalConnectivity<>();
        for (LfBus lfBus : lfNetwork.getBuses()) {
            connectivity.addVertex(lfBus);
        }
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            if (!lfBranch.getId().equals("l48")) {
                connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2());
            }
        }
        PowsyblException e = assertThrows(PowsyblException.class, connectivity::getSmallComponents);
        assertEquals("Algorithm not implemented for a network with several connected components at start", e.getMessage());
    }

    @Test
    void testNonConnectedComponents() {
        testNonConnectedComponents(new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        testNonConnectedComponents(new EvenShiloachGraphDecrementalConnectivity<>());
        testNonConnectedComponents(new MinimumSpanningTreeGraphDecrementalConnectivity<>());
    }

    @Test
    void testConnectedComponents() {
        testConnectedComponents(new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        testConnectedComponents(new EvenShiloachGraphDecrementalConnectivity<>());
        testConnectedComponents(new MinimumSpanningTreeGraphDecrementalConnectivity<>());
    }

    private void testConnectivity(GraphDecrementalConnectivity<LfBus> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l48");

        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
        assertEquals(2, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
        assertEquals(2, connectivity.getSmallComponents().size());

        AssertionError e = assertThrows(AssertionError.class, () -> connectivity.getComponentNumber(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private void testReducedMainComponent(GraphDecrementalConnectivity<LfBus> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l48", "l56", "l57", "l67");

        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(2, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
        assertEquals(4, connectivity.getSmallComponents().size());
    }

    private void testReaddEdge(GraphDecrementalConnectivity<LfBus> connectivity) {
        updateConnectivity(connectivity);

        String branchId = "l34";
        LfBranch lfBranch = lfNetwork.getBranchById(branchId);
        cutBranches(connectivity, branchId);

        assertEquals(1, connectivity.getSmallComponents().size());
        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b1_vl_0")));
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));

        connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2());
        assertEquals(0, connectivity.getSmallComponents().size());
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b1_vl_0")));
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));

        cutBranches(connectivity, "l48");
        assertEquals(1, connectivity.getSmallComponents().size());
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
    }

    private void testNonConnectedComponents(GraphDecrementalConnectivity<LfBus> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l48");

        assertEquals(createVerticesSet("b4_vl_0", "b5_vl_0", "b6_vl_0", "b7_vl_0", "b8_vl_0", "b9_vl_0", "b10_vl_0"),
            connectivity.getNonConnectedVertices(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(createVerticesSet("b1_vl_0", "b2_vl_0", "b3_vl_0", "b8_vl_0", "b9_vl_0", "b10_vl_0"),
            connectivity.getNonConnectedVertices(lfNetwork.getBusById("b6_vl_0")));
        assertEquals(createVerticesSet("b4_vl_0", "b5_vl_0", "b6_vl_0", "b7_vl_0", "b1_vl_0", "b2_vl_0", "b3_vl_0"),
            connectivity.getNonConnectedVertices(lfNetwork.getBusById("b10_vl_0")));

        AssertionError e = assertThrows(AssertionError.class, () -> connectivity.getNonConnectedVertices(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private void testConnectedComponents(GraphDecrementalConnectivity<LfBus> connectivity) {
        updateConnectivity(connectivity);
        cutBranches(connectivity, "l34", "l56", "l57");

        assertEquals(createVerticesSet("b1_vl_0", "b2_vl_0", "b3_vl_0"),
            connectivity.getConnectedComponent(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(createVerticesSet("b6_vl_0", "b7_vl_0"),
            connectivity.getConnectedComponent(lfNetwork.getBusById("b6_vl_0")));
        assertEquals(createVerticesSet("b4_vl_0", "b5_vl_0", "b8_vl_0", "b9_vl_0", "b10_vl_0"),
            connectivity.getConnectedComponent(lfNetwork.getBusById("b10_vl_0")));

        AssertionError e = assertThrows(AssertionError.class, () -> connectivity.getConnectedComponent(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private void cutBranches(GraphDecrementalConnectivity<LfBus> connectivity, String... branches) {
        Arrays.stream(branches).map(lfNetwork::getBranchById).forEach(lfBranch -> connectivity.cut(lfBranch.getBus2(), lfBranch.getBus1()));
    }

    private void updateConnectivity(GraphDecrementalConnectivity<LfBus> connectivity) {
        for (LfBus lfBus : lfNetwork.getBuses()) {
            connectivity.addVertex(lfBus);
        }
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2());
        }
    }

    private Set<LfBus> createVerticesSet(String... busIds) {
        return Arrays.stream(busIds).map(lfNetwork::getBusById).collect(Collectors.toSet());
    }
}
