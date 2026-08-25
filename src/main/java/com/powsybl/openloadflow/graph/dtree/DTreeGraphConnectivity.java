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
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, DTGraph<V, E>> {

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
        return new VerticesNotInMainComponent(excludedTree);
    }

    private final class VerticesNotInMainComponent extends AbstractSetView<V> {
        private final DTNode<V, E> excludedTree;
        private int size = -1;

        VerticesNotInMainComponent(DTNode<V, E> excludedTree) {
            this.excludedTree = excludedTree;
        }

        @Override
        public Iterator<V> iterator() {
            return new VerticesNotInMainComponentIterator(excludedTree.findRoot());
        }

        @Override
        public boolean contains(Object o) {
            if (o != null) {
                return getGraph().rootOf((V) o) != excludedTree.findRoot();
            }

            return false;
        }

        @Override
        public int size() {
            if (size < 0) {
                size = 0;

                DTNode<V, E> excludedTreeRoot = excludedTree.findRoot();
                size = getGraph().getRoots().stream()
                        .filter(root -> root != excludedTreeRoot)
                        .mapToInt(DTNode::size)
                        .sum();
            }

            return size;
        }
    }

    private class VerticesNotInMainComponentIterator implements Iterator<V> {

        private final DTNode<V, E> excludedTree;
        private int index = 0;
        private Iterator<V> curIt;

        VerticesNotInMainComponentIterator(DTNode<V, E> excludedTree) {
            this.excludedTree = excludedTree;
        }

        @Override
        public boolean hasNext() {
            if (curIt != null && curIt.hasNext()) {
                return true;
            }

            List<DTNode<V, E>> roots = getGraph().getRoots();
            while (index < roots.size()) {
                DTNode<V, E> next = roots.get(index);
                index++;

                if (next != excludedTree) {
                    curIt = new DFSIterator<>(next);
                    return true;
                }
            }

            return false;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            return curIt.next();
        }
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
