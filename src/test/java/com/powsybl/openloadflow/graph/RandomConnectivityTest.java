/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;
import org.jgrapht.generate.ScaleFreeGraphGenerator;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class RandomConnectivityTest {

    @Test
    void randomGraph() {
        for (int size = 0; size < 50; size++) {
            for (int seed = 0; seed < 100; seed++) {
                System.out.printf("size: %d, seed: %d%n", size, seed);
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
                        //new HolmEtAlGraphConnectivity<>(),
                        new DTreeGraphConnectivity<>(),
                        new DTreeStandalone<>()
                );

                for (GraphConnectivity<Integer, DefaultEdge> connectivity : toTest) {
                    assertSameResultAsControlSample(graph, connectivity, sample);
                }
            }
        }
    }

    private static <V, E> void assertSameResultAsControlSample(Graph<V, E> graph, GraphConnectivity<V, E> connectivity, Sample<V, E> sample) {
        List<E> edges = new ArrayList<>(graph.edgeSet());

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
        for (int i = 0; i < edges.size(); i++) {
            E edge = edges.get(i);
            connectivity.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge), edge);
            sample.checkAddEdge(connectivity, edge);

            // check previously connected edges are still connected
            for (int j = 0; j <= i; j++) {
                E e2 = edges.get(j);
                assertEquals(connectivity.getComponentNumber(graph.getEdgeSource(e2)),
                        connectivity.getComponentNumber(graph.getEdgeTarget(e2)),
                        e2.toString());
            }
        }

        // check the graph is indeed fully connected
        for (V v1 : graph.vertexSet()) {
            for (V v2 : graph.vertexSet()) {
                Assertions.assertEquals(connectivity.getComponentNumber(v1), connectivity.getComponentNumber(v2),
                        "(size = " + sample.size + ", seed = " + sample.seed + ")");
            }
        }

        // fully disconnect the graph
        for (int i = 0; i < edges.size(); i++) {
            E edge = edges.get(i);
            connectivity.removeEdge(edge);
            sample.checkRemoveEdge(connectivity, edge);
        }

        // check graph is indeed fully disconnected
        for (E edge : edges) {
            assertNotEquals(connectivity.getComponentNumber(graph.getEdgeSource(edge)),
                    connectivity.getComponentNumber(graph.getEdgeTarget(edge)),
                    edge.toString() + " isn't disconnected");
        }

        sample.endTest();
    }

    static class Sample<V, E> {

        private final int size;
        private final int seed;

        private final List<Result<V>> expectedResults = new ArrayList<>();
        private final Set<V> verticesInGraphConnectivity = new HashSet<>();
        private boolean buildingControlSample = true;
        private int step = 0;

        Sample(int size, int seed) {
            this.size = size;
            this.seed = seed;
        }

        public void check(GraphConnectivity<V, E> connectivity, String method) {
            Result<V> result = Result.from(verticesInGraphConnectivity, connectivity);

            if (buildingControlSample) {
                expectedResults.add(result);
            } else {
                assertEquals(expectedResults.get(step),
                        result,
                        "%s at step = %d with %s (size = %d, seed = %d)"
                                .formatted(method, step, connectivity.getClass().getSimpleName(), size, seed));
            }
            step++;
        }

        public void checkAddVertex(GraphConnectivity<V, E> connectivity, V vertex) {
            Assertions.assertTrue(verticesInGraphConnectivity.add(vertex));
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
            verticesInGraphConnectivity.clear();
        }

        public void endTest() {
            assertEquals(step, expectedResults.size(), "sample was not fully tested");
        }
    }

    private record Result<V>(
            int nbComponent,
            Set<Set<V>> components
    ) {
        public static <V> Result<V> from(Iterable<V> vertices, GraphConnectivity<V, ?> conn) {
            Set<Set<V>> components = new HashSet<>();

            for (V vertex : vertices) {
                components.add(new HashSet<>(conn.getConnectedComponent(vertex)));
            }

            return new Result<>(conn.getNbConnectedComponents(), components);
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
}
