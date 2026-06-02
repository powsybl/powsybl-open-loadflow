/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * D-Tree implementation from <a href="https://arxiv.org/pdf/2207.06887"/>
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, DTreeGraphConnectivity.Graph<V, E>> {

    public DTreeGraphConnectivity() {
        super(new Graph<>());
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        componentSets = null;
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        componentSets = null;
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {
        componentSets = null;
    }

    @Override
    protected void updateComponents() {
        if (componentSets != null) {
            return;
        }

        List<Graph<V, E>.DTNode> roots = getGraph().roots;
        roots.sort((s1, s2) -> s2.size - s1.size);

        componentSets = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            Graph<V, E>.DTNode root = roots.get(i);
            root.rootIndex = i;
            componentSets.add(root.createSetView());
        }
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return getGraph().vertexToTreeNode.get(vertex).findRootOptReroot().rootIndex;
    }

    @Override
    public int getNbConnectedComponents() {
        checkSavedContext();
        return getGraph().roots.size();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        checkVertex(vertex);
        return getGraph().vertexToTreeNode.get(vertex).findRootOptReroot().createSetView();
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        checkSavedContext();
        checkVertex(vertex);

        Graph<V, E> graph = getGraph();
        Graph<V, E>.DTNode excludedTree = graph.vertexToTreeNode.get(vertex).findRootOptReroot();

        Set<V> components = new HashSet<>();
        for (Graph<V, E>.DTNode root : graph.roots) {
            if (root != excludedTree) {
                components.addAll(root.createSetView());
            }
        }

        return components;
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    public static final class Graph<V, E> implements GraphModel<V, E> {

        private final Map<V, DTNode> vertexToTreeNode = new HashMap<>();
        private final Map<E, Edge<V>> edges = new HashMap<>();

        private final List<DTNode> roots = new ArrayList<>();

        @Override
        public void addEdge(V u, V v, E e) {
            if (containsEdge(e)) {
                return;
            }

            DTNode nodeU = vertexToTreeNode.get(u);
            DTNode nodeV = vertexToTreeNode.get(v);

            Pair<DTNode, Integer> rootUdist = nodeU.findRootWithDist();
            Pair<DTNode, Integer> rootVdist = nodeV.findRootWithDist();

            boolean treeEdge;
            if (rootUdist.getKey() == rootVdist.getKey()) {
                // insert non tree edge
                insertNonTreeEdge(rootUdist.getKey(), nodeU, rootUdist.getValue(), nodeV, rootVdist.getValue(), e);
                treeEdge = false;
            } else {
                // insert tree edge
                insertTreeEdge(rootUdist.getKey(), nodeU, rootVdist.getKey(), nodeV, e);
                treeEdge = true;
            }

            edges.put(e, new Edge<>(u, v, treeEdge));
        }

        private void insertNonTreeEdge(DTNode root, DTNode nodeU, int depthU, DTNode nodeV, int depthV, E edge) {
            DTNode deep;
            DTNode shallow;
            int delta;

            if (depthU <= depthV) {
                shallow = nodeU;
                deep = nodeV;
                delta = depthV - depthU;
            } else {
                shallow = nodeV;
                deep = nodeU;
                delta = depthU - depthV;
            }

            if (delta < 2) {
                // no changes in the BFS tree
                nodeU.nonTreeEdges.add(edge);
                nodeV.nonTreeEdges.add(edge);
            } else {
                DTNode i = deep;
                for (int j = 0; j < delta - 2; j++) {
                    i = i.parent;
                }

                i.parent.nonTreeEdges.add(i.parentEdge);
                i.nonTreeEdges.add(i.parentEdge);

                unlink(i);
                link(root, shallow, deep, edge);
            }
        }

        private void insertTreeEdge(DTNode rootU, DTNode nodeU, DTNode rootV, DTNode nodeV, E edge) {
            DTNode toRemove;
            if (rootU.size < rootV.size) {
                nodeU.makeRoot();
                link(rootV, nodeV, nodeU, edge);
                toRemove = nodeU;
            } else {
                nodeV.makeRoot();
                link(rootU, nodeU, nodeV, edge);
                toRemove = nodeV;
            }

            removeRoot(toRemove);
        }

        @Override
        public void removeEdge(E e) {
            Edge<V> edge = edges.remove(e);
            if (edge == null) {
                return;
            }

            DTNode nodeU = vertexToTreeNode.get(edge.u);
            DTNode nodeV = vertexToTreeNode.get(edge.v);

            if (edge.treeEdge) {
                removeTreeEdge(nodeU, nodeV, e);
            } else {
                removeNonTreeEdge(nodeU, nodeV, e);
            }
        }

        private void removeTreeEdge(DTNode nodeU, DTNode nodeV, E edge) {
            DTNode child;

            if (nodeU == nodeV.parent) {
                child = nodeV;
            } else {
                child = nodeU;
            }

            DTNode otherTree = unlink(child);
            addRoot(child);

            DTNode small;
            DTNode large;
            if (child.size < otherTree.size) {
                small = child;
                large = otherTree;
            } else {
                small = otherTree;
                large = child;
            }

            replace(small);
        }

        private void replace(DTNode root) {
            Queue<DTNode> queue = new ArrayDeque<>();
            queue.offer(root);

            while (!queue.isEmpty()) {
                DTNode n = queue.poll();

                for (E nonTreeEdge : n.nonTreeEdges) {
                    V opp = edges.get(nonTreeEdge).opposite(n.vertex);
                    DTNode oppNode = vertexToTreeNode.get(opp);
                    DTNode oppRoot = oppNode.findRoot();

                    if (oppRoot != root) {
                        // found a replacement edge
                        removeNonTreeEdge(n, oppNode, nonTreeEdge);
                        insertTreeEdge(root, n, oppRoot, oppNode, nonTreeEdge);
                        edges.get(nonTreeEdge).treeEdge = true;

                        return;
                    }
                }

                queue.addAll(n.children.keySet());
            }
        }

        private void removeNonTreeEdge(DTNode nodeU, DTNode nodeV, E edge) {
            nodeU.nonTreeEdges.remove(edge);
            nodeV.nonTreeEdges.remove(edge);
        }

        private void link(DTNode rootU, DTNode nodeU, DTNode rootV, E edge) {
            nodeU.children.put(rootV, edge);

            rootV.parent = nodeU;
            rootV.parentEdge = edge;

            DTNode newCentroid = null;
            DTNode i = nodeU;

            while (i != null) {
                i.size += rootV.size;

                if (newCentroid == null && i.size > (rootU.size + rootV.size) / 2) {
                    newCentroid = i;
                }

                i = i.parent;
            }

            if (newCentroid != null && newCentroid != rootU) {
                newCentroid.makeRoot();
            }
        }

        private DTNode unlink(DTNode node) {
            Objects.requireNonNull(node.parent);

            DTNode newTree = node;

            while (newTree.parent != null) {
                newTree = newTree.parent;
                newTree.size -= node.size;
            }

            node.parent.children.remove(node);
            node.parent = null;
            node.parentEdge = null;
            return newTree;
        }

        private void addRoot(DTNode node) {
            node.rootIndex = roots.size();
            roots.add(node);
        }

        private void removeRoot(DTNode node) {
            // update roots, swapping 'node' and the last element of roots
            DTNode last = roots.removeLast();
            if (node != last) {
                last.rootIndex = node.rootIndex;
                roots.set(last.rootIndex, last);
            }
        }

        @Override
        public void addVertex(V v) {
            if (containsVertex(v)) {
                return;
            }

            DTNode newNode = new DTNode(v);
            vertexToTreeNode.put(v, newNode);
            addRoot(newNode);
        }

        @Override
        public void removeVertex(V v) {
            if (!containsVertex(v)) {
                return;
            }

            for (E edge : getNeighborEdgesOf(v)) {
                removeEdge(edge);
            }
            DTNode root = vertexToTreeNode.remove(v);
            removeRoot(root);
        }

        @Override
        public boolean containsVertex(V vertex) {
            return vertexToTreeNode.containsKey(vertex);
        }

        @Override
        public boolean containsEdge(E edge) {
            return edges.containsKey(edge);
        }

        @Override
        public V getEdgeSource(E edge) {
            return switch (edges.get(edge)) {
                case null -> null;
                case Edge<V> e -> e.u;
            };
        }

        @Override
        public V getEdgeTarget(E edge) {
            return switch (edges.get(edge)) {
                case null -> null;
                case Edge<V> e -> e.v;
            };
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
            DTNode node = vertexToTreeNode.get(v);

            Set<E> edges = new HashSet<>(node.nonTreeEdges);
            if (node.parentEdge != null) {
                edges.add(node.parentEdge);
            }
            edges.addAll(node.children.values());

            return edges;
        }

        @Override
        public int getNeighborEdgeCountOf(V v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<V> getVertices() {
            return vertexToTreeNode.keySet();
        }

        @Override
        public List<V> getNeighborVerticesOf(V v) {
            throw new UnsupportedOperationException();
        }

        private final class DTNode {

            public DTNode parent = null;
            public E parentEdge = null;
            public Map<DTNode, E> children = new HashMap<>();
            public int size;

            public V vertex;
            public Set<E> nonTreeEdges = new HashSet<>();

            // valid only if this node is a root
            public int rootIndex;

            DTNode(V vertex) {
                this.vertex = vertex;
                this.size = 1;
            }

            public void makeRoot() {
                if (parent == null) {
                    return;
                }

                DTNode child = this;
                DTNode parent = child.parent;
                this.parent = null;
                this.parentEdge = null;

                // swap parent/child relationship, between parent and child
                while (parent != null) {
                    DTNode greatParent = parent.parent;

                    // The followings invariants hold:
                    // 1. parent.children[child] == parentEdge
                    // 2. child.children[parent] == null
                    E parentEdge = parent.children.remove(child);
                    child.children.put(parent, parentEdge);

                    parent.parent = child;
                    parent.parentEdge = parentEdge;

                    // Now, it should be:
                    // 1. child.children[parent] == parentEdge
                    // 2. parent.children[child] == null
                    // 3. parent.parent = child
                    // 4. parent.parentEdge == parentEdge

                    child = parent;
                    parent = greatParent;
                }

                // child is the old root, update rootIndex and roots
                rootIndex = child.rootIndex;
                roots.set(rootIndex, DTNode.this);

                // update size attributes
                while (child.parent != null) {
                    child.size -= child.parent.size;
                    child.parent.size += child.size;
                    child = child.parent;
                }
            }

            public DTNode findRoot() {
                DTNode node = this;

                while (node.parent != null) {
                    node = node.parent;
                }

                return node;
            }

            public Pair<DTNode, Integer> findRootWithDist() {
                DTNode node = this;
                int dist = 0;

                while (node.parent != null) {
                    node = node.parent;
                    dist++;
                }

                return new ImmutablePair<>(node, dist);
            }

            public DTNode findRootOptReroot() {
                DTNode nodeRoot = this;
                DTNode nodeRootChild = null; // the child of nodeRoot in the path from nodeRoot to node

                while (nodeRoot.parent != null) {
                    nodeRootChild = nodeRoot;
                    nodeRoot = nodeRoot.parent;
                }

                if (nodeRootChild != null && nodeRootChild.size > nodeRoot.size / 2) {
                    nodeRootChild.makeRoot();
                    nodeRoot = nodeRootChild;
                }

                return nodeRoot;
            }

            public Set<V> createSetView() {
                return new AbstractSetView<>() {
                    @Override
                    public Iterator<V> iterator() {
                        return new BFSIterator();
                    }

                    @Override
                    public int size() {
                        return size;
                    }
                };
            }

            private class BFSIterator implements Iterator<V> {

                private final Stack<DTNode> stack = new Stack<>();

                BFSIterator() {
                    stack.push(DTNode.this);
                }

                @Override
                public boolean hasNext() {
                    return !stack.isEmpty();
                }

                @Override
                public V next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    DTNode node = stack.pop();
                    stack.addAll(node.children.keySet());
                    return node.vertex;
                }
            }
        }
    }

    private static final class Edge<V> {
        private final V u;
        private final V v;
        private boolean treeEdge;

        private Edge(V u, V v, boolean treeEdge) {
            this.u = u;
            this.v = v;
            this.treeEdge = treeEdge;
        }

        public V opposite(V vertex) {
            if (u == vertex) {
                return v;
            } else {
                return u;
            }
        }

        @Override
        public String toString() {
            return "Edge{" +
                    "u=" + u +
                    ", v=" + v +
                    ", treeEdge=" + treeEdge +
                    '}';
        }
    }
}
