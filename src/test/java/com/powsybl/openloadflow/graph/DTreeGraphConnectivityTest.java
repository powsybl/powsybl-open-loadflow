/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.google.common.collect.Sets;
import com.powsybl.openloadflow.graph.derivative.Delta2DTreeStandalone;
import com.powsybl.openloadflow.graph.derivative.Delta2ReplaceWithBestDTreeStandalone;
import com.powsybl.openloadflow.graph.derivative.ReplaceWithBestDTreeStandalone;
import com.powsybl.openloadflow.graph.dtree.DTGraph;
import com.powsybl.openloadflow.graph.dtree.DTNode;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
class DTreeGraphConnectivityTest {

    private <V, E> void assertDTNode(DTNode<V, E> node, V parent, E parentEdge, Set<V> children, Set<E> nonTreeEdges) {
        assertEquals(parent, node.getParent() == null ? null : node.getParent().getVertex());
        assertEquals(parentEdge, node.getParentEdge() == null ? null : node.getParentEdge().getEdgeData());
        assertEquals(nonTreeEdges, node.getNonTreeEdges().stream().map(Edge::getEdgeData).collect(Collectors.toSet()));

        Set<V> actualChildren = new HashSet<>();
        DTNode<V, E> child = node.getFirstChild();
        while (child != null) {
            assertTrue(actualChildren.add(child.getVertex())); // all children are distinct
            child = child.getNextSibling();
        }

        assertEquals(children, actualChildren);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDTreeConnectivities")
    void testInsertNonTreeEdge(SpanningForestGraphConnectivity<Integer, String> connectivity) {
        DTGraph<Integer, String> graph = connectivity.getGraph();
        for (int i = 0; i < 6; i++) {
            connectivity.addVertex(i);
        }
        connectivity.addEdge(1, 2, "1-2");
        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(0, 5, "0-5");
        connectivity.addEdge(0, 4, "0-4");
        connectivity.addEdge(2, 3, "2-3");

        //      1 -- 2
        //      |    |
        // 5 -- 0    3
        //      |
        //      4

        connectivity.startTemporaryChanges();
        assertDTNode(graph.getNodeThrowIfInexistent(0), null, null, Set.of(1, 4, 5), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(1), 0, "0-1", Set.of(2), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(2), 1, "1-2", Set.of(3), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(3), 2, "2-3", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(4), 0, "0-4", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(5), 0, "0-5", Set.of(), Set.of());

        // Adding this edge doesn't affect connectivity.
        // However, it modifies the spanning tree.
        connectivity.addEdge(0, 3, "0-3");
        // After:
        //      1 -- 2   (2 and 3 are still connected, but not in the spanning tree!)
        //      |
        // 5 -- 0 -- 3
        //      |
        //      4

        assertDTNode(graph.getNodeThrowIfInexistent(0), null, null, Set.of(1, 3, 4, 5), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(1), 0, "0-1", Set.of(2), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(2), 1, "1-2", Set.of(), Set.of("2-3"));
        assertDTNode(graph.getNodeThrowIfInexistent(3), 0, "0-3", Set.of(), Set.of("2-3"));
        assertDTNode(graph.getNodeThrowIfInexistent(4), 0, "0-4", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(5), 0, "0-5", Set.of(), Set.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDTreeConnectivities")
    void testMakeRootUpdateGreatParent(SpanningForestGraphConnectivity<Integer, String> connectivity) {
        DTGraph<Integer, String> graph = connectivity.getGraph();

        connectivity.addVertex(0);
        connectivity.addVertex(1);
        connectivity.addVertex(2);
        connectivity.addVertex(3);
        connectivity.addVertex(4);
        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(1, 2, "1-2");
        connectivity.addEdge(2, 3, "2-3");
        connectivity.addEdge(0, 4, "0-4");
        connectivity.addEdge(3, 4, "3-4");
        // 0 -- 1 -- 2 -- 3
        // |------4-------|
        // In the spanning tree, the root is 1
        // 4 <-- 0 <-- 1 --> 2 --> 3

        connectivity.startTemporaryChanges();
        assertDTNode(graph.getNodeThrowIfInexistent(0), 1, "0-1", Set.of(4), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(1), null, null, Set.of(0, 2), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(2), 1, "1-2", Set.of(3), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(3), 2, "2-3", Set.of(), Set.of("3-4"));
        assertDTNode(graph.getNodeThrowIfInexistent(4), 0, "0-4", Set.of(), Set.of("3-4"));

        connectivity.removeEdge("0-1");

        // the root is now 3, it involves getting the great parent of 1 (which is 3)
        // 1 <-- 2 <-- 3 --> 4 --> 0
        assertDTNode(graph.getNodeThrowIfInexistent(0), 4, "0-4", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(1), 2, "1-2", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(2), 3, "2-3", Set.of(1), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(3), null, null, Set.of(2, 4), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(4), 3, "3-4", Set.of(0), Set.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDTreeConnectivities")
    void testGetConnectedComponentContains(SpanningForestGraphConnectivity<Integer, String> connectivity) {
        for (int i = 0; i < 2; i++) {
            connectivity.addVertex(i);
        }
        connectivity.startTemporaryChanges();

        Set<Integer> zero = connectivity.getConnectedComponent(0);
        Set<Integer> one = connectivity.getConnectedComponent(1);

        assertTrue(zero.contains(0));
        assertFalse(zero.contains(1));
        assertTrue(one.contains(1));
        assertFalse(one.contains(0));

        connectivity.addEdge(0, 1, "0-1");

        assertTrue(zero.contains(1));
        assertTrue(one.contains(0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDTreeConnectivities")
    void testGetConnectedComponentUsesFindRoot(SpanningForestGraphConnectivity<Integer, String> connectivity) {
        for (int i = 0; i < 3; i++) {
            connectivity.addVertex(i);
        }
        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(1, 2, "1-2");
        connectivity.startTemporaryChanges();
        // 0 -- 1 -- 2
        //      ^ root

        Set<Integer> set = connectivity.getConnectedComponent(0);
        assertEquals(3, set.size());
        assertTrue(set.contains(2));
        assertEquals(Set.of(0, 1, 2), Sets.newHashSet(set));
    }

    /**
     * The goal of this test is to build a connected component such that its root
     * isn't a centroid and there is a centroid at distance 2 from the root. For example
     * in this tree:
     * <pre>
     *     0 -> 1 -> 2 -> 3 -> 4
     * </pre>
     * 0 is the root but the centroid is 2.
     * And then, the test will run a connectivity query (using {@link DTreeGraphConnectivity#getConnectedComponent(Object)}
     * and then {@link Set#contains(Object)}) between 3 and 4. DTree will compare the root
     * of the tree containing 3, and same for 4.
     * First, DTree will get the root of 3, which 0 and reroot the tree to 1.
     * Then, DTree wil get the root of 4, which is now 1, and reroot the tree to 2.
     * But 1 is different from 2, so DTree will return false even if 3 and 4 are in the same tree.
     * Therefore, DTree mustn't use findRootOptReroot the second time.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDTreeConnectivities")
    void testGetConnectComponentContainsDoesntUseFindRootOptRerootOnTheRightSide(SpanningForestGraphConnectivity<Integer, String> connectivity) {
        for (int i = 0; i < 8; i++) {
            connectivity.addVertex(i);
        }

        // The graph and sequence of remove was found by generating a graph
        // and removing all edges until a centroid at distance 2 from a root
        // is found. If there is none, a new graph (bigger) is generated.
        connectivity.addEdge(7, 7, "7-7");
        connectivity.addEdge(0, 0, "0-0");
        connectivity.addEdge(6, 4, "6-4");
        connectivity.addEdge(0, 3, "0-3");
        connectivity.addEdge(7, 3, "7-3");
        connectivity.addEdge(4, 5, "4-5");
        connectivity.addEdge(1, 4, "1-4");
        connectivity.addEdge(2, 7, "2-7");
        connectivity.addEdge(3, 6, "3-6");
        connectivity.addEdge(2, 3, "2-3");
        connectivity.addEdge(2, 6, "2-6");
        connectivity.addEdge(5, 1, "5-1");
        connectivity.addEdge(4, 2, "4-2");
        connectivity.addEdge(1, 1, "1-1");
        connectivity.addEdge(6, 5, "6-5");
        connectivity.addEdge(0, 5, "0-5");
        //   _         _
        //  | |       | |
        //   7 ----- 3 - 0 - 5
        //   |     ╱ |     ╱ | ╲
        //   |   ╱   |   ╱   |   1
        //   | ╱     | ╱     | ╱|_|
        //   2 ----- 6 ----- 4
        //   |_____________|
        // Spanning tree:
        // 3 - 7
        //   - 2
        //   - 0
        //   - 6 - 5
        //       - 4 - 1

        connectivity.removeEdge("2-6");
        connectivity.removeEdge("4-2");
        connectivity.removeEdge("3-6");
        connectivity.removeEdge("0-3");

        // New spanning trees:
        // 2 - 3 - 7
        //     ^ root
        // 0 - 5 - 6 - 4 - 1
        // ^ root

        connectivity.startTemporaryChanges();
        Set<Integer> comp = connectivity.getConnectedComponent(1);
        // First, contains will call findRootOptReroot on 4.
        // That will change the root from 0 to 5, and return 5.
        // Then, findRootOptReroot will be called on 1.
        // That will change the root from 5 to 6, and return 6.
        // Finally, contains will compare the two value returned and return false.
        assertTrue(comp.contains(4));
    }

    private static Stream<Arguments> provideDTreeConnectivities() {
        return Stream.of(
                Arguments.of(new DTreeGraphConnectivity<>()),
                Arguments.of(new DTreeStandalone<>()),
                Arguments.of(new Delta2DTreeStandalone<>()),
                Arguments.of(new Delta2ReplaceWithBestDTreeStandalone<>()),
                Arguments.of(new ReplaceWithBestDTreeStandalone<>()),
                Arguments.of(new IDTreeStandalone<>()),
                Arguments.of(new DnDTreeStandalone<>()),
                Arguments.of(new OptDTreeStandalone<>()));
    }
}
