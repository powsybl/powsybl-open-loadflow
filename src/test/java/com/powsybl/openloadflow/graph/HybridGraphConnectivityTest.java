/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class HybridGraphConnectivityTest {

    private record Vertex(int num) {
        int getNum() {
            return num;
        }
    }

    private record Edge(String id) {
    }

    @Test
    void testSwitchingAndNesting() {
        HybridGraphConnectivity<Vertex, Edge> connectivity = new HybridGraphConnectivity<>(Vertex::getNum);

        Vertex v1 = new Vertex(0);
        Vertex v2 = new Vertex(1);
        Vertex v3 = new Vertex(2);
        Edge e12 = new Edge("e12");
        Edge e23 = new Edge("e23");

        connectivity.addVertex(v1);
        connectivity.addVertex(v2);
        connectivity.addVertex(v3);
        connectivity.addEdge(v1, v2, e12);
        connectivity.addEdge(v2, v3, e23);

        // initially no mode until first query
        assertEquals(1, connectivity.getNbConnectedComponents());

        // test DECREMENTAL mode (only removals)
        connectivity.startTemporaryChanges();
        connectivity.removeEdge(e12);
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());

        // test nesting and switching to FULL mode (addition)
        connectivity.startTemporaryChanges();
        Edge e13 = new Edge("e13");
        connectivity.addEdge(v1, v3, e13);
        assertEquals(1, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.FULL, connectivity.getMode());

        // test undoing nested change goes back to previous state and mode
        connectivity.undoTemporaryChanges();
        assertEquals(2, connectivity.getNbConnectedComponents());
        // after undo, mode is invalidated, next query will re-evaluate
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());

        // test switching to FULL mode in the first level
        connectivity.undoTemporaryChanges();
        connectivity.startTemporaryChanges();
        Edge e13Bis = new Edge("e13_bis");
        connectivity.addEdge(v1, v3, e13Bis);
        assertEquals(1, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.FULL, connectivity.getMode());

        connectivity.undoTemporaryChanges();
        assertEquals(1, connectivity.getNbConnectedComponents());
    }

    @Test
    void testDeepNesting() {
        HybridGraphConnectivity<Vertex, Edge> connectivity = new HybridGraphConnectivity<>(Vertex::getNum);
        Vertex v1 = new Vertex(0);
        Vertex v2 = new Vertex(1);
        Edge e12 = new Edge("e12");
        connectivity.addVertex(v1);
        connectivity.addVertex(v2);
        connectivity.addEdge(v1, v2, e12);

        connectivity.startTemporaryChanges(); // level 1
        connectivity.removeEdge(e12);
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());

        connectivity.startTemporaryChanges(); // level 2
        // stay decremental
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());

        connectivity.startTemporaryChanges(); // level 3
        Edge e12Bis = new Edge("e12Bis");
        connectivity.addEdge(v1, v2, e12Bis);
        assertEquals(1, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.FULL, connectivity.getMode());

        connectivity.undoTemporaryChanges(); // back to level 2
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());

        connectivity.undoTemporaryChanges(); // back to level 1
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());

        connectivity.undoTemporaryChanges(); // back to base
        assertEquals(1, connectivity.getNbConnectedComponents());
    }

    @Test
    void testAddVertexDuringTemporaryChangesThrowsException() {
        HybridGraphConnectivity<Vertex, Edge> connectivity = new HybridGraphConnectivity<>(Vertex::num);
        Vertex v1 = new Vertex(0);
        connectivity.addVertex(v1);

        connectivity.startTemporaryChanges();
        Vertex v2 = new Vertex(1);
        PowsyblException e = assertThrows(PowsyblException.class, () -> connectivity.addVertex(v2));
        assertEquals("HybridGraphConnectivity does not support adding vertices after the initial graph construction", e.getMessage());
    }

    @Test
    void testRemoveAndReaddEdgeAcrossLevels() {
        HybridGraphConnectivity<Vertex, Edge> connectivity = new HybridGraphConnectivity<>(Vertex::num);
        Vertex v1 = new Vertex(0);
        Vertex v2 = new Vertex(1);
        Edge e12 = new Edge("e12");
        connectivity.addVertex(v1);
        connectivity.addVertex(v2);
        connectivity.addEdge(v1, v2, e12);

        connectivity.startTemporaryChanges(); // level 1
        connectivity.removeEdge(e12);

        connectivity.startTemporaryChanges(); // level 2
        connectivity.addEdge(v1, v2, e12);

        assertEquals(1, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.FULL, connectivity.getMode());

        connectivity.undoTemporaryChanges(); // back to level 1
        assertEquals(2, connectivity.getNbConnectedComponents());
        assertEquals(HybridGraphConnectivity.Mode.DECREMENTAL, connectivity.getMode());
    }
}
