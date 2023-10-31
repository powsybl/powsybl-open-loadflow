/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class ConnectivityTest {

    @Test
    void circleTest() {
        circleTest(new NaiveGraphConnectivity<>(s -> Integer.parseInt(s) - 1));
        circleTest(new EvenShiloachGraphDecrementalConnectivity<>());
        circleTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void loopCircleTest() {
        loopCircleTest(new NaiveGraphConnectivity<>(s -> Integer.parseInt(s) - 1));
        loopCircleTest(new EvenShiloachGraphDecrementalConnectivity<>());
        loopCircleTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void saveResetTest() {
        saveResetTest(new NaiveGraphConnectivity<>(v -> v - 1));
        saveResetTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void setMainComponentVertexTest() {
        setMainComponentVertexTest(new NaiveGraphConnectivity<>(v -> v - 1));
        setMainComponentVertexTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void exceptionsTest() {
        exceptionsTest(new NaiveGraphConnectivity<>(v -> v - 1));
        exceptionsTest(new EvenShiloachGraphDecrementalConnectivity<>());
        exceptionsTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void multipleEdgesTest() {
        multipleEdgesTest(new NaiveGraphConnectivity<>(s -> Integer.parseInt(s) - 1), true);
        multipleEdgesTest(new EvenShiloachGraphDecrementalConnectivity<>(), false);
        multipleEdgesTest(new MinimumSpanningTreeGraphConnectivity<>(), true);
    }

    @Test
    void removeThenAddEdgesTest() {
        removeThenAddEdgesTest(new NaiveGraphConnectivity<>(v -> v - 1));
        removeThenAddEdgesTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    private void circleTest(GraphConnectivity<String, String> c) {
        String o1 = "1";
        String o2 = "2";
        String o3 = "3";
        String o4 = "4";
        String e12 = "1-2";
        String e23 = "2-3";
        String e34 = "3-4";
        String e41 = "4-1";
        c.addVertex(o1);
        c.addVertex(o2);
        c.addVertex(o3);
        c.addVertex(o4);
        c.addEdge(o1, o2, e12);
        c.addEdge(o2, o3, e23);
        c.addEdge(o3, o4, e34);
        c.addEdge(o4, o1, e41);

        c.startTemporaryChanges();
        c.removeEdge(e12);
        assertEquals(1, c.getNbConnectedComponents());
        assertTrue(c.getEdgesAddedToMainComponent().isEmpty());
        assertEquals(Set.of(e12), c.getEdgesRemovedFromMainComponent());
        assertTrue(c.getVerticesAddedToMainComponent().isEmpty());
        assertTrue(c.getVerticesRemovedFromMainComponent().isEmpty());
    }

    private void loopCircleTest(GraphConnectivity<String, String> c) {
        String o1 = "1";
        String o2 = "2";
        String o3 = "3";
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        c.addVertex(o1);
        c.addVertex(o2);
        c.addVertex(o3);
        c.addEdge(o1, o1, e11);
        c.addEdge(o1, o2, e12);
        c.addEdge(o2, o3, e23);
        c.addEdge(o3, o1, e31);

        c.startTemporaryChanges();
        c.removeEdge(e11);
        assertEquals(1, c.getNbConnectedComponents());
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11), c.getEdgesRemovedFromMainComponent());

        c.undoTemporaryChanges();
        c.startTemporaryChanges();
        c.removeEdge(e12);
        assertEquals(1, c.getNbConnectedComponents());
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e12), c.getEdgesRemovedFromMainComponent());

        c.removeEdge(e31);
        assertEquals(2, c.getNbConnectedComponents());
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(o1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e31, e12), c.getEdgesRemovedFromMainComponent());
    }

    private void saveResetTest(GraphConnectivity<Integer, String> c) {
        Integer v1 = 1;
        Integer v2 = 2;
        Integer v3 = 3;
        Integer v4 = 4;
        Integer v5 = 5;
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        String e45 = "4-5";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addVertex(v3);
        c.addVertex(v4);
        c.addVertex(v5);
        c.addEdge(v1, v1, e11);
        c.addEdge(v1, v2, e12);
        c.addEdge(v2, v3, e23);
        c.addEdge(v3, v1, e31);
        c.addEdge(v4, v5, e45);
        //  |-------|
        //  1---2---3   4---5
        // |_|

        c.startTemporaryChanges();
        c.removeEdge(e12);
        c.removeEdge(e31);
        assertEquals(3, c.getNbConnectedComponents());
        assertEquals(Set.of(v1), c.getConnectedComponent(v1));
        assertEquals(Set.of(v2, v3), c.getConnectedComponent(v2));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3   4---5
        // |_|

        c.startTemporaryChanges();
        c.removeEdge(e23);
        c.addEdge(v1, v2, e12);
        c.removeEdge(e11);
        String e34 = "3-4";
        c.addEdge(v3, v4, e34);
        assertEquals(2, c.getNbConnectedComponents());
        assertEquals(Set.of(v1, v2), c.getConnectedComponent(v1));
        assertEquals(Set.of(v3, v4, v5), c.getConnectedComponent(v5));
        assertEquals(Set.of(e34, e45), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v4, v5), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e23), c.getEdgesRemovedFromMainComponent());
        //  1---2   3---4---5

        c.undoTemporaryChanges();
        assertEquals(3, c.getNbConnectedComponents());
        assertEquals(Set.of(v1), c.getConnectedComponent(v1));
        assertEquals(Set.of(v2, v3), c.getConnectedComponent(v2));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3   4---5
        // |_|

        c.startTemporaryChanges();
        c.addEdge(v1, v2, e12);
        assertEquals(2, c.getNbConnectedComponents());
        assertEquals(Set.of(v1, v2, v3), c.getConnectedComponent(v2));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));
        assertEquals(Set.of(e11, e12), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  1---2---3   4---5
        // |_|

        c.undoTemporaryChanges();
        assertEquals(3, c.getNbConnectedComponents());
        assertEquals(Set.of(v1), c.getConnectedComponent(v1));
        assertEquals(Set.of(v2, v3), c.getConnectedComponent(v2));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3   4---5
        // |_|

        c.startTemporaryChanges();
        String e14 = "1-4";
        c.addEdge(v1, v4, e14);
        c.addEdge(v3, v4, e34);
        assertEquals(1, c.getNbConnectedComponents());
        assertEquals(Set.of(e11, e14, e34, e45), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1, v4, v5), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  1   2---3---4---5
        // |_|

        Integer v6 = 6;
        c.addVertex(v6);
        assertEquals(2, c.getNbConnectedComponents());
        assertEquals(Set.of(v6), c.getConnectedComponent(v6));
        assertEquals(Set.of(e11, e14, e34, e45), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1, v4, v5), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  1   2---3---4---5    6
        // |_|

        c.undoTemporaryChanges();
        c.undoTemporaryChanges();

        c.startTemporaryChanges();
        assertEquals(2, c.getNbConnectedComponents());
        assertEquals(Set.of(v1, v2, v3), c.getConnectedComponent(v1));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());

    }

    private void setMainComponentVertexTest(GraphConnectivity<Integer, String> c) {
        Integer v1 = 1;
        Integer v2 = 2;
        Integer v3 = 3;
        Integer v4 = 4;
        Integer v5 = 5;
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        String e45 = "4-5";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addVertex(v3);
        c.addVertex(v4);
        c.addVertex(v5);
        c.addEdge(v1, v1, e11);
        c.addEdge(v1, v2, e12);
        c.addEdge(v2, v3, e23);
        c.addEdge(v3, v1, e31);
        c.addEdge(v4, v5, e45);
        c.setMainComponentVertex(v5);
        //  |-------|
        //  1---2---3   4---5
        // |_|

        c.startTemporaryChanges();
        c.removeEdge(e12);
        c.removeEdge(e31);
        String e15 = "1-5";
        c.addEdge(v1, v4, e15);
        assertEquals(Set.of(e11, e15), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  |---------------|
        //  1   2---3   4---5
        // |_|

        c.startTemporaryChanges();
        c.removeEdge(e23);
        c.addEdge(v1, v2, e12);
        c.removeEdge(e11);
        String e34 = "3-4";
        c.addEdge(v3, v4, e34);
        assertEquals(Set.of(e12, e34), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v2, v3), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11), c.getEdgesRemovedFromMainComponent());
        //  |---------------|
        //  1---2   3---4---5

        c.undoTemporaryChanges();
        //  |---------------|
        //  1   2---3   4---5
        // |_|

        c.startTemporaryChanges();
        String e14 = "1-4";
        c.addEdge(v1, v4, e14);
        c.addEdge(v3, v4, e34);
        c.removeEdge(e45);
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1, v4), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e15, e45), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  1   2---3---4   5
        // |_|

        Integer v6 = 6;
        c.addVertex(v6);
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1, v4), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e15, e45), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  1   2---3---4   5    6
        // |_|

        c.undoTemporaryChanges();
        c.undoTemporaryChanges();

        c.startTemporaryChanges();
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
    }

    private void exceptionsTest(GraphConnectivity<Integer, String> c) {
        Integer v1 = 1;
        Integer v2 = 2;
        String e12 = "1-2";
        String e22 = "2-2";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addEdge(v1, v2, e12);
        c.addEdge(v2, v2, e22);
        c.removeEdge(e22);

        PowsyblException e1 = assertThrows(PowsyblException.class, c::getNbConnectedComponents);
        assertEquals("Cannot compute connectivity without a saved state, please call GraphConnectivity::startTemporaryChanges at least once beforehand",
                e1.getMessage());

        PowsyblException e2 = assertThrows(PowsyblException.class, c::undoTemporaryChanges);
        assertEquals("Cannot reset, no remaining saved connectivity", e2.getMessage());

        PowsyblException e3 = assertThrows(PowsyblException.class, c::undoTemporaryChanges);
        assertEquals("Cannot reset, no remaining saved connectivity", e3.getMessage());

        c.setMainComponentVertex(v1);
        c.setMainComponentVertex(v2); // changing the main component vertex before starting temporary changes is accepted
        c.startTemporaryChanges();
        c.setMainComponentVertex(v2); // setting again the same vertex is accepted
        PowsyblException e4 = assertThrows(PowsyblException.class, () -> c.setMainComponentVertex(v1));
        assertEquals("Cannot change main component vertex after starting temporary changes", e4.getMessage());
    }

    private void multipleEdgesTest(GraphConnectivity<String, String> c, boolean incrementalSupport) {
        String o1 = "1";
        String o2 = "2";
        String o3 = "3";
        String e12 = "1-2";
        String e23 = "2-3";
        c.addVertex(o1);
        c.addVertex(o2);
        c.addVertex(o3);
        c.addEdge(o1, o2, e12);
        c.addEdge(o1, o2, e12);
        c.addEdge(o2, o3, e23);
        // 1---2---3

        c.startTemporaryChanges();
        assertEquals(1, c.getNbConnectedComponents());
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        // 1---2---3

        c.removeEdge(e12);
        assertEquals(2, c.getNbConnectedComponents());
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(o1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e12), c.getEdgesRemovedFromMainComponent());
        // 1   2---3

        // Non-effective modifications
        c.removeEdge(e12);
        c.removeEdge(e12);
        c.addVertex(o1);
        assertEquals(2, c.getNbConnectedComponents());

        if (incrementalSupport) {
            c.addEdge(o1, o2, e12);
            assertEquals(1, c.getNbConnectedComponents());
            assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
            assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
            assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
            assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
            // 1---2---3
        }
    }

    private void removeThenAddEdgesTest(GraphConnectivity<Integer, String> c) {
        IntStream.range(1, 6).forEach(c::addVertex);
        IntStream.range(1, 5).forEach(i -> c.addEdge(i, i + 1, i + "-" + (i + 1)));
        // 1---2---3---4---5

        c.startTemporaryChanges();
        c.removeEdge("2-3");
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of("1-2", "2-3"), c.getEdgesRemovedFromMainComponent());
        // 1---2   3---4---5

        c.removeEdge("1-2");
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of("1-2", "2-3"), c.getEdgesRemovedFromMainComponent());
        // 1   2   3---4---5

        c.addEdge(1, 2, "1-2");
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of("1-2", "2-3"), c.getEdgesRemovedFromMainComponent());
        // 1---2   3---4---5

        c.addVertex(6);
        c.addEdge(5, 6, "5-6");
        assertEquals(Set.of("5-6"), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(6), c.getVerticesAddedToMainComponent());
        // 1---2   3---4---5---6

        c.removeEdge("5-6");
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of("1-2", "2-3"), c.getEdgesRemovedFromMainComponent());
        // 1---2   3---4---5   6

        c.removeEdge("1-2");
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of("1-2", "2-3"), c.getEdgesRemovedFromMainComponent());
        // 1   2   3---4---5   6

        c.addEdge(1, 2, "1-2");
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of("1-2", "2-3"), c.getEdgesRemovedFromMainComponent());
        // 1---2   3---4---5   6
    }
}
