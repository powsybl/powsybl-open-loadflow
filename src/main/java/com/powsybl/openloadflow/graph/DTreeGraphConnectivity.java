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
        for (Iterator<GraphModification<V, E>> it = m.descendingIterator(); it.hasNext();) {
            GraphModification<V, E> modification = it.next();
            modification.undo(getGraph());
        }
    }

    @Override
    protected void updateComponents() {

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
        return getGraph().vertexToTreeNode.get(vertex).createSetView();
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        checkSavedContext();
        checkVertex(vertex);
        return getGraph().spanningForest.getNonConnectedVertices(vertex);
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    public static final class Graph<V, E> implements GraphModel<V, E> {

        private final Map<V, DTNode<V, E>> vertexToTreeNode = new HashMap<>();
        private final Map<E, Edge<V>> edges = new HashMap<>();

        private final List<DTNode<V, E>> roots = new ArrayList<>();

        @Override
        public void addEdge(V u, V v, E e) {
            DTNode<V, E> nodeU = vertexToTreeNode.get(u);
            DTNode<V, E> nodeV = vertexToTreeNode.get(v);

            Pair<DTNode<V, E>, Integer> rootUdist = nodeU.findRootWithDist();
            Pair<DTNode<V, E>, Integer> rootVdist = nodeV.findRootWithDist();

            if (rootUdist.getKey() == rootVdist.getKey()) {
                // insert non tree edge
                insertNonTreeEdge(rootUdist.getKey(), nodeU, rootUdist.getValue(), nodeV, rootVdist.getValue(), e);
            } else {
                // insert tree edge
                insertTreeEdge(rootUdist.getKey(), nodeU, rootVdist.getKey(), nodeV, e);
            }

            edges.put(e, new Edge<>(u, v));
        }

        private void insertNonTreeEdge(DTNode<V, E> root, DTNode<V, E> nodeU, int depthU, DTNode<V, E> nodeV, int depthV, E edge) {
            DTNode<V, E> deep;
            DTNode<V, E> shallow;
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
                DTNode<V, E> i = deep;
                for (int j = 0; j < delta - 2; j++) {
                    i = i.parent;
                }

                i.parent.nonTreeEdges.add(i.parentEdge);
                i.nonTreeEdges.add(i.parentEdge);

                unlink(i);
                link(root, shallow, deep, edge);
            }
        }

        private void insertTreeEdge(DTNode<V, E> rootU, DTNode<V, E> nodeU, DTNode<V, E> rootV, DTNode<V, E> nodeV, E edge) {
            if (rootU.size < rootV.size) {
                nodeU.makeRoot();
                link(rootV, nodeV, nodeU, edge);
            } else {
                nodeV.makeRoot();
                link(rootU, nodeU, nodeV, edge);
            }
        }

        @Override
        public void removeEdge(E e) {
            Edge<V> edge = edges.remove(e);
            if (edge == null) {
                return;
            }

            DTNode<V, E> nodeU = vertexToTreeNode.get(edge.u);
            DTNode<V, E> nodeV = vertexToTreeNode.get(edge.v);

            if (nodeU.parent == nodeV || nodeV.parent == nodeU) {
                // tree edge
                removeTreeEdge(nodeU, nodeV, e);
            } else {
                // non tree edge
                removeNonTreeEdge(nodeU, nodeV, e);
            }
        }

        private void removeTreeEdge(DTNode<V, E> nodeU, DTNode<V, E> nodeV, E edge) {
            DTNode<V, E> child;

            if (nodeU == nodeV.parent) {
                child = nodeV;
            } else {
                child = nodeU;
            }

            DTNode<V, E> otherTree = unlink(child);

            DTNode<V, E> small;
            DTNode<V, E> large;

            if (child.size < otherTree.size) {
                small = child;
                large = otherTree;
            } else {
                small = otherTree;
                large = child;
            }

            replace(small);
        }

        private void replace(DTNode<V, E> root) {
            Queue<DTNode<V, E>> queue = new ArrayDeque<>();
            queue.offer(root);

            while (!queue.isEmpty()) {
                DTNode<V, E> n = queue.poll();

                for (E nonTreeEdge : n.nonTreeEdges) {
                    V opp = edges.get(nonTreeEdge).opposite(n.vertex);
                    DTNode<V, E> oppNode = vertexToTreeNode.get(opp);
                    DTNode<V, E> oppRoot = oppNode.findRoot();

                    if (oppRoot != root) {
                        // found a replacement edge
                        removeNonTreeEdge(n, oppNode, nonTreeEdge);
                        insertTreeEdge(root, n, oppRoot, oppNode, nonTreeEdge);

                        return;
                    }
                }

                queue.addAll(n.children.keySet());
            }
        }

        private void removeNonTreeEdge(DTNode<V, E> nodeU, DTNode<V, E> nodeV, E edge) {
            nodeU.nonTreeEdges.remove(edge);
            nodeV.nonTreeEdges.remove(edge);
        }

        private void link(DTNode<V, E> rootU, DTNode<V, E> nodeU, DTNode<V, E> rootV, E edge) {
            nodeU.children.put(rootV, edge);

            rootV.parent = nodeU;
            rootV.parentEdge = edge;

            DTNode<V, E> newCentroid = null;
            DTNode<V, E> i = nodeU;

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

        private DTNode<V, E> unlink(DTNode<V, E> node) {
            Objects.requireNonNull(node.parent);

            DTNode<V, E> newTree = node;

            while (newTree.parent != null) {
                newTree = newTree.parent;
                newTree.size -= node.size;
            }

            node.parent.children.remove(node);
            node.parent = null;
            return newTree;
        }

        @Override
        public void addVertex(V v) {
            if (containsVertex(v)) {
                return;
            }

            vertexToTreeNode.put(v, new DTNode<>(v));
        }

        @Override
        public void removeVertex(V v) {
            for (E edge : getNeighborEdgesOf(v)) {
                removeEdge(edge);
            }
            vertexToTreeNode.remove(v);
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
            DTNode<V, E> node = vertexToTreeNode.get(v);

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
    }

    public static final class DTNode<V, E> {

        public DTNode<V, E> parent = null;
        public E parentEdge = null;
        public Map<DTNode<V, E>, E> children = new HashMap<>();
        public int size;

        public V vertex;
        public Set<E> nonTreeEdges = new HashSet<>();

        // valid only if this node is a root
        public int rootIndex;

        public DTNode(V vertex) {
            this.vertex = vertex;
            this.size = 1;
        }

        public void makeRoot() {
            DTNode<V, E> child = this;
            DTNode<V, E> current = child.parent;
            parent = null;

            // swap parent/child relationship
            while (current != null) {
                // greatParent --> current --> child
                DTNode<V, E> greatParent = current.parent;
                current.parent = child;
                current.children.remove(child);
                child.children.add(current);
                // greatParent --> current <-- child

                child = current;
                current = greatParent;
            }

            // update size attributes
            while (child.parent != null) {
                child.size -= child.parent.size;
                child.parent.size += child.size;
                child = child.parent;
            }
        }

        public DTNode<V, E> findRoot() {
            DTNode<V, E> node = this;

            while (node.parent != null) {
                node = node.parent;
            }

            return node;
        }

        public Pair<DTNode<V, E>, Integer> findRootWithDist() {
            DTNode<V, E> node = this;
            int dist = 0;

            while (node.parent != null) {
                node = node.parent;
                dist++;
            }

            return new ImmutablePair<>(node, dist);
        }

        public DTNode<V, E> findRootOptReroot() {
            DTNode<V, E> nodeRoot = this;
            DTNode<V, E> nodeRootChild = null; // the child of nodeRoot in the path from nodeRoot to node

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
            return new AbstractSetView<V>() {
                @Override
                public Iterator<V> iterator() {
                    return new BFSIterator<>(DTNode.this);
                }

                @Override
                public int size() {
                    return size;
                }
            };
        }

        private static class BFSIterator<V> implements Iterator<V> {

            private final Stack<DTNode<V, ?>> stack = new Stack<>();

            BFSIterator(DTNode<V, ?> node) {
                stack.push(node);
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

                DTNode<V, ?> node = stack.pop();
                stack.addAll(node.children.keySet());
                return node.vertex;
            }
        }
    }
    
    private record Edge<V>(V u, V v) {
        public V opposite(V vertex) {
            if (u == vertex) {
                return v;
            } else {
                return u;
            }
        }
    }
}
