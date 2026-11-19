/**
 * Copyright (c) 2021-2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Gaël Macherel {@literal <gael.macherel at artelys.com>}
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class GraphConnectivityNonConnectedVerticesTest {

    @Test
    void testNonConnectedComponents() {
        testNonConnectedComponents(new NaiveGraphConnectivity<>(v -> v));
        testNonConnectedComponents(new EvenShiloachGraphDecrementalConnectivity<>());
        testNonConnectedComponents(new DTreeGraphConnectivity<>());
    }

    private <G extends GraphModel<Integer, String>> void testNonConnectedComponents(AbstractGraphConnectivity<Integer, String, G> connectivity) {
        // three groups {0, 1, 2}, {3, 4, 5, 6} and {7, 8, 9} linked by the edges "e23" and "e37"
        for (int v = 0; v < 10; v++) {
            connectivity.addVertex(v);
        }
        addEdge(connectivity, 0, 1);
        addEdge(connectivity, 1, 2);
        addEdge(connectivity, 0, 2);
        addEdge(connectivity, 2, 3);
        addEdge(connectivity, 3, 4);
        addEdge(connectivity, 4, 5);
        addEdge(connectivity, 5, 6);
        addEdge(connectivity, 3, 7);
        addEdge(connectivity, 7, 8);
        addEdge(connectivity, 8, 9);

        connectivity.startTemporaryChanges();
        connectivity.removeEdge("e23");
        connectivity.removeEdge("e37");

        assertEquals(Set.of(3, 4, 5, 6, 7, 8, 9), connectivity.getNonConnectedVertices(2));
        assertEquals(Set.of(0, 1, 2, 7, 8, 9), connectivity.getNonConnectedVertices(5));
        assertEquals(Set.of(3, 4, 5, 6, 0, 1, 2), connectivity.getNonConnectedVertices(9));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> connectivity.getNonConnectedVertices(null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private static void addEdge(GraphConnectivity<Integer, String> connectivity, int v1, int v2) {
        connectivity.addEdge(v1, v2, "e" + v1 + v2);
    }
}
