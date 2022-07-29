/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.util.UnionFind;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class MinimumSpanningTreeGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private final Deque<SpanningTrees> mstSaved = new ArrayDeque<>();
    private SpanningTrees mst;
    private final Graph<V, E> graph;

    private final Deque<Deque<GraphModification<V, E>>> graphModifications = new ArrayDeque<>();

    public MinimumSpanningTreeGraphConnectivity() {
        this.graph = new Pseudograph<>(null, null, false);
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        GraphModification<V, E> modif = new EdgeAdd<>(vertex1, vertex2, edge);
        modif.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(modif);
            mst.addEdge(vertex1, vertex2, edge);
        }
    }

    @Override
    public void removeEdge(E edge) {
        if (!graph.containsEdge(edge)) {
            throw new PowsyblException("No such edge in graph: " + edge);
        }
        V vertex1 = graph.getEdgeSource(edge);
        V vertex2 = graph.getEdgeTarget(edge);
        GraphModification<V, E> modif = new EdgeRemove<>(vertex1, vertex2, edge);
        modif.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(modif);
            if (mst == null || mst.getEdges().contains(edge)) {
                invalidateMst();
            }
        }
    }

    @Override
    public void save() {
        invalidateMst();
        mst = new KruskalMinimumSpanningTrees().getSpanningTree();
        mstSaved.add(new SpanningTrees(mst));
        graphModifications.add(new ArrayDeque<>());
    }

    @Override
    public void reset() {
        if (graphModifications.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }
        Deque<GraphModification<V, E>> m = graphModifications.pollLast();
        mst = mstSaved.pollLast();
        if (m.isEmpty()) {
            // there are no modifications left at this level: going to lower level.
            if (graphModifications.isEmpty()) {
                throw new PowsyblException("Cannot reset, no remaining saved connectivity");
            }
            m = graphModifications.pollLast();
            mst = mstSaved.pollLast();
        }
        graphModifications.add(new ArrayDeque<>());
        m.descendingIterator().forEachRemaining(gm -> gm.undo(graph));
    }

    private void invalidateMst() {
        this.mst = null;
    }

    @Override
    public int getComponentNumber(V vertex) {
        checkSaved();
        checkVertex(vertex);
        lazyCompute();
        V root = mst.forest.getRoot(vertex);
        Set<V> cc = mst.forest.getRootConnectedComponentMap().get(root);
        return mst.forest.getConnectedComponents().indexOf(cc);
    }

    @Override
    public List<Set<V>> getSmallComponents() {
        checkSaved();
        List<Set<V>> components = getConnectedComponents();
        return components.subList(1, components.size());
    }

    private List<Set<V>> getConnectedComponents() {
        checkSaved();
        lazyCompute();
        return mst.forest.getConnectedComponents();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSaved();
        checkVertex(vertex);
        return getConnectedComponents().get(getComponentNumber(vertex));
    }

    @Override
    public Set<V> getNonConnectedVertices(V vertex) {
        checkSaved();
        checkVertex(vertex);
        return getConnectedComponents().stream().filter(component -> !component.contains(vertex))
            .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private void lazyCompute() {
        if (this.mst == null) {
            this.mst = new KruskalMinimumSpanningTrees().getSpanningTree();
        }
    }

    private void checkSaved() {
        if (graphModifications.isEmpty()) {
            throw new PowsyblException("Cannot call connectivity computation, no remaining saved connectivity");
        }
    }

    private void checkVertex(V vertex) {
        if (!graph.containsVertex(vertex)) {
            throw new AssertionError("given vertex " + vertex + " is not in the graph");
        }
    }

    class KruskalMinimumSpanningTrees implements SpanningTreeAlgorithm<Object> {

        @Override
        public SpanningTrees getSpanningTree() {
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
    }

    private class MyUnionFind extends UnionFind<V> {
        private List<Set<V>> connectedComponents;
        private Map<V, Set<V>> rootConnectedComponentMap;

        public MyUnionFind(Set<V> vs) {
            super(vs);
        }

        public MyUnionFind(MyUnionFind other) {
            super(other.getParentMap().keySet());
            other.getRankMap().forEach((k, v) -> getRankMap().put(k, v));
            other.getParentMap().forEach((k, v) -> getParentMap().put(k, v));
            this.connectedComponents = other.connectedComponents;
            this.rootConnectedComponentMap = other.rootConnectedComponentMap;
        }

        public List<Set<V>> getConnectedComponents() {
            lazyComputeConnectedComponents();
            return this.connectedComponents;
        }

        public Map<V, Set<V>> getRootConnectedComponentMap() {
            lazyComputeConnectedComponents();
            return rootConnectedComponentMap;
        }

        private void lazyComputeConnectedComponents() {
            if (connectedComponents == null || rootConnectedComponentMap == null) {
                rootConnectedComponentMap = new HashMap<>();
                graph.vertexSet().forEach(vertex -> rootConnectedComponentMap.computeIfAbsent(find(vertex), k -> new HashSet<>()).add(vertex));
                connectedComponents = rootConnectedComponentMap.values().stream().sorted((cc1, cc2) -> cc2.size() - cc1.size()).collect(Collectors.toList());
            }
        }

        public V getRoot(V vertex) {
            return super.getParentMap().get(vertex);
        }

        public void invalidateConnectedComponents() {
            connectedComponents = null;
            rootConnectedComponentMap = null;
        }
    }
}
