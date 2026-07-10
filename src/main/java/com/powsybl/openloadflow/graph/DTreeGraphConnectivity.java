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
public class DTreeGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, DTreeGraphConnectivity.DTGraph<V, E>> {

    public DTreeGraphConnectivity() {
        super(new DTGraph<>());
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

        DTGraph<V, E> graph = getGraph();
        List<DTGraph<V, E>.DTNode> roots = graph.roots;

        // sorting roots will sort components as components is a wrapper around roots
        roots.sort((s1, s2) -> s2.size - s1.size);
        for (int i = 0; i < graph.roots.size(); i++) {
            roots.get(i).rootIndex = i;
        }

        componentSets = graph.components;
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
        return getGraph().rootOf(vertex).componentView();
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        checkSavedContext();
        checkVertex(vertex);

        DTGraph<V, E> graph = getGraph();
        DTGraph<V, E>.DTNode excludedTree = graph.rootOf(vertex);

        Set<V> components = new HashSet<>();
        for (DTGraph<V, E>.DTNode root : graph.roots) {
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
        DTGraph<V, E> graph = getGraph();
        DTGraph<V, E>.DTNode excludedTree = getMainComponentRoot(mainComponentVertex);

        return new AbstractSetView<>() {
            int size = -1;

            @Override
            public Iterator<V> iterator() {
                return new VerticesNotInMainComponentIterator(excludedTree);
            }

            @Override
            public boolean contains(Object o) {
                if (o != null) {
                    return graph.rootOf((V) o) != excludedTree;
                }

                return false;
            }

            @Override
            public int size() {
                if (size < 0) {
                    size = 0;
                    for (DTGraph<V, E>.DTNode root : graph.roots) {
                        if (root != excludedTree) {
                            size += root.size;
                        }
                    }
                }

                return size;
            }
        };
    }

    private class VerticesNotInMainComponentIterator implements Iterator<V> {

        private final DTGraph<V, E>.DTNode excludedTree;
        private int index = 0;
        private Iterator<V> curIt;

        VerticesNotInMainComponentIterator(DTGraph<V, E>.DTNode excludedTree) {
            this.excludedTree = excludedTree;
        }

        @Override
        public boolean hasNext() {
            if (curIt != null && curIt.hasNext()) {
                return true;
            }

            DTGraph<V, E> graph = getGraph();
            while (index < graph.roots.size()) {
                DTGraph<V, E>.DTNode next = graph.roots.get(index);
                index++;

                if (next != excludedTree) {
                    curIt = getGraph().iterator(next);
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
    private DTGraph<V, E>.DTNode getMainComponentRoot(V mainComponentVertex) {
        DTGraph<V, E> graph = getGraph();

        if (mainComponentVertex != null) {
            return graph.rootOf(mainComponentVertex);
        } else {
            DTGraph<V, E>.DTNode biggestRoot = graph.roots.getFirst();

            for (int i = 1; i < graph.roots.size(); i++) {
                DTGraph<V, E>.DTNode root = graph.roots.get(i);
                if (root.size > biggestRoot.size) {
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

    public long computeSd() {
        return getGraph().sumOfDistances();
    }

    public static final class DTGraph<V, E> implements GraphModel<V, E> {

        private final Map<V, DTNode> vertexToTreeNode = new HashMap<>();
        private final Map<E, Edge> edges = new HashMap<>();

        private final List<DTNode> roots = new ArrayList<>();

        private final AllComponentsView components = new AllComponentsView();

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

        // =============
        // * INSERTION *
        // =============

        @Override
        public void addEdge(V u, V v, E e) {
            if (containsEdge(e)) {
                return;
            }

            DTNode nodeU = getNodeThrowIfInexistent(u);
            DTNode nodeV = getNodeThrowIfInexistent(v);

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

            edges.put(e, new Edge(u, v, treeEdge));
        }

        private DTNode getNodeThrowIfInexistent(V v) {
            DTNode node = vertexToTreeNode.get(v);
            if (node == null) {
                throw new IllegalArgumentException("no such vertex in graph: " + v);
            }

            return node;
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

        // ===========
        // * REMOVAL *
        // ===========

        @Override
        public void removeEdge(E e) {
            Edge edge = edges.remove(e);
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
            if (child.size < otherTree.size) {
                small = child;
            } else {
                small = otherTree;
            }

            replace(small);
        }

        private void replace(DTNode rootSmall) {
            DTNode newRoot = null; // a potential new root in case no replacement edge is found

            ArrayDeque<DTNode> queue = new ArrayDeque<>();
            queue.offer(rootSmall);

            while (!queue.isEmpty()) {
                DTNode n = queue.poll();

                if (n != rootSmall && n.size > rootSmall.size / 2) {
                    newRoot = n;
                }

                for (E nonTreeEdge : n.nonTreeEdges) {
                    Edge edge = edges.get(nonTreeEdge);

                    V opp = edge.opposite(n.vertex);
                    DTNode oppNode = vertexToTreeNode.get(opp);
                    DTNode oppRoot = oppNode.findRoot();

                    if (oppRoot != rootSmall) {
                        // found a replacement edge
                        removeNonTreeEdge(n, oppNode, nonTreeEdge);
                        insertTreeEdge(rootSmall, n, oppRoot, oppNode, nonTreeEdge);
                        edge.treeEdge = true;

                        return;
                    }
                }

                DTNode child = n.firstChild;
                while (child != null) {
                    queue.add(child);
                    child = child.nextSibling;
                }
            }

            if (newRoot != null) {
                newRoot.makeRoot(true);
            }
        }

        private void removeNonTreeEdge(DTNode nodeU, DTNode nodeV, E edge) {
            nodeU.nonTreeEdges.remove(edge);
            nodeV.nonTreeEdges.remove(edge);
        }

        // ======================
        // * TREE MANIPULATIONS *
        // ======================

        private void link(DTNode rootU, DTNode nodeU, DTNode rootV, E edge) {
            // first: update parent/child relations
            nodeU.addChildUnchecked(rootV, edge);

            rootV.parent = nodeU;
            rootV.parentEdge = edge;

            // next: update size attributes in the parent tree
            DTNode newCentroid = null;
            DTNode cur = nodeU;

            while (cur != null) {
                cur.size += rootV.size;

                if (newCentroid == null && cur != rootU && cur.size > (rootU.size + rootV.size) / 2) {
                    // the new root is the first node in the path from nodeU to rootU
                    // such that it contains more than half of the nodes in the merged
                    // tree. This reduces the sum of distances.
                    newCentroid = cur;
                }

                cur = cur.parent;
            }

            // eventually, change the root to a better one
            if (newCentroid != null) {
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

        // =========
        // * OTHER *
        // =========

        public Iterator<V> iterator(DTNode root) {
            return new DFSIterator(root);
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
                case Edge e -> e.u;
            };
        }

        @Override
        public V getEdgeTarget(E edge) {
            return switch (edges.get(edge)) {
                case null -> null;
                case Edge e -> e.v;
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
            return vertexToTreeNode.get(v).getNeighborEdges();
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

            private DTNode parent = null;
            private E parentEdge = null;

            // the children of this node. They are stored in a
            // doubly linked list
            private DTNode previousSibling = null;
            private DTNode nextSibling = null;
            private DTNode firstChild = null;

            // the size of this subtree
            private int size;

            private final V vertex;
            private final Set<E> childTreeEdges = new LinkedHashSet<>();
            private final Set<E> nonTreeEdges = new LinkedHashSet<>();

            // valid only if this node is a root
            private int rootIndex;

            private ComponentView componentView = null;
            private NeighborEdges neighborEdges = null;

            DTNode(V vertex) {
                this.vertex = vertex;
                this.size = 1;
            }

            private void makeRoot(boolean updateRoots) {
                if (parent == null) {
                    return;
                }

                DTNode child = this;
                DTNode parent = child.parent;
                E parentEdge = child.parentEdge;
                parent.removeChildUnchecked(child); // remove before making parentEdge null

                this.parent = null;
                this.parentEdge = null;

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
            private void addChildUnchecked(DTNode child, E edge) {
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
            private void removeChildUnchecked(DTNode child) {
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

            private DTNode findRoot() {
                DTNode node = this;

                while (node.parent != null) {
                    node = node.parent;
                }

                return node;
            }

            private Pair<DTNode, Integer> findRootWithDist() {
                DTNode node = this;
                int dist = 0;

                while (node.parent != null) {
                    node = node.parent;
                    dist++;
                }

                return new ImmutablePair<>(node, dist);
            }

            private DTNode findRootOptReroot() {
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
            public Set<V> componentView() {
                if (componentView == null) {
                    componentView = new ComponentView(this);
                }

                return componentView;
            }

            public Set<E> getNeighborEdges() {
                if (neighborEdges == null) {
                    neighborEdges = new NeighborEdges(this);
                }
                return neighborEdges;
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
                    Edge e = edges.get(nte);

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

        private final class AllComponentsView extends AbstractList<Set<V>> {

            @Override
            public Set<V> get(int index) {
                return roots.get(index).componentView();
            }

            @Override
            public int size() {
                return roots.size();
            }
        }

        private final class ComponentView extends AbstractSetView<V> {

            private final DTNode node;

            ComponentView(DTNode node) {
                this.node = node;
            }

            @Override
            public Iterator<V> iterator() {
                return new DFSIterator(node);
            }

            @Override
            public boolean contains(Object o) {
                if (o != null) {
                    return rootOf((V) o) == node;
                }

                return false;
            }

            @Override
            public int size() {
                return node.size;
            }
        }

        private final class DFSIterator implements Iterator<V> {

            private DTNode cursor;

            DFSIterator(DTNode root) {
                cursor = root;
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

        private final class NeighborEdges extends AbstractSetView<E> {

            private final DTNode node;

            NeighborEdges(DTNode node) {
                this.node = node;
            }

            @Override
            public Iterator<E> iterator() {
                return new NeighborEdgesIterator(node);
            }

            @Override
            public int size() {
                int size = node.nonTreeEdges.size() + node.childTreeEdges.size();
                if (node.parent != null) {
                    size++;
                }
                return size;
            }
        }

        private final class NeighborEdgesIterator implements Iterator<E> {

            private E next;
            private Iterator<E> current;
            private Iterator<E> nextIt;

            NeighborEdgesIterator(DTNode node) {
                if (node.parent != null) {
                    next = node.parentEdge;
                }

                if (!node.childTreeEdges.isEmpty()) {
                    current = node.childTreeEdges.iterator();

                    if (!node.nonTreeEdges.isEmpty()) {
                        nextIt = node.nonTreeEdges.iterator();
                    }

                } else if (!node.nonTreeEdges.isEmpty()) {
                    current = node.nonTreeEdges.iterator();
                }
            }

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }

                while (current != null) {
                    if (current.hasNext()) {
                        next = current.next();
                        return true;
                    }
                    current = nextIt;
                    nextIt = null; // this loop can loop at most two times
                }

                return false;
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                E ret = next;
                next = null;
                return ret;
            }
        }

        private final class Edge {
            private final V u;
            private final V v;
            private boolean treeEdge;

            private Edge(V u, V v, boolean treeEdge) {
                this.u = u;
                this.v = v;
                this.treeEdge = treeEdge;
            }

            public V opposite(V vertex) {
                if (u.equals(vertex)) {
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
}
