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
public class HolmEtAlGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, HolmEtAlGraphConnectivity.Graph<V, E>> {

    private final TObjectIntMap<V> vertexToComponent = new TObjectIntHashMap<>();

    public HolmEtAlGraphConnectivity() {
        super(new Graph<>());
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        // simple because everything is done in Graph
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        // simple because everything is done in Graph
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        // simple because everything is done in Graph
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

        componentSets = new ArrayList<>();
        vertexToComponent.clear();

        Graph<V, E> graph = getGraph();
        SpanningForest<V, E> fullForest = graph.spanningForests.getFirst();

        for (Iterator<V> roots = fullForest.roots(); roots.hasNext();) {
            V root = roots.next();

            Set<V> component = new HashSet<>();
            for (Iterator<V> it = fullForest.verticesInComponent(root); it.hasNext();) {
                V vertex = it.next();
                component.add(vertex);
            }

            componentSets.add(component);
        }

        componentSets.sort(Comparator.comparingInt(c -> -c.size()));

        int i = 0;
        for (Set<V> comp : componentSets) {
            for (V vertex : comp) {
                vertexToComponent.put(vertex, i);
            }

            i++;
        }
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return vertexToComponent.get(vertex);
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    public static final class Graph<V, E> implements GraphModel<V, E> {

        private final List<SpanningForest<V, E>> spanningForests = new ArrayList<>();
        private final List<Map<V, SetMultimap<V, E>>> adjacencyList = new ArrayList<>();

        // contains additional data for every tree edges
        // some nontree edge may be absent. In case an
        // EdgeInfo doesn't exist, the edge should be considered
        // as a nontree edge with level 0.
        private final Map<E, EdgeInfo<V>> edgeInfos = new HashMap<>();

        public Graph() {
            spanningForests.add(new SpanningForest<>());
            adjacencyList.add(new HashMap<>());
        }

        private void addNonTreeEdgeAtLevel(V v1, V v2, E e, int level) {
            adjacencyList.get(level).get(v1).put(v2, e);
            adjacencyList.get(level).get(v2).put(v1, e);
        }

        private void removeNonTreeEdgeAtLevel(V v1, V v2, E e, int level) {
            adjacencyList.get(level).get(v1).remove(v2, e);
            adjacencyList.get(level).get(v2).remove(v1, e);
        }

        @Override
        public void addEdge(V v1, V v2, E e) {
            if (containsEdge(e)) {
                return;
            }

            // try link v1 and v2
            boolean treeEdge;
            if (!spanningForests.getFirst().addEdge(v1, v2, e)) {
                // they are already in the same tree
                addNonTreeEdgeAtLevel(v1, v2, e, 0);
                treeEdge = false;
            } else {
                treeEdge = true;
            }

            edgeInfos.put(e, new EdgeInfo<>(0, treeEdge, v1, v2));
        }

        private void promoteTreeEdges(V representative, int currentLevel, int newLevel) {
            SpanningForest<V, E> forest = spanningForests.get(currentLevel);
            SpanningForest<V, E> newForest = spanningForests.get(newLevel);

            for (Iterator<E> it = forest.edgesInComponent(representative); it.hasNext();) {
                E edge = it.next();
                V src = getEdgeSource(edge);
                V dest = getEdgeTarget(edge);

                newForest.addEdge(src, dest, edge);
            }
        }

        private void replace(V v1, V v2, int level) {
            SpanningForest<V, E> forest = spanningForests.get(level);
            V smallest = v1; // vertex in the smallest component between T_v1 and T_v2
            if (forest.componentSize(v1) > forest.componentSize(v2)) {
                smallest = v2;
            }

            // promote edges of the smallest tree
            promoteTreeEdges(smallest, level, level + 1);

            // iterate over all incident non-tree edges of level 'level' to the smallest tree
            for (Iterator<V> it = forest.verticesInComponent(smallest); it.hasNext();) {
                V vertexInComponent = it.next();

                for (E replacementCandidate : adjacencyList.get(level).get(vertexInComponent).values()) {
                    EdgeInfo<V> info = edgeInfos.get(replacementCandidate);

                    if (forest.connected(info.src, info.dest)) {
                        // src and target are still connected, even after the separation of T_u and T_v
                        // that means that src and target were already in the same spanning tree, which is T_u
                        // because we are iterating over the incident edges of T_u

                        removeNonTreeEdgeAtLevel(info.src, info.dest, replacementCandidate, info.level);
                        info.level = level + 1; // increase level
                        addNonTreeEdgeAtLevel(info.src, info.dest, replacementCandidate, info.level);
                    } else {
                        // src and target are not connected. That means they are in two different
                        // spanning trees. One of them is T_u (because we are iterating over the incident
                        // edges of T_u). The other one must be in T_v. Indeed, if it is not, that would
                        // mean the spanning forest wasn't actually a spanning forest.
                        // Conclusion: this candidate edge is a replacement edge

                        info.treeEdge = true;
                        for (int i = 0; i <= level; i++) {
                            spanningForests.get(i).addEdge(info.src, info.dest, replacementCandidate);
                        }
                        return;
                    }
                }
            }

            if (level > 0) {
                replace(v1, v2, level - 1);
            }
        }

        @Override
        public void removeEdge(E e) {
            EdgeInfo<V> info = edgeInfos.remove(e);
            if (info == null) {
                return;
            }

            if (info.isTreeEdge()) {
                // remove edge from every spanning trees. the edge cannot be in level > 'info.level'
                for (int i = 0; i <= info.level; i++) {
                    spanningForests.get(i).removeEdge(info.src, info.dest, e);
                }

                replace(info.src, info.dest, info.level);
            } else {
                removeNonTreeEdgeAtLevel(info.src, info.dest, e, info.level);
            }
        }

        @Override
        public void addVertex(V v) {
            if (containsVertex(v)) {
                return;
            }

            // these lines could be avoided if the number of vertices were known
            // at the creation of the object
            if (currentLevelMax() != newLevelMax(1)) {
                Map<V, SetMultimap<V, E>> newLevel = new HashMap<>();
                for (V vertex : adjacencyList.getFirst().keySet()) {
                    newLevel.put(vertex, HashMultimap.create());
                }
                adjacencyList.add(newLevel);

                SpanningForest<V, E> newLevelForest = new SpanningForest<>();
                spanningForests.add(newLevelForest);
            }

            // add v
            for (Map<V, SetMultimap<V, E>> list : adjacencyList) {
                list.put(v, HashMultimap.create());
            }

            for (SpanningForest<V, E> forest : spanningForests) {
                forest.addVertex(v);
            }
        }

        private int currentLevelMax() {
            return adjacencyList.size();
        }

        // l_max = floor(log2(vertexCount)) + 1
        private int newLevelMax(int vertexToAdd) {
            int vertexCount = adjacencyList.getFirst().size() + vertexToAdd;

            return (int) (Math.floor(Math.log(vertexCount) / Math.log(2)) + 1);
        }

        @Override
        public void removeVertex(V v) {
            throw new UnsupportedOperationException("removeVertex unimplemented because VertexAdd are never undone");
        }

        @Override
        public boolean containsVertex(V vertex) {
            return adjacencyList.getFirst().containsKey(vertex);
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

        public Iterable<E> getEdgesBetween(V vertex1, V vertex2, int level) {
            return adjacencyList.get(level).get(vertex1).get(vertex2);
        }

        @Override
        public Set<E> getEdges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<E> getNeighborEdgesOf(V v) {
            Set<E> set = new HashSet<>();

            for (int i = 0; i < adjacencyList.size(); i++) {
                set.addAll(adjacencyList.get(i).get(v).values());
            }

            return set;
        }

        @Override
        public int getNeighborEdgeCountOf(V v) {
            return getNeighborEdgesOf(v).size();
        }

        @Override
        public Set<V> getVertices() {
            return adjacencyList.getFirst().keySet();
        }

        @Override
        public List<V> getNeighborVerticesOf(V v) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EdgeInfo<V> {
        private int level;
        private boolean treeEdge;

        private final V src;
        private final V dest;

        EdgeInfo(int level, boolean treeEdge, V src, V dest) {
            this.level = level;
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
