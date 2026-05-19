/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;
import org.jgrapht.generate.BarabasiAlbertForestGenerator;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpanningForestTest {

    private static final Random RANDOM = new Random(0);

    @Test
    void testConstructor() {
        SpanningForest<Integer, DefaultEdge> spanningForest = new SpanningForest<>();
        assertEquals(0, spanningForest.treeCount());
        // TODO: check other accessors
    }

    // Inspired by https://github.com/jgrapht/jgrapht/blob/70d5287e3e10cb8c4676e84fbfe1d4a3cc4338ba/jgrapht-core/src/test/java/org/jgrapht/alg/connectivity/TreeDynamicConnectivityTest.java#L41
    @Test
    void testAddVertex() {
        SpanningForest<Integer, DefaultEdge> forest = new SpanningForest<>();

        for (int i = 0; i < 50; i++) {
            assertFalse(forest.contains(i));
            assertTrue(forest.addVertex(i));
            forest.checkInvariants();
            assertTrue(forest.contains(i));
            assertFalse(forest.addVertex(i));
            forest.checkInvariants();

            assertEquals(1, forest.componentSize(i));
            assertEquals(i + 1, forest.treeCount());
        }
    }

    @Test
    void addEdgeBetweenSingletonAndSingleton() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        forest.addEdge(0, 1, 0);
        forest.checkInvariants();
        assertTrue(forest.connected(0, 1));
    }

    @Test
    void addEdgeBetweenTreeAndSingleton() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        forest.addEdge(0, 1, -1);
        forest.addEdge(0, 2, -2);

        forest.checkInvariants();
        assertTrue(forest.connected(0, 1));
        assertTrue(forest.connected(0, 2));
        assertTrue(forest.connected(1, 2));
    }

    @Test
    void addEdgeBetweenSingletonAndTree() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        forest.addEdge(0, 1, -1);
        forest.addEdge(2, 0, -2);

        forest.checkInvariants();
        assertTrue(forest.connected(0, 1));
        assertTrue(forest.connected(0, 2));
        assertTrue(forest.connected(1, 2));
    }

    @Test
    void addEdgeBetweenTreeAndTree() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        // tree1
        forest.addEdge(0, 1, -1);
        forest.addEdge(0, 2, -2);
        // tree2
        forest.addEdge(3, 4, -3);
        forest.addEdge(4, 3, -4);
        // link
        forest.addEdge(0, 4, -5);

        forest.checkInvariants();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                assertTrue(forest.connected(i, j));
            }
        }
    }

    @Test
    void testEdgesInComponent() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        assertEquals(Collections.emptySet(), collect(forest.edgesInComponent(0), 0));

        forest.addEdge(0, 1, -1);
        assertEquals(Set.of(-1), collect(forest.edgesInComponent(0), 1));

        forest.addEdge(0, 2, -2);
        assertEquals(Set.of(-1, -2), collect(forest.edgesInComponent(0), 2));
    }

    @Test
    void testVerticesInComponent() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        assertEquals(Collections.emptySet(), collect(forest.verticesInComponent(0), 0));
        forest.addVertex(0);
        forest.addVertex(1);

        assertEquals(Set.of(0), collect(forest.verticesInComponent(0), 1));
        assertEquals(Set.of(1), collect(forest.verticesInComponent(1), 1));

        forest.addEdge(0, 1, -1);
        assertEquals(Set.of(0, 1), collect(forest.verticesInComponent(0), 2));
        assertEquals(Set.of(0, 1), collect(forest.verticesInComponent(1), 2));

        forest.addEdge(1, 2, -2);
        assertEquals(Set.of(0, 1, 2), collect(forest.verticesInComponent(0), 3));
        assertEquals(Set.of(0, 1, 2), collect(forest.verticesInComponent(1), 3));
        assertEquals(Set.of(0, 1, 2), collect(forest.verticesInComponent(2), 3));

        forest.removeEdge(0, 1, -1);
        assertEquals(Set.of(0), collect(forest.verticesInComponent(0), 1));
        assertEquals(Set.of(1, 2), collect(forest.verticesInComponent(1), 2));
        assertEquals(Set.of(1, 2), collect(forest.verticesInComponent(2), 2));
    }

    // The purpose of maxSize is to prevent infinite loop if Iterator#next
    // always returns true
    <V> Set<V> collect(Iterator<V> it, int maxSize) {
        Set<V> set = new HashSet<>();

        while (it.hasNext() && set.size() < maxSize) {
            assertTrue(set.add(it.next()));
        }

        assertFalse(it.hasNext());

        return set;
    }

    @Test
    void testConnected() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        forest.addEdge(0, 1, 0);
        forest.checkInvariants();
        assertTrue(forest.connected(0, 1));

        forest.addEdge(1, 2, 1);
        forest.checkInvariants();
        assertTrue(forest.connected(0, 1));
        assertTrue(forest.connected(0, 2));
        assertTrue(forest.connected(1, 2));

        forest.removeEdge(0, 1, 0);
        forest.checkInvariants();
        assertFalse(forest.connected(0, 1));
        assertTrue(forest.connected(1, 2));

        forest.removeEdge(1, 2, 1);
        forest.checkInvariants();
        assertFalse(forest.connected(0, 1));
        assertFalse(forest.connected(0, 2));
        assertFalse(forest.connected(1, 2));
    }

    // Inspired by https://github.com/jgrapht/jgrapht/blob/70d5287e3e10cb8c4676e84fbfe1d4a3cc4338ba/jgrapht-core/src/test/java/org/jgrapht/alg/connectivity/TreeDynamicConnectivityTest.java#L58
    // this test is slow (1-2s) because of checkInvariants
    @Test
    void testTwoTrees() {
        // generate two distinct trees
        Graph<Integer, DefaultEdge> tree1 = generateTree(50, 0);
        Graph<Integer, DefaultEdge> tree2 = generateTree(50, tree1.vertexSet().size());

        SpanningForest<Integer, DefaultEdge> forest = new SpanningForest<>();
        // connect these trees in the forest. that is,
        // for each edge in tree1 and tree2, call forest.addEdge
        connectTree(tree1, forest);
        connectTree(tree2, forest);

        // for every pair of vertex in tree1 and tree2, add edge between these two
        // and then remove it
        int iter = 0;
        for (int v1 : tree1.vertexSet()) {
            for (int v2 : tree2.vertexSet()) {
                DefaultEdge edge = new DefaultEdge();
                assertFalse(forest.connected(v1, v2), "iter = " + iter);

                assertTrue(forest.addEdge(v1, v2, edge), "iter = " + iter);
                forest.checkInvariants();
                assertTrue(forest.connected(v1, v2), "iter = " + iter);

                assertFalse(forest.addEdge(v1, v2, edge), "iter = " + iter);
                forest.checkInvariants();
                assertTrue(forest.connected(v1, v2), "iter = " + iter);

                assertTrue(forest.removeEdge(v1, v2, edge), "iter = " + iter);
                forest.checkInvariants();
                assertFalse(forest.connected(v1, v2), "iter = " + iter);

                assertFalse(forest.removeEdge(v1, v2, edge), "iter = " + iter);
                forest.checkInvariants();
                assertFalse(forest.connected(v1, v2), "iter = " + iter);

                iter++;
            }
        }

        // remove every edge in the forest
        disconnectTree(tree1, forest);
        disconnectTree(tree2, forest);
    }

    private void connectTree(Graph<Integer, DefaultEdge> graph, SpanningForest<Integer, DefaultEdge> forest) {
        for (Integer v : graph.vertexSet()) {
            assertFalse(forest.contains(v));
            assertTrue(forest.addVertex(v));
            forest.checkInvariants();
            assertTrue(forest.contains(v));
        }
        for (DefaultEdge e : graph.edgeSet()) {
            int source = graph.getEdgeSource(e);
            int target = graph.getEdgeTarget(e);
            assertFalse(forest.connected(source, target));
            assertTrue(forest.addEdge(source, target, e));
            forest.checkInvariants();
            assertTrue(forest.connected(source, target));
        }
    }

    private void disconnectTree(Graph<Integer, DefaultEdge> graph, SpanningForest<Integer, DefaultEdge> forest) {
        for (DefaultEdge e : graph.edgeSet()) {
            int source = graph.getEdgeSource(e);
            int target = graph.getEdgeTarget(e);
            assertTrue(forest.connected(source, target));
            assertTrue(forest.removeEdge(source, target, e));
            assertFalse(forest.connected(source, target));
        }
    }

    private Graph<Integer, DefaultEdge> generateTree(int size, int start) {
        Graph<Integer, DefaultEdge> tree = new DefaultUndirectedGraph<>(
                SupplierUtil.createIntegerSupplier(start), SupplierUtil.createDefaultEdgeSupplier(),
                false);

        BarabasiAlbertForestGenerator<Integer, DefaultEdge> gen =
                new BarabasiAlbertForestGenerator<>(1, size, RANDOM);
        gen.generateGraph(tree);
        return tree;
    }
}
