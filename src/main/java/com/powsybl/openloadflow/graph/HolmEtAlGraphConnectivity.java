/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.jgrapht.util.AVLTree;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class HolmEtAlGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, JGraphTModel<V, E>> {

    private final List<SpanningForest<V>> spanningForests = new ArrayList<>();

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
                spanningForests.get(i).removeEdge(edgeRemove.v1, edgeRemove.v2);
            }

            replace(edgeRemove, info.level);
        }
    }

    private void replace(EdgeRemove<V, E> edgeRemove, int level) {
        SpanningForest<V> forest = spanningForests.get(level);
        V u = edgeRemove.v1;
        V v = edgeRemove.v2;
        AVLTree<V> treeU = forest.find(u);
        AVLTree<V> treeV = forest.find(v);

        if (treeU.getSize() > treeV.getSize()) {
            treeU = treeV;
            u = edgeRemove.v2;
        }

        SpanningForest<V> above = spanningForests.get(level + 1);
        // TODO: also need to update level...
        above.addAll(treeU); // promote edges of the smallest tree

        // iterate over all incident edges to treeU
        JGraphTModel<V, E> graph = getGraph();
        for (Iterator<V> it = forest.verticesInComponent(u); it.hasNext();) {
            V vertexInComponent = it.next();

            for (E replacementCandidate : graph.getNeighborEdgesOf(vertexInComponent)) {
                EdgeInfo replacementEdgeInfo = edgeInfos.computeIfAbsent(replacementCandidate, e -> new EdgeInfo());
                if (replacementEdgeInfo.isTreeEdge() || replacementEdgeInfo.level != level) {
                    // FIXME: iterate over only nontree edges of level 'level' instead of iterating over every edges
                    // By the way, this implementation is iterating over every edges twice !!
                    continue;
                }

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
                        spanningForests.get(i).addEdge(src, target);
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
        SpanningForest<V> level0 = spanningForests.getFirst();

        if (level0.addEdge(edgeAdd.v1, edgeAdd.v2)) {
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
}
