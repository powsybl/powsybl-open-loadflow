/**
 * Copyright (c) 2021-2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.graph.derivative.Delta2DTreeStandalone;
import com.powsybl.openloadflow.graph.derivative.Delta2ReplaceWithBestDTreeStandalone;
import com.powsybl.openloadflow.graph.derivative.ReplaceWithBestDTreeStandalone;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivity;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class ConnectivityTest {

    <V> void assertComponentEquals(Set<V> expectedElements, int expectedNum, Component<V> component) {
        assertEquals(expectedElements, component);
        assertEquals(expectedElements, component.toOwnedSet());
        assertEquals(expectedNum, component.getNumber());
    }

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
        Integer v6 = 6;
        Integer v7 = 7;
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        String e45 = "4-5";
        String e36 = "3-6";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addVertex(v3);
        c.addVertex(v4);
        c.addVertex(v5);
        c.addVertex(v6);
        c.addEdge(v1, v1, e11);
        c.addEdge(v1, v2, e12);
        c.addEdge(v2, v3, e23);
        c.addEdge(v3, v1, e31);
        c.addEdge(v4, v5, e45);
        c.addEdge(v3, v6, e36);
        //  |-------|
        //  1---2---3---6   4---5
        // |_|

        c.startTemporaryChanges();
        c.removeEdge(e12);
        c.removeEdge(e31);
        assertEquals(3, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1), 2, c.getConnectedComponent(v1));
        assertComponentEquals(Set.of(v2, v3, v6), 0, c.getConnectedComponent(v2));
        assertComponentEquals(Set.of(v4, v5), 1, c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3---6   4---5
        // |_|

        c.startTemporaryChanges();
        c.removeEdge(e23);
        c.addEdge(v1, v2, e12);
        c.removeEdge(e11);
        String e34 = "3-4";
        c.addEdge(v3, v4, e34);
        assertEquals(2, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1, v2), 1, c.getConnectedComponent(v1));
        assertComponentEquals(Set.of(v3, v4, v5, v6), 0, c.getConnectedComponent(v5));
        assertEquals(Set.of(e34, e45), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v4, v5), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e23), c.getEdgesRemovedFromMainComponent());
        //  1---2   6---3---4---5

        c.undoTemporaryChanges();
        assertEquals(3, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1), 2, c.getConnectedComponent(v1));
        assertComponentEquals(Set.of(v2, v3, v6), 0, c.getConnectedComponent(v2));
        assertComponentEquals(Set.of(v4, v5), 1, c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3---6   4---5
        // |_|

        c.startTemporaryChanges();
        c.addEdge(v1, v2, e12);
        assertEquals(2, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1, v2, v3, v6), 0, c.getConnectedComponent(v2));
        assertComponentEquals(Set.of(v4, v5), 1, c.getConnectedComponent(v5));
        assertEquals(Set.of(e11, e12), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  1---2---3---6   4---5
        // |_|

        c.undoTemporaryChanges();
        assertEquals(3, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1), 2, c.getConnectedComponent(v1));
        assertComponentEquals(Set.of(v2, v3, v6), 0, c.getConnectedComponent(v2));
        assertComponentEquals(Set.of(v4, v5), 1, c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3---6   4---5
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

        c.addVertex(v7);
        assertEquals(2, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v7), 1, c.getConnectedComponent(v7));
        assertEquals(Set.of(e11, e14, e34, e45), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v1, v4, v5), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  |-----------|
        //  1   2---3---4---5    7
        // |_|

        c.undoTemporaryChanges();
        assertEquals(3, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1), 2, c.getConnectedComponent(v1));
        assertComponentEquals(Set.of(v2, v3, v6), 0, c.getConnectedComponent(v2));
        assertComponentEquals(Set.of(v4, v5), 1, c.getConnectedComponent(v5));
        assertEquals(Collections.emptySet(), c.getEdgesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(v1), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e11, e12, e31), c.getEdgesRemovedFromMainComponent());
        //  1   2---3---6   4---5
        // |_|

        c.startTemporaryChanges();
        String e27 = "2-7";
        String e67 = "6-7";
        c.addVertex(7);
        c.addEdge(v2, v7, e27);
        c.addEdge(v6, v7, e67);
        assertEquals(3, c.getNbConnectedComponents());
        assertEquals(Set.of(e27, e67), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(v7), c.getVerticesAddedToMainComponent());
        assertEquals(Collections.emptySet(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Collections.emptySet(), c.getEdgesRemovedFromMainComponent());
        //  1   2---3---6   4---5
        // |_|  |---7---|

        c.undoTemporaryChanges();
        c.undoTemporaryChanges();

        c.startTemporaryChanges();
        assertEquals(2, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(v1, v2, v3, v6), 0, c.getConnectedComponent(v1));
        assertComponentEquals(Set.of(v4, v5), 1, c.getConnectedComponent(v5));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideNonRestrictedConnectivities")
    void quickStartTemporaryChangesTest(GraphConnectivity<Integer, String> c) {
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        String e45 = "4-5";
        String e36 = "3-6";
        String e16 = "1-6";

        c.addVertex(1);
        c.addVertex(2);
        c.addVertex(3);
        c.addVertex(4);
        c.addVertex(5);
        c.addVertex(6);
        c.addEdge(1, 1, e11);
        c.addEdge(1, 2, e12);
        c.addEdge(2, 3, e23);
        c.addEdge(3, 1, e31);
        c.addEdge(4, 5, e45);
        c.addEdge(3, 6, e36);
        //  |-------|
        //  1---2---3---6   4---5
        // |_|

        c.startTemporaryChanges(false);
        c.removeEdge(e36);
        //  |-------|
        //  1---2---3   6   4---5
        // |_|

        assertThrows(PowsyblException.class, c::getVerticesRemovedFromMainComponent);
        assertThrows(PowsyblException.class, c::getEdgesRemovedFromMainComponent);
        assertThrows(PowsyblException.class, c::getVerticesAddedToMainComponent);
        assertThrows(PowsyblException.class, c::getEdgesAddedToMainComponent);

        c.startTemporaryChanges(true);
        c.removeEdge(e23);
        c.removeEdge(e31);
        c.addEdge(1, 6, e16);
        //  |-------|
        //  1---2   6   3   4---5
        // |_|

        assertEquals(Set.of(e16), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(6), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(3), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e23, e31), c.getEdgesRemovedFromMainComponent());

        c.undoTemporaryChanges();

        assertThrows(PowsyblException.class, c::getVerticesRemovedFromMainComponent);
        assertThrows(PowsyblException.class, c::getEdgesRemovedFromMainComponent);
        assertThrows(PowsyblException.class, c::getVerticesAddedToMainComponent);
        assertThrows(PowsyblException.class, c::getEdgesAddedToMainComponent);
        assertEquals(3, c.getNbConnectedComponents());
        assertComponentEquals(Set.of(1, 2, 3), 0, c.getConnectedComponent(1));
        assertComponentEquals(Set.of(6), 2, c.getConnectedComponent(6));
        assertComponentEquals(Set.of(4, 5), 1, c.getConnectedComponent(4));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideNonRestrictedConnectivities")
    void testTopologicalComparisonsWhenMainComponentChanges(GraphConnectivity<Integer, String> c) {
        String e12 = "1-2";
        String e34 = "3-4";
        String e45 = "4-5";

        c.addVertex(1);
        c.addVertex(2);
        c.addVertex(3);
        c.addVertex(4);
        c.addVertex(5);
        c.addEdge(1, 2, e12);
        c.startTemporaryChanges();
        // 1---2   3   4   5

        c.addEdge(3, 4, e34);
        c.addEdge(4, 5, e45);
        // 1---2   3---4---5

        assertEquals(Set.of(3, 4, 5), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(1, 2), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(e34, e45), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(e12), c.getEdgesRemovedFromMainComponent());

        c.removeEdge(e34);
        c.removeEdge(e45);
        // 1---2   3   4   5

        assertEquals(Set.of(), c.getVerticesAddedToMainComponent());
        assertEquals(Set.of(), c.getVerticesRemovedFromMainComponent());
        assertEquals(Set.of(), c.getEdgesAddedToMainComponent());
        assertEquals(Set.of(), c.getEdgesRemovedFromMainComponent());
    }

    <V> void assertConnected(GraphConnectivity<V, ?> c, V u, V v) {
        assertTrue(c.connected(u, v));
        assertTrue(c.connected(v, u));
    }

    <V> void assertDisconnected(GraphConnectivity<V, ?> c, V u, V v) {
        assertFalse(c.connected(u, v));
        assertFalse(c.connected(v, u));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAllConnectivities")
    void testConnected(GraphConnectivity<Integer, String> c) {
        c.addVertex(1);
        c.addVertex(2);
        c.addVertex(3);
        c.addVertex(4);
        c.addVertex(5);

        if (!(c instanceof EvenShiloachGraphDecrementalConnectivity)) {
            // step 1: graph growth
            c.startTemporaryChanges();

            assertDisconnected(c, 1, 2);
            c.addEdge(1, 2, "1-2");
            assertConnected(c, 1, 2);

            assertDisconnected(c, 2, 3);
            c.addEdge(2, 3, "2-3");
            assertConnected(c, 2, 3);
            assertConnected(c, 1, 2);

            c.addEdge(3, 1, "3-1");
            assertConnected(c, 2, 3);
            assertConnected(c, 1, 2);

            assertDisconnected(c, 4, 5);
            c.addEdge(4, 5, "4-5");
            assertConnected(c, 4, 5);

            assertDisconnected(c, 3, 4);
            c.addEdge(3, 4, "3-4");
        } else {
            // with EvenShiloach we can only test removal
            c.addEdge(1, 2, "1-2");
            c.addEdge(2, 3, "2-3");
            c.addEdge(3, 1, "3-1");
            c.addEdge(4, 5, "4-5");
            c.addEdge(3, 4, "3-4");

            c.startTemporaryChanges();
        }

        // 1---2---3---4---5
        // |_______|

        // fully connected
        for (int i = 1; i < 5; i++) {
            for (int j = 1; j <= i; j++) {
                assertConnected(c, i, j);
            }
        }

        // step 2: graph decline
        c.removeEdge("3-4");
        assertDisconnected(c, 1, 4);
        assertDisconnected(c, 1, 5);
        assertDisconnected(c, 2, 4);
        assertDisconnected(c, 2, 5);
        assertDisconnected(c, 3, 4);
        assertDisconnected(c, 3, 5);
        assertConnected(c, 1, 2);
        assertConnected(c, 2, 3);
        assertConnected(c, 4, 5);
        // 1---2---3   4---5
        // |_______|

        c.removeEdge("1-2");
        assertConnected(c, 1, 2);
        assertConnected(c, 2, 3);
        // 2---3---1   4---5

        c.removeEdge("4-5");
        assertDisconnected(c, 4, 5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAllConnectivities")
    void testConnectedExceptions(GraphConnectivity<Integer, String> c) {
        c.addVertex(1);
        c.addVertex(2);
        c.addEdge(1, 2, "1-2");
        c.startTemporaryChanges();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> c.connected(0, 1));
        assertEquals("given vertex 0 is not in the graph", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> c.connected(null, 1));
        assertEquals("given vertex null is not in the graph", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> c.connected(1, 3));
        assertEquals("given vertex 3 is not in the graph", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> c.connected(1, null));
        assertEquals("given vertex null is not in the graph", e.getMessage());
    }

    private static Stream<Arguments> provideNonRestrictedConnectivities() {
        return Stream.of(
                Arguments.of(new NaiveGraphConnectivity<Integer, String>(v -> v - 1)),
                Arguments.of(new HolmEtAlGraphConnectivity<>()),
                Arguments.of(new NewHolmGraphConnectivity<>()),
                Arguments.of(new ETTreeGraphConnectivity<>()),
                Arguments.of(new DTreeGraphConnectivity<>()),
                Arguments.of(new DTreeStandalone<>()),
                Arguments.of(new Delta2DTreeStandalone<>()),
                Arguments.of(new Delta2ReplaceWithBestDTreeStandalone<>()),
                Arguments.of(new ReplaceWithBestDTreeStandalone<>()),
                Arguments.of(new IDTreeStandalone<>()),
                Arguments.of(new IndexedDTreeStandalone<>(
                        (Integer v) -> v - 1,
                        new ToIntFunction<String>() {
                            private final TObjectIntMap<String> map = new TObjectIntHashMap<>();

                            @Override
                            public int applyAsInt(String value) {
                                return map.adjustOrPutValue(value, 0, map.size());
                            }
                        })),
                Arguments.of(new DnDTreeStandalone<>()),
                Arguments.of(new OptDTreeStandalone<>()));
    }

    private static Stream<Arguments> provideAllConnectivities() {
        return Stream.of(
                Arguments.of(new NaiveGraphConnectivity<Integer, String>(v -> v - 1)),
                Arguments.of(new EvenShiloachGraphDecrementalConnectivity<>()),
                Arguments.of(new HolmEtAlGraphConnectivity<>()),
                Arguments.of(new NewHolmGraphConnectivity<>()),
                Arguments.of(new ETTreeGraphConnectivity<>()),
                Arguments.of(new DTreeGraphConnectivity<>()),
                Arguments.of(new com.powsybl.openloadflow.graph.dtreepr.DTreeGraphConnectivity<>()),
                Arguments.of(new DTreeStandalone<>()),
                Arguments.of(new Delta2DTreeStandalone<>()),
                Arguments.of(new Delta2ReplaceWithBestDTreeStandalone<>()),
                Arguments.of(new ReplaceWithBestDTreeStandalone<>()),
                Arguments.of(new IDTreeStandalone<>()),
                Arguments.of(new IndexedDTreeStandalone<>(
                        (Integer v) -> v - 1,
                        new ToIntFunction<String>() {
                            private final TObjectIntMap<String> map = new TObjectIntHashMap<>();

                            @Override
                            public int applyAsInt(String value) {
                                return map.adjustOrPutValue(value, 0, map.size());
                            }
                        })),
                Arguments.of(new DnDTreeStandalone<>()),
                Arguments.of(new OptDTreeStandalone<>()));
    }
}
