/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class HolmEtAlWithoutLevelGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, HolmEtAlWithoutLevelGraphConnectivity.Graph<V, E>> {

    private TObjectIntMap<V> vertexToComponent;

    public HolmEtAlWithoutLevelGraphConnectivity() {
        super(new HolmEtAlWithoutLevelGraphConnectivity.Graph<>());
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        // simple because everything is done in Graph
        vertexToComponent = null;
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        // simple because everything is done in Graph
        vertexToComponent = null;
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        // simple because everything is done in Graph
        vertexToComponent = null;
        componentSets = null;
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {
        componentSets = null;

        for (Iterator<GraphModification<V, E>> it = m.descendingIterator(); it.hasNext();) {
            GraphModification<V, E> modification = it.next();
            modification.undo(getGraph());
        }
    }

    @Override
    protected void updateComponents() {
        if (componentSets != null) {
            return;
        }

        vertexToComponent = null;
        componentSets = getGraph().spanningForest.getComponents();
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return getVertexToComponent().get(vertex);
    }

    private TObjectIntMap<V> getVertexToComponent() {
        if (vertexToComponent == null) {
            vertexToComponent = new TObjectIntHashMap<>();

            // don't compute mapping for the biggest component
            // vertexToComponent.get return 0 (the biggest component)
            // if the key isn't present
            for (int i = 1; i < componentSets.size(); i++) {
                Set<V> comp = componentSets.get(i);

                for (V vertex : comp) {
                    vertexToComponent.put(vertex, i);
                }
            }
        }

        return vertexToComponent;
    }

    @Override
    public int getNbConnectedComponents() {
        return getGraph().spanningForest.treeCount();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        return getGraph().spanningForest.getComponent(vertex);
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        return getGraph().spanningForest.getNonConnectedVertices(vertex);
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    public static final class Graph<V, E> implements GraphModel<V, E> {

        private final SpanningForest<V, E> spanningForest = new SpanningForest<>();
        private final Map<V, SetMultimap<V, E>> adjacencyList = new HashMap<>();

        private final Map<E, EdgeInfo<V>> edgeInfos = new HashMap<>();

        public Graph() {

        }

        private void addNonTreeEdge(V v1, V v2, E e) {
            adjacencyList.get(v1).put(v2, e);
            adjacencyList.get(v2).put(v1, e);
        }

        private void removeNonTreeEdge(V v1, V v2, E e) {
            adjacencyList.get(v1).remove(v2, e);
            adjacencyList.get(v2).remove(v1, e);
        }

        @Override
        public void addEdge(V v1, V v2, E e) {
            if (containsEdge(e)) {
                return;
            }

            // try link v1 and v2
            boolean treeEdge;
            if (!spanningForest.addEdge(v1, v2, e)) {
                // they are already in the same tree
                addNonTreeEdge(v1, v2, e);
                treeEdge = false;
            } else {
                treeEdge = true;
            }

            edgeInfos.put(e, new EdgeInfo<>(treeEdge, v1, v2));
        }

        private boolean replace(V v1, V v2) {
            V smallest = v1; // vertex in the smallest component between T_v1 and T_v2
            if (spanningForest.treeSize(v1) > spanningForest.treeSize(v2)) {
                smallest = v2;
            }

            // iterate over all incident non-tree edges to the smallest tree
            for (Iterator<V> it = spanningForest.verticesInComponent(smallest); it.hasNext();) {
                V vertexInComponent = it.next();

                for (E replacementCandidate : adjacencyList.get(vertexInComponent).values()) {
                    EdgeInfo<V> info = edgeInfos.get(replacementCandidate);

                    if (!spanningForest.connected(info.src, info.dest)) {
                        // src and target are not connected. That means they are in two different
                        // spanning trees. One of them is T_u (because we are iterating over the incident
                        // edges of T_u). The other one must be in T_v. Indeed, if it is not, that would
                        // mean the spanning forest wasn't actually a spanning forest.
                        // Conclusion: this candidate edge is a replacement edge

                        removeNonTreeEdge(info.src, info.dest, replacementCandidate);
                        info.treeEdge = true;
                        spanningForest.addEdge(info.src, info.dest, replacementCandidate);
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public void removeEdge(E e) {
            EdgeInfo<V> info = edgeInfos.remove(e);
            if (info == null) {
                return;
            }

            if (info.isTreeEdge()) {
                // remove the edge from the spanning forest
                spanningForest.removeEdge(info.src, info.dest, e);

                // search for a replacement
                replace(info.src, info.dest);
            } else {
                removeNonTreeEdge(info.src, info.dest, e);
            }
        }

        @Override
        public void addVertex(V v) {
            if (containsVertex(v)) {
                return;
            }

            // add v
            adjacencyList.put(v, HashMultimap.create());
            spanningForest.addVertex(v);
        }

        @Override
        public void removeVertex(V v) {
            // terrible implementation
            spanningForest.removeVertex(v);

            adjacencyList.remove(v);

            for (SetMultimap<V, E> map : adjacencyList.values()) {
                map.removeAll(v);
            }
        }

        @Override
        public boolean containsVertex(V vertex) {
            return adjacencyList.containsKey(vertex);
        }

        @Override
        public boolean containsEdge(E edge) {
            return edgeInfos.containsKey(edge);
        }

        @Override
        public V getEdgeSource(E edge) {
            EdgeInfo<V> info = edgeInfos.get(edge);
            if (info == null) {
                return null;
            }

            return info.src;
        }

        @Override
        public V getEdgeTarget(E edge) {
            EdgeInfo<V> info = edgeInfos.get(edge);
            if (info == null) {
                return null;
            }

            return info.dest;
        }

        @Override
        public Set<E> getEdgesBetween(V vertex1, V vertex2) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<E> getEdges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<E> getNeighborEdgesOf(V v) {
            Set<E> set = new HashSet<>(adjacencyList.get(v).values());

            for (Iterator<E> it = spanningForest.adjacentEdges(v); it.hasNext();) {
                set.add(it.next());
            }

            return set;
        }

        @Override
        public int getNeighborEdgeCountOf(V v) {
            return getNeighborEdgesOf(v).size();
        }

        @Override
        public Set<V> getVertices() {
            return adjacencyList.keySet();
        }

        @Override
        public List<V> getNeighborVerticesOf(V v) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EdgeInfo<V> {
        private boolean treeEdge;

        private final V src;
        private final V dest;

        EdgeInfo(boolean treeEdge, V src, V dest) {
            this.treeEdge = treeEdge;
            this.src = src;
            this.dest = dest;
        }

        public boolean isNonTreeEdge() {
            return !treeEdge;
        }

        public boolean isTreeEdge() {
            return treeEdge;
        }
    }
}
