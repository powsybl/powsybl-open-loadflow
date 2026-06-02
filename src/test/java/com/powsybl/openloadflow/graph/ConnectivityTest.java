/**
 * Copyright (c) 2021-2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.jgrapht.Graph;
import org.jgrapht.generate.ScaleFreeGraphGenerator;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class ConnectivityTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideNonRestrictedConnectivities")
    void setMainComponentVertexExceptionTest(GraphConnectivity<Integer, String> c) {
        Integer v1 = 1;
        Integer v2 = 2;
        Integer v3 = 3;
        String e12 = "1-2";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addVertex(v3);
        c.addEdge(v1, v2, e12);

        c.setMainComponentVertex(v1);
        c.startTemporaryChanges();
        c.setMainComponentVertex(v2); // setting the main component vertex is accepted if already in the main component before
        PowsyblException e4 = assertThrows(PowsyblException.class, () -> c.setMainComponentVertex(v3));
        assertEquals("Cannot take the given vertex as main component vertex! This vertex was outside the main component before starting temporary changes", e4.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAllConnectivities")
    void circleTest(GraphConnectivity<Integer, String> c) {
        int o1 = 1;
        int o2 = 2;
        int o3 = 3;
        int o4 = 4;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAllConnectivities")
    void loopCircleTest(GraphConnectivity<Integer, String> c) {
        int o1 = 1;
        int o2 = 2;
        int o3 = 3;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideNonRestrictedConnectivities")
    void saveResetTest(GraphConnectivity<Integer, String> c) {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideNonRestrictedConnectivities")
    void setMainComponentVertexTest(GraphConnectivity<Integer, String> c) {
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
        String e14 = "1-4";
        c.addEdge(v1, v4, e14);
        assertEquals(Set.of(e11, e14), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
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
        //  |-----------|
        //  1---2   3---4---5

        c.undoTemporaryChanges();
        //  |-----------|
        //  1   2---3   4---5
        // |_|

        c.startTemporaryChanges();
        String e14b = "1-4 duplicate";
        c.addEdge(v1, v4, e14b);
        c.addEdge(v3, v4, e34);
        c.removeEdge(e45);
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1, v4), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e14, e45), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  |-----------|
        //  1   2---3---4   5
        // |_|

        c.setMainComponentVertex(1);
        assertEquals(Set.of(e14b, e23, e34), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v2, v3), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v5), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e45), c.getEdgesRemovedFromMainComponent());

        c.setMainComponentVertex(5);
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1, v4), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e14, e45), c.getEdgesRemovedFromMainComponent());

        c.setMainComponentVertex(1);
        Integer v6 = 6;
        c.addVertex(v6);
        assertEquals(Set.of(e14b, e23, e34), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v2, v3), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v5), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e45), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  |-----------|
        //  1   2---3---4   5    6
        // |_|

        c.undoTemporaryChanges(); // vertex 5 is considered again as main component vertex
        //  |-----------|
        //  1   2---3   4---5
        // |_|
        assertEquals(Set.of(e11, e14), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());

        c.undoTemporaryChanges();

        c.startTemporaryChanges();
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAllConnectivities")
    void exceptionsTest(GraphConnectivity<Integer, String> c) {
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
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAllConnectivities")
    void multipleEdgesTest(GraphConnectivity<Integer, String> c) {
        int o1 = 1;
        int o2 = 2;
        int o3 = 3;
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

        boolean incrementalSupport = !(c instanceof EvenShiloachGraphDecrementalConnectivity);
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideNonRestrictedConnectivities")
    void removeThenAddEdgesTest(GraphConnectivity<Integer, String> c) {
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

    @Test
    void fishTest() {
        //  0     2
        //  |\  ／ |＼
        //  | 1    |  5
        //  |/  ＼ |／
        //  4     3

        HolmEtAlGraphConnectivity<Integer, String> connectivity = new HolmEtAlGraphConnectivity<>();
        for (int i = 0; i < 6; i++) {
            connectivity.addVertex(i);
        }

        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(2, 1, "2-1");
        connectivity.addEdge(3, 1, "3-1");
        connectivity.addEdge(3, 2, "3-2");
        connectivity.addEdge(0, 4, "0-4");
        connectivity.addEdge(1, 4, "1-4");
        connectivity.addEdge(2, 5, "2-5");
        connectivity.addEdge(5, 3, "5-3");

        // order of removal is important
        connectivity.removeEdge("0-1");
        connectivity.removeEdge("2-1");
        connectivity.removeEdge("3-1");
        connectivity.removeEdge("3-2");

        connectivity.startTemporaryChanges();
        assertEquals(2, connectivity.getNbConnectedComponents());
    }

    @Test
    void testDTreeInsertNonTreeEdge() {
        //      0 -- 1
        //      |    |
        // 3 -- 2 -- 5
        //      |
        //      4

        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
        for (int i = 0; i < 6; i++) {
            connectivity.addVertex(i);
        }

        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(2, 0, "2-0");
        connectivity.addEdge(2, 3, "2-3");
        connectivity.addEdge(2, 4, "2-4");
        connectivity.addEdge(1, 5, "1-5");

        connectivity.startTemporaryChanges();
        for (int i = 0; i < 6; i++) {
            assertEquals(0, connectivity.getComponentNumber(i));
        }

        // Adding this edge doesn't affect connectivity.
        // However, it modifies the spanning tree.
        // Before:
        //      0 -- 1
        //      |    |
        // 3 -- 2    5  (5 and 2 not connected)
        //      |
        //      4
        connectivity.addEdge(5, 2, "5-2");
        // After:
        //      0    1   (0 and 1 are still connected, but not in the spanning tree!)
        //      |    |
        // 3 -- 1 -- 5
        //      |
        //      4
        for (int i = 0; i < 6; i++) {
            assertEquals(0, connectivity.getComponentNumber(i));
        }
    }

    @Test
    void randomGraph() {
        for (int size = 1; size < 100; size++) {
            System.out.println(size);
            for (int seed = 0; seed < 1000; seed++) {
                // generate graph
                Graph<Integer, DefaultEdge> graph = generateGraph(size, seed);

                // generate expected results based on NaiveGraphConnectivity
                Sample<Integer, DefaultEdge> sample = new Sample<>(size, seed);
                sample.buildingControlSample = true;
                assertSameResultAsControlSample(graph, new NaiveGraphConnectivity<>(i -> i), sample);
                sample.buildingControlSample = false;

                // test others implementations produce same results as NaiveGraphConnectivity
                List<GraphConnectivity<Integer, DefaultEdge>> toTest = List.of(
                        new HolmEtAlWithoutLevelGraphConnectivity<>(),
                        new HolmEtAlGraphConnectivity<>(),
                        new DTreeGraphConnectivity<>()
                );

                for (GraphConnectivity<Integer, DefaultEdge> connectivity : toTest) {
                    assertSameResultAsControlSample(graph, connectivity, sample);
                }
            }
        }
    }

    private static <V, E> void assertSameResultAsControlSample(Graph<V, E> graph, GraphConnectivity<V, E> connectivity, Sample<V, E> sample) {
        sample.beginTest();

        boolean init = false;

        // add all vertices
        for (V vertex : graph.vertexSet()) {
            connectivity.addVertex(vertex);
            if (!init) {
                connectivity.startTemporaryChanges();
                init = true;
            }

            sample.checkAddVertex(connectivity, vertex);
        }

        // fully connect the graph
        for (E edge : graph.edgeSet()) {
            connectivity.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge), edge);
            sample.checkAddEdge(connectivity, edge);

            // check previously connected edges are still connected
            for (E e2 : graph.edgeSet()) {
                assertEquals(connectivity.getComponentNumber(graph.getEdgeSource(e2)), connectivity.getComponentNumber(graph.getEdgeTarget(e2)), edge.toString());

                if (edge == e2) {
                    break;
                }
            }
        }

        // check the graph is indeed fully connected
        connectivity.startTemporaryChanges();
        for (V v1 : graph.vertexSet()) {
            for (V v2 : graph.vertexSet()) {
                Assertions.assertEquals(connectivity.getComponentNumber(v1), connectivity.getComponentNumber(v2),
                        "(size = " + sample.size + ", seed = " + sample.seed + ")");
            }
        }
        connectivity.undoTemporaryChanges();

        // fully disconnect the graph
        for (E edge : graph.edgeSet()) {
            connectivity.removeEdge(edge);
            sample.checkRemoveEdge(connectivity, edge);

            // check previously disconnected edges are still disconnected
            // TODO: doesn't work
            for (E e2 : graph.edgeSet()) {
                assertNotEquals(connectivity.getComponentNumber(graph.getEdgeSource(e2)), connectivity.getComponentNumber(graph.getEdgeTarget(e2)), edge.toString());

                if (edge == e2) {
                    break;
                }
            }
        }

        sample.endTest();
    }

    static class Sample<V, E> {

        private final int size;
        private final int seed;

        private final List<Integer> nbConnectedComponents = new ArrayList<>();
        private boolean buildingControlSample = true;
        private int step = 0;

        Sample(int size, int seed) {
            this.size = size;
            this.seed = seed;
        }

        public void check(GraphConnectivity<V, E> connectivity, String method) {
            if (buildingControlSample) {
                nbConnectedComponents.add(connectivity.getNbConnectedComponents());
            } else {
                assertEquals(nbConnectedComponents.get(step),
                        connectivity.getNbConnectedComponents(),
                        "%s at step = %d with %s (size = %d, seed = %d)"
                                .formatted(method, step, connectivity.getClass().getSimpleName(), size, seed));
            }
            step++;
        }

        public void checkAddVertex(GraphConnectivity<V, E> connectivity, V vertex) {
            check(connectivity, "addVertex(" + vertex + ")");
        }

        public void checkAddEdge(GraphConnectivity<V, E> connectivity, E edge) {
            check(connectivity, "addEdge(" + edge + ")");
        }

        public void checkRemoveEdge(GraphConnectivity<V, E> connectivity, E edge) {
            check(connectivity, "removeEdge(" + edge + ")");
        }

        public void beginTest() {
            step = 0;
        }

        public void endTest() {
            assertEquals(step, nbConnectedComponents.size(), "sample was not fully tested");
        }
    }

    private Graph<Integer, DefaultEdge> generateGraph(int vertexCount, int seed) {
        Graph<Integer, DefaultEdge> graph = new DefaultUndirectedGraph<>(
                SupplierUtil.createIntegerSupplier(), SupplierUtil.createDefaultEdgeSupplier(),
                false);

        // https://mathworld.wolfram.com/Scale-FreeNetwork.html
        // many vertices with low degree
        // and many vertices with high degree
        ScaleFreeGraphGenerator<Integer, DefaultEdge> gen =
                new ScaleFreeGraphGenerator<>(vertexCount, new Random(seed));
        gen.generateGraph(graph);

        return graph;
    }

    private static Stream<Arguments> provideNonRestrictedConnectivities() {
        return Stream.of(
                Arguments.of(new NaiveGraphConnectivity<Integer, String>(v -> v - 1)),
                Arguments.of(new MinimumSpanningTreeGraphConnectivity<>()),
                Arguments.of(new HolmEtAlGraphConnectivity<>()),
                Arguments.of(new HolmEtAlWithoutLevelGraphConnectivity<>()),
                Arguments.of(new DTreeGraphConnectivity<>()));
    }

    private static Stream<Arguments> provideAllConnectivities() {
        return Stream.of(
                Arguments.of(new NaiveGraphConnectivity<Integer, String>(v -> v - 1)),
                Arguments.of(new EvenShiloachGraphDecrementalConnectivity<>()),
                Arguments.of(new MinimumSpanningTreeGraphConnectivity<>()),
                Arguments.of(new HolmEtAlGraphConnectivity<>()),
                Arguments.of(new HolmEtAlWithoutLevelGraphConnectivity<>()),
                Arguments.of(new DTreeGraphConnectivity<>()));
    }
}
