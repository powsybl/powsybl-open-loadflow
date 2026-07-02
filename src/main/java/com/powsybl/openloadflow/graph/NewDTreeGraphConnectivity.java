/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class NewDTreeGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, NewDTreeGraphConnectivity.DTGraph<V, E>> {

    public NewDTreeGraphConnectivity(ToIntFunction<V> vertexToInt, ToIntFunction<E> edgeToInt) {
        super(new DTGraph<>(vertexToInt, edgeToInt));
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
    protected Set<V> getVerticesNotInMainComponent(V mainComponentVertex) {
        // first determine the excluded tree: either the tree containing
        // the mainComponentVertex, either the biggest tree
        DTGraph<V, E> graph = getGraph();
        DTGraph<V, E>.DTNode excludedTree = getMainComponentRoot(mainComponentVertex);

        return new AbstractSetView<>() {
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
                // this can be computed once
                int size = 0;
                for (DTGraph<V, E>.DTNode root : graph.roots) {
                    if (root != excludedTree) {
                        size += root.size;
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

    /*public long computeSd() {
        return getGraph().sumOfDistances();
    }*/

    public static class DTGraph<V, E> implements GraphModel<V, E> {

        @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
        public static boolean DEBUG = false;

        final ToIntFunction<V> vertexToInt;
        final ToIntFunction<E> edgeToInt;

        final TIntObjectMap<DTNode> vertexToTreeNode = new TIntObjectHashMap<>();
        final TIntObjectHashMap<Edge> edges = new TIntObjectHashMap<>();

        final List<DTNode> roots = new ArrayList<>();

        final AllComponentsView components = new AllComponentsView();

        DTGraph(ToIntFunction<V> vertexToInt, ToIntFunction<E> edgeToInt) {
            this.vertexToInt = vertexToInt;
            this.edgeToInt = edgeToInt;
        }

        private DTNode getNode(V vertex) {
            return vertexToTreeNode.get(vertexToInt.applyAsInt(vertex));
        }

        private Edge getEdge(E edge) {
            return edges.get(edgeToInt.applyAsInt(edge));
        }

        private DTNode rootOf(V vertex) {
            return getNode(vertex).findRootOptReroot();
        }

        // ==============
        // * INVARIANTS *
        // ==============

        private void check() {
            if (!DEBUG) {
                return;
            }

            checkEdges();
            checkParentChildRelation();
        }

        private void checkEdges() {
            for (DTNode node : vertexToTreeNode.valueCollection()) {
                for (TIntIterator it = node.nonTreeEdges.iterator(); it.hasNext();) {
                    int nonTreeEdge = it.next();
                    assert edges.containsKey(nonTreeEdge) && !edges.get(nonTreeEdge).treeEdge;
                }
            }

            for (TIntObjectIterator<Edge> it = edges.iterator(); it.hasNext();) {
                int e = it.key();
                Edge edge = it.value();

                DTNode src = Objects.requireNonNull(getNode(edge.u));
                DTNode dest = Objects.requireNonNull(getNode(edge.v));

                if (edge.treeEdge) {
                    assert src.parent == dest && src.parentEdge == e || dest.parent == src && dest.parentEdge == e;
                } else {
                    assert src.nonTreeEdges.contains(e);
                    assert dest.nonTreeEdges.contains(e);
                }
            }
        }

        private void checkParentChildRelation() {
            for (DTNode node : vertexToTreeNode.valueCollection()) {
                DTNode child = node.firstChild;

                while (child != null) {
                    assert child.parent == node;
                    child = child.nextSibling;
                }

                if (node.parent != null) {
                    DTNode parentChild = node.parent.firstChild;
                    boolean present = false;

                    while (parentChild != null && !present) {
                        present = parentChild == node;
                        parentChild = parentChild.nextSibling;
                    }

                    assert present;
                }
            }
        }

        // =============
        // * INSERTION *
        // =============

        @Override
        public void addEdge(V u, V v, E e) {
            int edgeId = edgeToInt.applyAsInt(e);
            if (edges.containsKey(edgeId)) {
                return;
            }

            DTNode nodeU = getNode(u);
            DTNode nodeV = getNode(v);

            Pair<DTNode, Integer> rootUdist = nodeU.findRootWithDist();
            Pair<DTNode, Integer> rootVdist = nodeV.findRootWithDist();

            boolean treeEdge;
            if (rootUdist.getKey() == rootVdist.getKey()) {
                // insert non tree edge
                treeEdge = insertNonTreeEdge(rootUdist.getKey(),
                        nodeU, rootUdist.getValue(),
                        nodeV, rootVdist.getValue(),
                        edgeId);
            } else {
                // insert tree edge
                insertTreeEdge(rootUdist.getKey(), nodeU, rootVdist.getKey(), nodeV, edgeId);
                treeEdge = true;
            }

            edges.put(edgeId, new Edge(e, u, v, treeEdge));

            check();
        }

        private boolean insertNonTreeEdge(DTNode root, DTNode nodeU, int depthU, DTNode nodeV, int depthV, int edge) {
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

        private void insertTreeEdge(DTNode rootU, DTNode nodeU, DTNode rootV, DTNode nodeV, int edge) {
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
            int id = edgeToInt.applyAsInt(e);
            Edge edge = edges.remove(id);
            if (edge == null) {
                return;
            }

            DTNode nodeU = getNode(edge.u);
            DTNode nodeV = getNode(edge.v);

            if (edge.treeEdge) {
                removeTreeEdge(nodeU, nodeV);
            } else {
                removeNonTreeEdge(nodeU, nodeV, id);
            }

            check();
        }

        private void removeTreeEdge(DTNode nodeU, DTNode nodeV) {
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

                if (n.size > rootSmall.size / 2) {
                    newRoot = n;
                }

                for (TIntIterator it = n.nonTreeEdges.iterator(); it.hasNext();) {
                    int nonTreeEdge = it.next();
                    Edge struct = edges.get(nonTreeEdge);

                    V opp = struct.opposite(n.vertex);
                    DTNode oppNode = vertexToTreeNode.get(vertexToInt.applyAsInt(opp));
                    DTNode oppRoot = oppNode.findRoot();

                    if (oppRoot != rootSmall) {
                        // found a replacement edge
                        removeNonTreeEdge(n, oppNode, nonTreeEdge);
                        insertTreeEdge(rootSmall, n, oppRoot, oppNode, nonTreeEdge);
                        struct.treeEdge = true;

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

        private void removeNonTreeEdge(DTNode nodeU, DTNode nodeV, int edge) {
            nodeU.nonTreeEdges.remove(edge);
            nodeV.nonTreeEdges.remove(edge);
        }

        // ======================
        // * TREE MANIPULATIONS *
        // ======================

        private void link(DTNode rootU, DTNode nodeU, DTNode rootV, int edge) {
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
            node.parentEdge = -1;
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
            int index = vertexToInt.applyAsInt(v);

            if (!vertexToTreeNode.containsKey(index)) {
                DTNode newNode = new DTNode(v);
                vertexToTreeNode.put(index, newNode);
                addRoot(newNode);
            }
        }

        @Override
        public void removeVertex(V v) {
            DTNode node = getNode(v);

            if (node != null) {
                for (E edge : node.getNeighborEdges()) {
                    removeEdge(edge);
                }

                // a node with no incident edges is always a root
                removeRoot(node);
            }
        }

        @Override
        public boolean containsVertex(V vertex) {
            return vertexToTreeNode.containsKey(vertexToInt.applyAsInt(vertex));
        }

        @Override
        public boolean containsEdge(E edge) {
            return edges.containsKey(edgeToInt.applyAsInt(edge));
        }

        @Override
        public V getEdgeSource(E edge) {
            return switch (getEdge(edge)) {
                case null -> null;
                case Edge e -> e.u;
            };
        }

        @Override
        public V getEdgeTarget(E edge) {
            return switch (getEdge(edge)) {
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
            return getNode(v).getNeighborEdges();
        }

        @Override
        public int getNeighborEdgeCountOf(V v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<V> getVertices() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<V> getNeighborVerticesOf(V v) {
            throw new UnsupportedOperationException();
        }

        public final class DTNode {

            private DTNode parent = null;
            private int parentEdge = -1;

            // the children of this node. They are stored in a
            // doubly linked list
            private DTNode previousSibling = null;
            private DTNode nextSibling = null;
            private DTNode firstChild = null;

            // the size of this subtree
            private int size;

            private final V vertex;
            private final TIntHashSet childTreeEdges = new TIntHashSet();
            private final TIntHashSet nonTreeEdges = new TIntHashSet();

            // valid only if this node is a root
            public int rootIndex;

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
                int parentEdge = child.parentEdge;
                parent.removeChildUnchecked(child); // remove before making parentEdge null

                this.parent = null;
                this.parentEdge = -1;

                // swap parent/child relation
                while (parent != null) {
                    DTNode greatParent = parent.parent;
                    int greatParentEdge = parent.parentEdge;

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
            private void addChildUnchecked(DTNode child, int edge) {
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
                return new DFSIterator(node); // TODO: maybe cache iterator?
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

        private class DFSIterator implements Iterator<V> {

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
                if (node.parentEdge >= 0) {
                    size++;
                }
                return size;
            }
        }

        private final class NeighborEdgesIterator implements Iterator<E> {

            private E next;
            private TIntIterator current;
            private TIntIterator nextIt;

            NeighborEdgesIterator(DTNode node) {
                if (node.parentEdge >= 0) {
                    next = edges.get(node.parentEdge).edge;
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
                        next = edges.get(current.next()).edge;
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
            private final E edge;
            private final V u;
            private final V v;
            private boolean treeEdge;

            private Edge(E edge, V u, V v, boolean treeEdge) {
                this.edge = edge;
                this.u = u;
                this.v = v;
                this.treeEdge = treeEdge;
            }

            public V opposite(V vertex) {
                if (vertexToInt.applyAsInt(u) == vertexToInt.applyAsInt(vertex)) {
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
