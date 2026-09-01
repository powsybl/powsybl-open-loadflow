/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.google.common.collect.Sets;
import com.powsybl.openloadflow.graph.dtree.DTGraph;
import com.powsybl.openloadflow.graph.dtree.DTNode;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivity;
import com.powsybl.openloadflow.graph.dtree.Edge;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
class DTreeGraphConnectivityTest {

    private <V, E> void assertDTNode(DTNode<V, E> node, V parent, E parentEdge, Set<V> children, Set<E> nonTreeEdges) {
        assertEquals(parent, node.getParent() == null ? null : node.getParent().getVertex());
        assertEquals(parentEdge, node.getParentEdge() == null ? null : node.getParentEdge().edgeData());
        assertEquals(nonTreeEdges, node.getNonTreeEdges().stream().map(Edge::edgeData).collect(Collectors.toSet()));

        Set<V> actualChildren = new HashSet<>();
        DTNode<V, E> child = node.getFirstChild();
        while (child != null) {
            assertTrue(actualChildren.add(child.getVertex())); // all children are distinct
            child = child.getNextSibling();
        }

        assertEquals(children, actualChildren);
    }

    @Test
    void testInsertNonTreeEdge() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
        DTGraph<Integer, String> graph = connectivity.getGraph();

        for (int i = 0; i < 8; i++) {
            connectivity.addVertex(i);
        }
        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(0, 2, "0-2");
        connectivity.addEdge(0, 3, "0-3");
        connectivity.addEdge(0, 4, "0-4");
        connectivity.addEdge(4, 5, "4-5");
        connectivity.addEdge(5, 6, "5-6");
        connectivity.addEdge(6, 7, "6-7");

        // tree with 0 being the root:
        //    1   4 -- 5
        //     \ /     |
        // 2 -- 0      |
        //     /       |
        //    3   7 -- 6

        connectivity.startTemporaryChanges();
        assertDTNode(graph.getNodeThrowIfInexistent(0), null, null, Set.of(1, 2, 3, 4), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(1), 0, "0-1", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(2), 0, "0-2", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(3), 0, "0-3", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(4), 0, "0-4", Set.of(5), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(5), 4, "4-5", Set.of(6), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(6), 5, "5-6", Set.of(7), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(7), 6, "6-7", Set.of(), Set.of());

        // Adding this edge doesn't affect connectivity.
        // However, it modifies the spanning tree.
        connectivity.addEdge(0, 7, "0-7");
        // After:
        //    1   4 -- 5
        //     \ /
        // 2 -- 0
        //     / \
        //    3   7 -- 6

        assertDTNode(graph.getNodeThrowIfInexistent(0), null, null, Set.of(1, 2, 3, 4, 7), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(1), 0, "0-1", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(2), 0, "0-2", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(3), 0, "0-3", Set.of(), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(4), 0, "0-4", Set.of(5), Set.of());
        assertDTNode(graph.getNodeThrowIfInexistent(5), 4, "4-5", Set.of(), Set.of("5-6"));
        assertDTNode(graph.getNodeThrowIfInexistent(6), 7, "6-7", Set.of(), Set.of("5-6"));
        assertDTNode(graph.getNodeThrowIfInexistent(7), 0, "0-7", Set.of(6), Set.of());
    }

    @Test
    void testMakeRootUpdateGreatParent() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
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

    @Test
    void testGetConnectedComponentContains() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
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

    @Test
    void testGetConnectedComponentUsesFindRoot() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
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
}
