/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.util.UnionFind;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class MinimumSpanningTreeGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E> {

    private final Deque<SpanningTrees> mstSaved = new ArrayDeque<>();
    private SpanningTrees mst;

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        if (mst != null) {
            mst.addEdge(edgeAdd.v1, edgeAdd.v2, edgeAdd.e);
        }
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        if (mst != null) {
            mst.addVertex(vertexAdd.v);
        }
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        if (mst == null || mst.getEdges().contains(edgeRemove.e)) {
            mst = null;
            componentSets = null;
        }
    }

    @Override
    public void startTemporaryChanges() {
        super.startTemporaryChanges();
        if (mst == null) {
            mst = new KruskalMinimumSpanningTrees().getSpanningTree();
        }
        mstSaved.add(mst);
        mst = new SpanningTrees(mst);
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {
        if (mstSaved.isEmpty()) {
            throw new IllegalArgumentException("Corrupted connectivity cache");
        }
        mst = mstSaved.pollLast();
        componentSets = null;
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        Set<V> cc = mst.forest.getConnectedComponent(vertex);
        return componentSets.indexOf(cc);
    }

    @Override
    protected void updateComponents() {
        if (mst == null) {
            mst = new KruskalMinimumSpanningTrees().getSpanningTree();
        }
        if (componentSets == null) {
            componentSets = mst.forest.getConnectedComponents();
        }
    }

    class KruskalMinimumSpanningTrees implements SpanningTreeAlgorithm<Object> {

        @Override
        public SpanningTrees getSpanningTree() {
            Graph<V, E> graph = getGraph();
            MyUnionFind forest = new MyUnionFind(graph.vertexSet());
            Set<Object> edgeList = new HashSet<>();
            SpanningTrees spanningTree = new SpanningTrees(forest, edgeList, 0);

            for (E edge : graph.edgeSet()) {
                V source = graph.getEdgeSource(edge);
                V target = graph.getEdgeTarget(edge);
                spanningTree.addEdge(source, target, edge);
            }

            return spanningTree;
        }
    }

    private class SpanningTrees extends SpanningTreeAlgorithm.SpanningTreeImpl<Object> {
        private final transient MyUnionFind forest;

        public SpanningTrees(MyUnionFind forest, Set<Object> edgeList, double spanningTreeCost) {
            super(edgeList, spanningTreeCost);
            this.forest = forest;
        }

        public SpanningTrees(SpanningTrees other) {
            super(new LinkedHashSet<>(other.getEdges()), other.getWeight());
            this.forest = new MyUnionFind(other.forest);
        }

        private void addEdge(V source, V target, E edge) {
            if (!forest.find(source).equals(forest.find(target))) {
                forest.union(source, target);
                getEdges().add(edge);
                forest.invalidateConnectedComponents();
            }
        }

        public void addVertex(V v) {
            forest.addElement(v);
        }
    }

    private class MyUnionFind extends UnionFind<V> {
        private Map<V, Set<V>> rootConnectedComponentMap;

        public MyUnionFind(Set<V> vs) {
            super(vs);
        }

        public MyUnionFind(MyUnionFind other) {
            super(other.getParentMap().keySet());
            other.getRankMap().forEach((k, v) -> getRankMap().put(k, v));
            other.getParentMap().forEach((k, v) -> getParentMap().put(k, v));
            this.rootConnectedComponentMap = null;
        }

        public List<Set<V>> getConnectedComponents() {
            lazyComputeConnectedComponents();
            return rootConnectedComponentMap.values().stream().sorted((cc1, cc2) -> cc2.size() - cc1.size()).collect(Collectors.toList());
        }

        public Set<V> getConnectedComponent(V vertex) {
            lazyComputeConnectedComponents();
            V root = super.getParentMap().get(vertex);
            return rootConnectedComponentMap.get(root);
        }

        private void lazyComputeConnectedComponents() {
            if (rootConnectedComponentMap == null) {
                rootConnectedComponentMap = new HashMap<>();
                getGraph().vertexSet().forEach(vertex -> rootConnectedComponentMap.computeIfAbsent(find(vertex), k -> new HashSet<>()).add(vertex));
            }
        }

        @Override
        public void addElement(V v) {
            super.addElement(v);
            Set<V> connectedComponent = new HashSet<>();
            connectedComponent.add(v);
            rootConnectedComponentMap.put(find(v), connectedComponent);
        }

        public void invalidateConnectedComponents() {
            rootConnectedComponentMap = null;
        }
    }
}
