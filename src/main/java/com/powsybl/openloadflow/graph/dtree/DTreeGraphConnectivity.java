/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtree;

import com.powsybl.openloadflow.graph.*;

import java.util.*;

/**
 * D-Tree implementation from <cite>Qing Chen, Oded Lachish, Sven Helmer, and Michael H. Böhlen. Dynamic
 * Spanning Trees for Connectivity Queries on Fully-dynamic Undirected
 * Graphs. PVLDB, 15(11): 3263 - 3276, 2022.
 * doi:10.14778/3551793.3551868</cite>. An extended version is available at
 * <a href="https://arxiv.org/pdf/2207.06887">https://arxiv.org/pdf/2207.06887</a>
 *
 * <p>
 * This implementation differs from the paper in the following ways:
 * <ul>
 *     <li>Instead of searching the best replacement edge, we select the first one encountered.
 *     It increases the sum of distances but improves performance of {@link #removeEdge(Object)}.</li>
 *     <li>TODO:findRootOptReroot</li>
 *     <li>When inserting a non-tree edge whose endpoints have a depth difference delta >= 2, we disconnect
 *     the ancestor at distance delta / 2 − 1 from the deeper endpoint from its parent, rather than disconnecting
 *     the ancestor at distance delta − 2 as in the original algorithm. It improves the sum of distances and
 *     performances. This was proposed by: <cite>Lantian Xu, Dong Wen, Lu Qin, Ronghua Li, Ying Zhang, and
 *     Xuemin Lin. 2024. Constant-time Connectivity Querying in Dynamic Graphs. Proc. ACM Manag. Data 2, 6
 *     (SIGMOD), Article 230 (December 2024), 23 pages.
 *     <a href="https://doi.org/10.1145/3698805">https://doi.org/10.1145/3698805</a></cite>.</li>
 * </ul>
 * </p>
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, DTGraph<V, E>> implements SpanningForestGraphConnectivity<V, E> {

    public DTreeGraphConnectivity() {
        super(new DTGraph<>());
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        // only invalidate components.
        // update is done directly in DTGraph because the graph is stored inside the spanning forest.
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        // only invalidate components.
        // update is done directly in DTGraph because the graph is stored inside the spanning forest.
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        // only invalidate components.
        // update is done directly in DTGraph because the graph is stored inside the spanning forest.
        componentSets = null;
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {
        // only invalidate components.
        // update is done directly in undoTemporaryChanges because the graph is stored inside the spanning forest.
        componentSets = null;
    }

    @Override
    protected void updateComponents() {
        if (componentSets != null) {
            return;
        }

        DTGraph<V, E> graph = getGraph();
        // sorting roots will sort components as components is a wrapper around roots
        graph.sortComponents();
        componentSets = graph.allComponents();
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return getGraph().rootOf(vertex).getIndex();
    }

    @Override
    public int getNbConnectedComponents() {
        checkSavedContext();
        return getGraph().getNbConnectedComponent();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        checkVertex(vertex);
        return getGraph().componentView(vertex);
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        checkSavedContext();
        checkVertex(vertex);

        DTGraph<V, E> graph = getGraph();
        DTNode<V, E> excludedTree = graph.rootOf(vertex);

        Set<V> components = new HashSet<>();
        for (DTNode<V, E> root : graph.getRoots()) {
            if (root != excludedTree) {
                components.addAll(root.componentView());
            }
        }

        return components;
    }

    @Override
    protected Set<V> getVerticesNotInMainComponent(V mainComponentVertex) {
        // first determine the excluded tree: either the tree containing
        // the mainComponentVertex, either the biggest tree
        DTNode<V, E> excludedTree = getMainComponentRoot(mainComponentVertex);
        return new VerticesNotInMainComponent<>(getGraph(), excludedTree);
    }

    @Override
    public long computeSumOfDistances() {
        long sum = 0;

        for (V vertex : getGraph().getVertices()) {
            sum += getGraph().getNodeThrowIfInexistent(vertex).findRootWithDepth().depth();
        }

        return sum;
    }

    @Override
    public int vertexCount() {
        return getGraph().getVertices().size();
    }

    /**
     * @param mainComponentVertex a vertex in the main component tree, may be null
     * @return the root of the tree containing mainComponentVertex, if not null,
     * or the root of the biggest tree
     */
    private DTNode<V, E> getMainComponentRoot(V mainComponentVertex) {
        DTGraph<V, E> graph = getGraph();

        if (mainComponentVertex != null) {
            return graph.rootOf(mainComponentVertex);
        } else {
            List<DTNode<V, E>> roots = getGraph().getRoots();
            DTNode<V, E> biggestRoot = roots.getFirst();

            for (int i = 1; i < roots.size(); i++) {
                DTNode<V, E> root = roots.get(i);
                if (root.size() > biggestRoot.size()) {
                    biggestRoot = root;
                }
            }

            return biggestRoot;
        }
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }
}
