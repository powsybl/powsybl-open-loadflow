/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.util.UnionFind;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class MinimumSpanningTreeGraphDecrementalConnectivity<V> implements GraphDecrementalConnectivity<V> {

    private SpanningTrees mstOrigin;
    private SpanningTrees mst;
    private final Graph<V, Object> graph;

    private final List<Triple<V, V, Object>> cutEdges;
    private List<V> sortedRoots;
    private Map<V, V> parentMap;

    public MinimumSpanningTreeGraphDecrementalConnectivity() {
        this.graph = new Pseudograph<>(Object.class);
        this.cutEdges = new ArrayList<>();
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
    }

    @Override
    public void addEdge(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.addEdge(vertex1, vertex2, new Object());
    }

    @Override
    public void cut(V vertex1, V vertex2) {
        if (this.mstOrigin == null) {
            this.mstOrigin = new KruskalMinimumSpanningTrees().getSpanningTree();
            resetMst();
        }
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        Object e = graph.removeEdge(vertex1, vertex2);

        if (mst != null && mst.getEdges().contains(e)) {
            invalidateMst();
        }

        cutEdges.add(Triple.of(vertex1, vertex2, e));
    }

    @Override
    public void reset() {
        for (Triple<V, V, Object> cutEdge : cutEdges) {
            graph.addEdge(cutEdge.getLeft(), cutEdge.getMiddle(), cutEdge.getRight());
        }
        cutEdges.clear();
        resetMst();
    }

    private void resetMst() {
        this.mst = mstOrigin;
        this.parentMap = null;
        this.sortedRoots = null;
    }

    private void invalidateMst() {
        this.mst = null;
        this.parentMap = null;
        this.sortedRoots = null;
    }

    @Override
    public int getComponentNumber(V vertex) {
        if (this.mst == null) {
            this.mst = new KruskalMinimumSpanningTrees().getSpanningTree();
        }
        if (this.parentMap == null) {
            this.parentMap = mst.forest.getParentMap();
            this.sortedRoots = mst.forest.getSortedRoots();
        }
        return sortedRoots.indexOf(parentMap.get(vertex));
    }

    @Override
    public List<Set<V>> getSmallComponents() {
        if (this.mst == null) {
            this.mst = new KruskalMinimumSpanningTrees().getSpanningTree();
        }
        if (this.parentMap == null) {
            this.parentMap = mst.forest.getParentMap();
            this.sortedRoots = mst.forest.getSortedRoots();
        }
        List<Set<V>> components = new ArrayList<>();
        for (V root : sortedRoots) {
            Set<V> set = parentMap.entrySet().stream()
                .filter(e -> e.getValue() == root)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
            components.add(set);
        }
        return components.subList(1, components.size());
    }

    class KruskalMinimumSpanningTrees implements SpanningTreeAlgorithm<Object> {

        @Override
        public SpanningTrees getSpanningTree() {
            MyUnionFind forest = new MyUnionFind(graph.vertexSet());

            Set<Object> edgeList = new HashSet<>();
            for (Object edge : graph.edgeSet()) {
                V source = graph.getEdgeSource(edge);
                V target = graph.getEdgeTarget(edge);
                if (forest.find(source).equals(forest.find(target))) {
                    continue;
                }

                forest.union(source, target);
                edgeList.add(edge);
            }

            return new SpanningTrees(forest, edgeList, 0);
        }
    }

    private class SpanningTrees extends SpanningTreeAlgorithm.SpanningTreeImpl<Object> {
        private final transient MyUnionFind forest;

        public SpanningTrees(MyUnionFind forest, Set<Object> edgeList, double spanningTreeCost) {
            super(edgeList, spanningTreeCost);
            this.forest = forest;
        }
    }

    private class MyUnionFind extends UnionFind<V> {
        private List<V> sortedRoots;

        public MyUnionFind(Set<V> vs) {
            super(vs);
        }

        public List<V> getSortedRoots() {
            lazyComputeSortedRoots();
            return this.sortedRoots;
        }

        private void lazyComputeSortedRoots() {
            if (sortedRoots == null) {
                Map<V, Integer> rankMap = super.getRankMap();
                Map<V, V> pMap = super.getParentMap();
                pMap.keySet().forEach(this::find);
                sortedRoots = new HashSet<>(pMap.values()).stream()
                    .sorted((o1, o2) -> o1 == o2 ? 0 : rankMap.get(o2) - rankMap.get(o1))
                    .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        @Override
        public Map<V, V> getParentMap() {
            return super.getParentMap();
        }
    }
}
