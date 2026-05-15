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

import java.util.Random;

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
        SpanningForest<Integer, DefaultEdge> spanningForest = new SpanningForest<>();

        for (int i = 0; i < 50; i++) {
            assertFalse(spanningForest.contains(i));
            assertTrue(spanningForest.addVertex(i));
            assertTrue(spanningForest.contains(i));
            assertFalse(spanningForest.addVertex(i));

            assertEquals(1, spanningForest.componentSize(i));
            assertEquals(i + 1, spanningForest.treeCount());
        }
    }

    @Test
    void testConnected() {
        SpanningForest<Integer, Integer> spanningForest = new SpanningForest<>();

        spanningForest.addEdge(0, 1, 0);
        assertTrue(spanningForest.connected(0, 1));
        spanningForest.addEdge(1, 2, 1);
        assertTrue(spanningForest.connected(0, 1));
        assertTrue(spanningForest.connected(0, 2));
        assertTrue(spanningForest.connected(1, 2));

        spanningForest.removeEdge(0, 1, 0);
        assertFalse(spanningForest.connected(0, 1));
        assertTrue(spanningForest.connected(1, 2));

        spanningForest.removeEdge(1, 2, 1);
        assertFalse(spanningForest.connected(0, 1));
        assertFalse(spanningForest.connected(0, 2));
        assertFalse(spanningForest.connected(1, 2));
    }

    // Inspired by https://github.com/jgrapht/jgrapht/blob/70d5287e3e10cb8c4676e84fbfe1d4a3cc4338ba/jgrapht-core/src/test/java/org/jgrapht/alg/connectivity/TreeDynamicConnectivityTest.java#L58
    @Test
    void testTwoTrees() {
        // generate two distinct trees
        Graph<Integer, DefaultEdge> tree1 = generateTree(50, 0);
        Graph<Integer, DefaultEdge> tree2 = generateTree(50, 50);

        SpanningForest<Integer, DefaultEdge> forest = new SpanningForest<>();
        // connect these trees in the forest. that is,
        // for each edge in tree1 and tree2, call foreset.addEdge
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
                assertTrue(forest.connected(v1, v2), "iter = " + iter);

                assertFalse(forest.addEdge(v1, v2, edge), "iter = " + iter);
                assertTrue(forest.connected(v1, v2), "iter = " + iter);

                assertTrue(forest.removeEdge(v1, v2, edge), "iter = " + iter);
                assertFalse(forest.connected(v1, v2), "iter = " + iter);

                assertFalse(forest.removeEdge(v1, v2, edge), "iter = " + iter);
                assertFalse(forest.connected(v1, v2), "iter = " + iter);

                iter++;
            }
        }

        // remove every edges in the forest
        disconnectTree(tree1, forest);
        disconnectTree(tree2, forest);
    }

    private void connectTree(
            Graph<Integer, DefaultEdge> graph, SpanningForest<Integer, DefaultEdge> connectivity)
    {
        for (Integer v : graph.vertexSet()) {
            assertFalse(connectivity.contains(v));
            assertTrue(connectivity.addVertex(v));
            assertTrue(connectivity.contains(v));
        }
        for (DefaultEdge e : graph.edgeSet()) {
            int source = graph.getEdgeSource(e), target = graph.getEdgeTarget(e);
            assertFalse(connectivity.connected(source, target));
            assertTrue(connectivity.addEdge(source, target, e));
            assertTrue(connectivity.connected(source, target));
        }
    }

    private void disconnectTree(Graph<Integer, DefaultEdge> graph, SpanningForest<Integer, DefaultEdge> connectivity) {
        for (DefaultEdge e : graph.edgeSet()) {
            int source = graph.getEdgeSource(e), target = graph.getEdgeTarget(e);
            assertTrue(connectivity.connected(source, target));
            assertTrue(connectivity.removeEdge(source, target, e));
            assertFalse(connectivity.connected(source, target));
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
