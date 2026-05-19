/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class HolmEtAlGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, JGraphTModel<V, E>> {

    private final List<SpanningForest<V, E>> spanningForests = new ArrayList<>();

    private final MultiLevelAdjacencyList adjacencyList = new MultiLevelAdjacencyList();

    // contains additional data for every tree edges
    // some nontree edge may be absent. In case an
    // EdgeInfo doesn't exist, the edge should be considered
    // as a nontree edge with level 0.
    private final Map<E, EdgeInfo> edgeInfos = new HashMap<>();

    public HolmEtAlGraphConnectivity() {
        super(new JGraphTModel<>());
        spanningForests.add(new SpanningForest<>());
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        EdgeInfo info = edgeInfos.remove(edgeRemove.e);

        if (info != null && info.isTreeEdge()) {
            // remove edge from every spanning trees. the edge cannot be in level > 'info.level'
            for (int i = 0; i <= info.level; i++) {
                spanningForests.get(i).removeEdge(edgeRemove.v1, edgeRemove.v2, edgeRemove.e);
            }

            replace(edgeRemove, info.level);
        }
    }

    private void replace(EdgeRemove<V, E> edgeRemove, int level) {
        SpanningForest<V, E> forest = spanningForests.get(level);
        V u = edgeRemove.v1; // vertex in the smallest component between T_u and T_v
        V v = edgeRemove.v2;

        if (forest.componentSize(u) > forest.componentSize(v)) {
            u = edgeRemove.v2;
        }

        SpanningForest<V, E> above = spanningForests.get(level + 1);

        // promote edges of the smallest tree
        for (Iterator<E> it = forest.edgesInComponent(u); it.hasNext();) {
            E edge = it.next();
            V src = getGraph().getEdgeSource(edge);
            V dest = getGraph().getEdgeTarget(edge);

            above.addEdge(src, dest, edge);
            for (E actualEdge : getGraph().getEdgesBetween(src, dest)) {
                edgeInfos.get(actualEdge).level = level + 1;
            }
        }

        // iterate over all incident non-tree edges of level 'level' to the smallest tree
        for (Iterator<V> it = forest.verticesInComponent(u); it.hasNext();) {
            V vertexInComponent = it.next();

            for (E replacementCandidate : adjacencyList.adjacentEdges(level, vertexInComponent)) {
                EdgeInfo replacementEdgeInfo = edgeInfos.computeIfAbsent(replacementCandidate, e -> new EdgeInfo());

                V src = graph.getEdgeSource(replacementCandidate);
                V target = graph.getEdgeTarget(replacementCandidate);

                if (forest.connected(src, target)) {
                    // src and target are still connected, even after the separation of T_u and T_v
                    // that means that src and target were already in the same spanning tree, which is T_u
                    // because we are iterating over the incident edges of T_u

                    replacementEdgeInfo.level = level + 1; // increase level
                } else {
                    // src and target are not connected. That means they are in two different
                    // spanning trees. One of them is T_u (because we are iterating over the incident
                    // edges of T_u). The other one must be in T_v. Indeed, if it is not, that would
                    // mean the spanning forest wasn't actually a spanning forest.
                    // Conclusion: this candidate edge is a replacement edge

                    replacementEdgeInfo.treeEdge = true;
                    for (int i = 0; i <= level; i++) {
                        spanningForests.get(i).addEdge(src, target, replacementCandidate);
                    }
                    return;
                }
            }
        }

        if (level > 0) {
            replace(edgeRemove, level - 1);
        }
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        SpanningForest<V, E> level0 = spanningForests.getFirst();

        if (level0.addEdge(edgeAdd.v1, edgeAdd.v2, edgeAdd.e)) {
            edgeInfos.put(edgeAdd.e, new EdgeInfo(0, true));
        }
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        // nothing to do here
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {

    }

    @Override
    protected void updateComponents() {

    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return 0;
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return false;
    }

    private static final class EdgeInfo {
        private int level;
        private boolean treeEdge;

        EdgeInfo() {
            this(0, false);
        }

        EdgeInfo(int level, boolean treeEdge) {
            this.level = level;
            this.treeEdge = treeEdge;
        }

        public boolean isNonTreeEdge() {
            return !treeEdge;
        }

        public boolean isTreeEdge() {
            return treeEdge;
        }
    }

    private final class MultiLevelAdjacencyList {

        private final List<Map<V, List<E>>> adjacencyList = new ArrayList<>();

        public void addEdge(int level, V src, V dest, E edge) {
            adjacencyList.get(level).compute(src, (s, list) -> {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(edge);
                return list;
            });

            adjacencyList.get(level).compute(dest, (s, list) -> {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(edge);
                return list;
            });
        }

        public void removeEdge(int level, V src, V dest, E edge) {
            adjacencyList.get(level).get(src).remove(edge);
            adjacencyList.get(level).get(dest).remove(edge);
        }

        public void changeLevel(int level, V src, V dest, E edge, int newLevel) {
            removeEdge(level, src, dest, edge);
            addEdge(newLevel, src, dest, edge);
        }

        public Iterable<E> adjacentEdges(int level, V src) {
            return adjacencyList.get(level).get(src);
        }
    }
}
