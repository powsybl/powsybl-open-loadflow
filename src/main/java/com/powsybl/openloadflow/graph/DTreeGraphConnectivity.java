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
        return getGraph().rootOf(vertex).rootIndex;
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
        return getGraph().rootOf(vertex).createSetView();
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        checkSavedContext();
        checkVertex(vertex);

        Graph<V, E> graph = getGraph();
        Graph<V, E>.DTNode excludedTree = graph.rootOf(vertex);

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

    public long computeSd() {
        return getGraph().sumOfDistances();
    }

    public static final class Graph<V, E> implements GraphModel<V, E> {

        private final Map<V, DTNode> vertexToTreeNode = new HashMap<>();
        private final Map<E, Edge<V>> edges = new HashMap<>();

        private final List<DTNode> roots = new ArrayList<>();

        public long sumOfDistances() {
            long sum = 0;

            for (DTNode node : vertexToTreeNode.values()) {
                sum += node.findRootWithDist().getValue();
            }

            return sum;
        }

        DTNode rootOf(V vertex) {
            return vertexToTreeNode.get(vertex).findRootOptReroot();
        }

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
                treeEdge = insertNonTreeEdge(rootUdist.getKey(), nodeU, rootUdist.getValue(), nodeV, rootVdist.getValue(), e);
            } else {
                // insert tree edge
                insertTreeEdge(rootUdist.getKey(), nodeU, rootVdist.getKey(), nodeV, e);
                treeEdge = true;
            }

            edges.put(e, new Edge<>(u, v, treeEdge));

            // checkEdges();
        }

        private boolean insertNonTreeEdge(DTNode root, DTNode nodeU, int depthU, DTNode nodeV, int depthV, E edge) {
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
                return false;
            } else {
                DTNode i = deep;
                for (int j = 0; j < delta - 2; j++) {
                    i = i.parent;
                }

                i.parent.nonTreeEdges.add(i.parentEdge);
                i.nonTreeEdges.add(i.parentEdge);
                edges.get(i.parentEdge).treeEdge = false;

                unlink(i);
                // updating roots is useless because 'deep' will be
                // connected to 'shallow' juste after.
                deep.makeRoot(false);
                link(root, shallow, deep, edge);
                return true;
            }
        }

        private void insertTreeEdge(DTNode rootU, DTNode nodeU, DTNode rootV, DTNode nodeV, E edge) {
            DTNode toRemove;
            if (rootU.size < rootV.size) {
                nodeU.makeRoot(true);
                link(rootV, nodeV, nodeU, edge);
                toRemove = nodeU;
            } else {
                nodeV.makeRoot(true);
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

            // checkEdges();
        }

        private void checkEdges() {
            for (DTNode node : vertexToTreeNode.values()) {
                for (E nonTreeEdge : node.nonTreeEdges) {
                    assert edges.containsKey(nonTreeEdge) && !edges.get(nonTreeEdge).treeEdge;
                }
            }

            for (Map.Entry<E, Edge<V>> entry : edges.entrySet()) {
                E e = entry.getKey();
                Edge<V> edge = entry.getValue();

                DTNode src = Objects.requireNonNull(vertexToTreeNode.get(edge.u));
                DTNode dest = Objects.requireNonNull(vertexToTreeNode.get(edge.v));

                if (edge.treeEdge) {
                    assert src.parent == dest && src.parentEdge == e || dest.parent == src && dest.parentEdge == e;
                } else {
                    assert src.nonTreeEdges.contains(e);
                    assert dest.nonTreeEdges.contains(e);
                }
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
            ArrayDeque<DTNode> queue = new ArrayDeque<>();
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

                DTNode child = n.firstChild;
                while (child != null) {
                    queue.add(child);
                    child = child.nextSibling;
                }
            }
        }

        private void removeNonTreeEdge(DTNode nodeU, DTNode nodeV, E edge) {
            nodeU.nonTreeEdges.remove(edge);
            nodeV.nonTreeEdges.remove(edge);
        }

        private void link(DTNode rootU, DTNode nodeU, DTNode rootV, E edge) {
            // first step: update parent/child relations
            nodeU.addChildUnchecked(rootV, edge);

            rootV.parent = nodeU;
            rootV.parentEdge = edge;

            // next: update size attributes in the parent tree
            DTNode newCentroid = null;
            DTNode i = nodeU;

            while (i != null) {
                i.size += rootV.size;

                if (newCentroid == null && i.size > (rootU.size + rootV.size) / 2) {
                    newCentroid = i;
                }

                i = i.parent;
            }

            // eventually, change the root to a better one
            if (newCentroid != null && newCentroid != rootU) {
                newCentroid.makeRoot(true);
            }
        }

        private DTNode unlink(DTNode node) {
            Objects.requireNonNull(node.parent);

            // first step: update size attribute in the parent tree
            DTNode newTree = node;
            while (newTree.parent != null) {
                newTree = newTree.parent;
                newTree.size -= node.size;
            }

            // second step: update parent/child relations
            node.parent.removeChildUnchecked(node);
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
        public Set<E> getNeighborEdgesOf(V v) { // TODO: optimize
            DTNode node = vertexToTreeNode.get(v);

            Set<E> edges = new HashSet<>(node.nonTreeEdges);
            if (node.parentEdge != null) {
                edges.add(node.parentEdge);
            }
            edges.addAll(node.childTreeEdges);

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

            // the children of this node. They are stored in a
            // doubly linked list
            public DTNode previousSibling = null;
            public DTNode nextSibling = null;
            public DTNode firstChild = null;

            // the size of this subtree
            public int size;

            public V vertex;
            public Set<E> childTreeEdges = new LinkedHashSet<>();
            public Set<E> nonTreeEdges = new LinkedHashSet<>();

            // valid only if this node is a root
            public int rootIndex;

            DTNode(V vertex) {
                this.vertex = vertex;
                this.size = 1;
            }

            public void makeRoot(boolean updateRoots) {
                if (parent == null) {
                    return;
                }

                DTNode child = this;
                DTNode parent = child.parent;
                E parentEdge = child.parentEdge;
                this.parent = null;
                this.parentEdge = null;

                parent.removeChildUnchecked(child);

                // swap parent/child relation
                while (parent != null) {
                    DTNode greatParent = parent.parent;
                    E greatParentEdge = parent.parentEdge;

                    // At this point:
                    // - the parent of 'parent' aka greatParent should be changed to child
                    // - parent is in the linked list of child of greatParent
                    // - child is NOT in the linked list of child of parent
                    if (greatParent != null) {
                        greatParent.removeChildUnchecked(parent);
                    }

                    child.addChildUnchecked(parent, parentEdge);

                    parent.parent = child;
                    parent.parentEdge = parentEdge;

                    // At this point:
                    // - parent isn't anymore is the linked list of child of greatParent
                    // - parent is a child of 'child'

                    child = parent;
                    parent = greatParent;
                    parentEdge = greatParentEdge;
                }

                if (updateRoots) {
                    // child is the old root, update rootIndex and roots
                    rootIndex = child.rootIndex;
                    roots.set(rootIndex, DTNode.this);
                }

                // update size attributes
                while (child.parent != null) {
                    child.size -= child.parent.size;
                    child.parent.size += child.size;
                    child = child.parent;
                }
            }

            // Add child in the doubly linked list of children
            // no verification is performed
            public void addChildUnchecked(DTNode child, E edge) {
                DTNode oldFirstChild = this.firstChild;

                firstChild = child;
                child.nextSibling = oldFirstChild;

                if (oldFirstChild != null) {
                    oldFirstChild.previousSibling = child;
                }

                childTreeEdges.add(edge);
            }

            // Remove child from the doubly linked list of children
            // no verification is performed
            public void removeChildUnchecked(DTNode child) {
                DTNode prev = child.previousSibling;
                DTNode next = child.nextSibling;

                child.previousSibling = null;
                child.nextSibling = null;

                if (prev != null) {
                    prev.nextSibling = next;
                } else {
                    // if there is no 'prev' node, that means
                    // that 'child' was the first child, and
                    // we need to update it
                    firstChild = next;
                }
                if (next != null) {
                    next.previousSibling = prev;
                }

                childTreeEdges.remove(child.parentEdge);
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
                    nodeRootChild.makeRoot(true);
                    nodeRoot = nodeRootChild;
                }

                return nodeRoot;
            }

            // This DNode MUST be a root
            public Set<V> createSetView() {
                return new AbstractSetView<>() {
                    @Override
                    public Iterator<V> iterator() {
                        return new DFSIterator();
                    }

                    @Override
                    public boolean contains(Object o) {
                        if (o != null) {
                            return rootOf((V) o) == DTNode.this;
                        }

                        return false;
                    }

                    @Override
                    public int size() {
                        return size;
                    }
                };
            }

            private class DFSIterator implements Iterator<V> {

                private DTNode cursor;

                DFSIterator() {
                    cursor = DTNode.this;
                }

                @Override
                public boolean hasNext() {
                    return cursor != null;
                }

                @Override
                public V next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    DTNode next = cursor;

                    // update cursor
                    if (cursor.firstChild != null) {
                        cursor = cursor.firstChild;
                    } else if (cursor.nextSibling != null) {
                        cursor = cursor.nextSibling;
                    } else {
                        while (cursor != null && cursor.nextSibling == null) {
                            cursor = cursor.parent;
                        }

                        if (cursor != null) {
                            cursor = cursor.nextSibling;
                        }
                    }

                    return next.vertex;
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(vertex.toString()).append(" -te-> {");

                Set<V> set = new HashSet<>();

                DTNode child = firstChild;
                while (child != null) {
                    if (!set.add(child.vertex)) {
                        sb.append("loop detected");
                        break;
                    } else {
                        sb.append(child.vertex).append(", ");
                    }
                    child = child.nextSibling;
                }
                sb.append("} -nte-> ");

                for (E nte : nonTreeEdges) {
                    Edge<V> e = edges.get(nte);

                    if (e.u.equals(vertex)) {
                        sb.append(e.v).append(", ");
                    } else if (e.v.equals(vertex)) {
                        sb.append(e.u).append(", ");
                    } else {
                        sb.append("nte error, ");
                    }
                }

                return sb.toString();
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
