/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.Pair;
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
        SpanningForest<Integer, String> spanningForest = new SpanningForest<>();
        assertForestEquals(spanningForest, List.of());
    }

    @Test
    void testConnected() {
        SpanningForest<Integer, String> spanningForest = new SpanningForest<>();

        assertFalse(spanningForest.connected(0, 0));
        assertFalse(spanningForest.connected(0, 1));
        spanningForest.addVertex(0);
        assertTrue(spanningForest.connected(0, 0));
        assertFalse(spanningForest.connected(0, 1));
        spanningForest.addVertex(1);
        assertFalse(spanningForest.connected(0, 1));
        spanningForest.addEdge(0, 1, "0-1");
        assertTrue(spanningForest.connected(0, 1));
    }

    @Test
    void testTreeSize() {
        SpanningForest<Integer, String> spanningForest = new SpanningForest<>();
        assertEquals(-1, spanningForest.treeSize(0));
        spanningForest.addVertex(0);
        assertEquals(1, spanningForest.treeSize(0));
        spanningForest.addEdge(0, 1, "0-1");
        assertEquals(2, spanningForest.treeSize(0));
    }

    @Test
    void testAddVertex() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertFalse(forest.contains(0));
        assertTrue(forest.addVertex(0));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0), Set.of())
        ));
        assertFalse(forest.addVertex(0)); // already existing

        assertFalse(forest.contains(1));
        assertTrue(forest.addVertex(1));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0), Set.of()),
                Pair.of(Set.of(1), Set.of())
        ));
        assertFalse(forest.addVertex(1)); // already existing
    }

    @Test
    void testRemoveVertex() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        // 0
        // 1--2
        // 4--3--5--6
        forest.addVertex(0);
        forest.addEdge(1, 2, "1-2");
        forest.addEdge(3, 4, "3-4");
        forest.addEdge(3, 5, "3-5");
        forest.addEdge(5, 6, "5-6");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0), Set.of()),
                Pair.of(Set.of(1, 2), Set.of("1-2")),
                Pair.of(Set.of(4, 3, 5, 6), Set.of("3-4", "3-5", "5-6"))
        ));

        assertFalse(forest.removeVertex(-1)); // removal of non-existing vertex

        // remove singleton
        assertTrue(forest.removeVertex(0));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(1, 2), Set.of("1-2")),
                Pair.of(Set.of(4, 3, 5, 6), Set.of("3-4", "3-5", "5-6"))
        ));

        // remove leaf
        assertTrue(forest.removeVertex(1));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(2), Set.of()),
                Pair.of(Set.of(4, 3, 5, 6), Set.of("3-4", "3-5", "5-6"))
        ));

        // remove non-leaf node
        assertTrue(forest.removeVertex(3));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(2), Set.of()),
                Pair.of(Set.of(4), Set.of()),
                Pair.of(Set.of(5, 6), Set.of("5-6"))
        ));
    }

    @Test
    void addEdgeBetweenSingletonAndSingleton() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertTrue(forest.addEdge(0, 1, "0-1"));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1), Set.of("0-1"))
        ));
    }

    @Test
    void addEdgeBetweenTreeAndSingleton() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertTrue(forest.addEdge(0, 1, "0-1"));
        assertTrue(forest.addEdge(0, 2, "0-2"));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2), Set.of("0-1", "0-2"))
        ));
    }

    @Test
    void addEdgeBetweenSingletonAndTree() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertTrue(forest.addEdge(0, 1, "0-1"));
        assertTrue(forest.addEdge(2, 0, "2-0"));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2), Set.of("0-1", "2-0"))
        ));
    }

    @Test
    void addEdgeBetweenTreeAndTree() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        // tree1
        assertTrue(forest.addEdge(0, 1, "0-1"));
        assertTrue(forest.addEdge(0, 2, "0-2"));
        // tree2
        assertTrue(forest.addEdge(3, 4, "3-4"));
        assertTrue(forest.addEdge(4, 5, "4-5"));
        // link
        assertTrue(forest.addEdge(0, 4, "0-4"));

        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2, 3, 4, 5), Set.of("0-1", "0-2", "3-4", "4-5", "0-4"))
        ));
    }

    @Test
    void addEdgeAlreadyConnected() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertTrue(forest.addEdge(0, 1, "0-1"));
        assertFalse(forest.addEdge(0, 1, "0-1bis"));
        assertFalse(forest.addEdge(0, 2, "0-1")); // the edge 0-1 already exists

        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1), Set.of("0-1"))
        ));

        assertFalse(forest.addEdge(0, 0, "0-0"));
    }

    @Test
    void removeEdgeCreatingTwoSingletons() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        // 0 -- 1
        forest.addEdge(0, 1, "0-1");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1), Set.of("0-1"))
        ));

        // 0
        // 1
        forest.removeEdge(0, 1, "0-1");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0), Set.of()),
                Pair.of(Set.of(1), Set.of())
        ));
    }

    @Test
    void removeEdgeCreatingOneSingletonAndOneTree() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        // 0 -- 1 -- 2
        forest.addEdge(0, 1, "0-1");
        forest.addEdge(1, 2, "1-2");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2), Set.of("0-1", "1-2"))
        ));

        // 0
        // 1 -- 2
        forest.removeEdge(0, 1, "0-1");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0), Set.of()),
                Pair.of(Set.of(1, 2), Set.of("1-2"))
        ));
    }

    @Test
    void removeEdgeCreatingOneTreeAndOneSingleton() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        // 0 -- 1 -- 2
        forest.addEdge(0, 1, "0-1");
        forest.addEdge(1, 2, "1-2");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2), Set.of("0-1", "1-2"))
        ));

        // 0 -- 1
        // 2
        forest.removeEdge(1, 2, "1-2");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1), Set.of("0-1")),
                Pair.of(Set.of(2), Set.of())
        ));
    }

    @Test
    void removeEdgeCreatingTwoTrees() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        // 0 -- 1 -- 2
        //           |
        //      5 -- 3 -- 4
        forest.addEdge(0, 1, "0-1");
        forest.addEdge(1, 2, "1-2");
        forest.addEdge(3, 4, "3-4");
        forest.addEdge(3, 5, "3-5");
        forest.addEdge(2, 3, "2-3");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2, 3, 4, 5), Set.of("0-1", "1-2", "3-4", "3-5", "2-3"))
        ));

        // 0 -- 1 -- 2
        // 5 -- 3 -- 4
        forest.removeEdge(1, 2, "2-3");
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1, 2), Set.of("0-1", "1-2")),
                Pair.of(Set.of(3, 4, 5), Set.of("3-4", "3-5"))
        ));
    }

    @Test
    void removeNonExistingEdge() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();
        forest.addEdge(0, 1, "0-1");
        forest.addVertex(2);

        assertFalse(forest.removeEdge(0, 1, "0-1bis"));
        assertFalse(forest.removeEdge(0, 2, "0-2"));
        assertForestEquals(forest, List.of(
                Pair.of(Set.of(0, 1), Set.of("0-1")),
                Pair.of(Set.of(2), Set.of())
        ));
    }

    @Test
    void testEdgesInComponent() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        assertSetEquals(Collections.emptySet(), forest.edgesInComponent(0));

        forest.addEdge(0, 1, -1);
        assertSetEquals(Set.of(-1), forest.edgesInComponent(0));

        forest.addEdge(0, 2, -2);
        assertSetEquals(Set.of(-1, -2), forest.edgesInComponent(0));

        forest.addVertex(3);
        assertSetEquals(Collections.emptySet(), forest.edgesInComponent(3));
    }

    @Test
    void testVerticesInComponent() {
        SpanningForest<Integer, Integer> forest = new SpanningForest<>();

        assertSetEquals(Collections.emptySet(), forest.verticesInComponent(0));
        forest.addVertex(0);
        forest.addVertex(1);

        assertSetEquals(Set.of(0), forest.verticesInComponent(0));
        assertSetEquals(Set.of(1), forest.verticesInComponent(1));

        forest.addEdge(0, 1, -1);
        assertSetEquals(Set.of(0, 1), forest.verticesInComponent(0));
        assertSetEquals(Set.of(0, 1), forest.verticesInComponent(1));

        forest.addEdge(1, 2, -2);
        assertSetEquals(Set.of(0, 1, 2), forest.verticesInComponent(0));
        assertSetEquals(Set.of(0, 1, 2), forest.verticesInComponent(1));
        assertSetEquals(Set.of(0, 1, 2), forest.verticesInComponent(2));

        forest.removeEdge(0, 1, -1);
        assertSetEquals(Set.of(0), forest.verticesInComponent(0));
        assertSetEquals(Set.of(1, 2), forest.verticesInComponent(1));
        assertSetEquals(Set.of(1, 2), forest.verticesInComponent(2));
    }

    @Test
    void testAdjacentEdges() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertSetEquals(Collections.emptySet(), forest.adjacentEdges(0));
        forest.addEdge(0, 1, "0-1");
        assertSetEquals(Set.of("0-1"), forest.adjacentEdges(0));
        assertSetEquals(Set.of("0-1"), forest.adjacentEdges(1));

        forest.addEdge(0, 2, "0-2");
        assertSetEquals(Set.of("0-1", "0-2"), forest.adjacentEdges(0));
        assertSetEquals(Set.of("0-1"), forest.adjacentEdges(1));
        assertSetEquals(Set.of("0-2"), forest.adjacentEdges(2));

        forest.addVertex(3);
        assertSetEquals(Set.of("0-1", "0-2"), forest.adjacentEdges(0));
        assertSetEquals(Set.of("0-1"), forest.adjacentEdges(1));
        assertSetEquals(Set.of("0-2"), forest.adjacentEdges(2));
        assertSetEquals(Set.of(), forest.adjacentEdges(3));
    }

    @Test
    void testRoots() {
        SpanningForest<Integer, String> forest = new SpanningForest<>();

        assertSetEquals(Collections.emptySet(), forest.roots());
        forest.addVertex(0);
        forest.addVertex(1);
        forest.addVertex(2);

        assertSetEquals(Set.of(0, 1, 2), forest.roots());
        forest.addEdge(1, 2, "1-2");

        Iterator<Integer> roots = forest.roots();
        int v1 = roots.next();
        int v2 = roots.next();
        assertFalse(roots.hasNext());
        assertTrue(v1 == 0 && (v2 == 1 || v2 == 2));
    }

    <V> void assertSetEquals(Set<V> expected, Iterator<V> it) {
        assertEquals(expected, collectDistinct(it, expected.size()));
    }

    <V, E> void assertForestEquals(SpanningForest<V, E> forest, List<Pair<Set<V>, Set<E>>> trees) {
        assertEquals(forest.treeCount(), trees.size());

        // first check every SpanningForest#XXX(V vertex)
        // except adjacentEdges
        for (Pair<Set<V>, Set<E>> tree : trees) {
            Set<V> vertices = tree.getKey();
            Set<E> edges = tree.getValue();

            for (V vertex1 : vertices) {
                assertEquals(vertices.size(), forest.treeSize(vertex1));
                assertTrue(forest.contains(vertex1));
                assertSetEquals(vertices, forest.verticesInComponent(vertex1));
                assertSetEquals(edges, forest.edgesInComponent(vertex1));
            }
        }

        // check SpanningForest#roots()
        Set<V> roots = collectDistinct(forest.roots(), forest.treeCount());
        for (Pair<Set<V>, Set<E>> tree : trees) {
            // assert that exactly one element of tree.getKey() is in roots
            V vertexInRoots = null;
            for (V vertex : tree.getKey()) {
                if (roots.contains(vertex)) {
                    assertNull(vertexInRoots); // assert that at most one element of tree.getKey() can be in roots
                    vertexInRoots = vertex;
                }
            }

            assertNotNull(vertexInRoots); // assert that at least one element of tree.getKey() can be in roots
        }

        // check SpanningForest#connected
        for (int i = 0; i < trees.size(); i++) {
            for (int j = 0; j < trees.size(); j++) {
                boolean connected = i == j;

                for (V vertexI : trees.get(i).getKey()) {
                    for (V vertexJ : trees.get(j).getKey()) {
                        assertEquals(connected, forest.connected(vertexI, vertexJ));
                    }
                }
            }
        }
    }

    <T> Set<T> collectDistinct(Iterator<T> it, int expectedSize) {
        Set<T> current = new HashSet<>();

        while (it.hasNext() && current.size() < expectedSize) {
            assertTrue(current.add(it.next()));
        }

        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
        assertEquals(expectedSize, current.size());

        return current;
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
            /*if (iter % 100 == 0) {
                System.out.println(iter);
            }*/

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

        // remove every edge in the forest
        disconnectTree(tree1, forest);
        disconnectTree(tree2, forest);
    }

    private void connectTree(Graph<Integer, DefaultEdge> graph, SpanningForest<Integer, DefaultEdge> forest) {
        for (Integer v : graph.vertexSet()) {
            assertFalse(forest.contains(v));
            assertTrue(forest.addVertex(v));
            assertTrue(forest.contains(v));
        }
        for (DefaultEdge e : graph.edgeSet()) {
            int source = graph.getEdgeSource(e);
            int target = graph.getEdgeTarget(e);
            assertFalse(forest.connected(source, target));
            assertTrue(forest.addEdge(source, target, e));
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
