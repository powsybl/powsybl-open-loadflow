/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeStandalone<V, E> implements GraphConnectivity<V, E> {

    public static boolean debug = false;

    private final Map<V, DTNode> vertexToTreeNode = new HashMap<>();
    private final Map<E, Edge> edges = new HashMap<>();

    private final List<DTNode> roots = new ArrayList<>();
    private boolean isSorted = true;

    private final Deque<Modifications> modificationsStack = new ArrayDeque<>();
    private V defaultMainComponentVertex;

    public boolean containsVertex(V vertex) {
        return vertexToTreeNode.containsKey(vertex);
    }

    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
    }

    public V getEdgeSource(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge e -> e.u;
        };
    }

    public V getEdgeTarget(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge e -> e.v;
        };
    }

    private DTNode getNodeOrThrow(V v) {
        DTNode node = vertexToTreeNode.get(v);
        if (node == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return node;
    }

    private DTNode rootOf(V vertex) {
        return vertexToTreeNode.get(vertex).findRoot();
    }

    private void sortComponents() {
        if (!isSorted) {
            roots.sort((s1, s2) -> s2.size - s1.size);
            for (int i = 0; i < roots.size(); i++) {
                roots.get(i).rootIndex = i;
            }
            isSorted = true;
        }
    }

    public long computeSd() {
        long sum = 0;

        for (DTNode node : vertexToTreeNode.values()) {
            sum += node.findRootWithDepth().getValue();
        }

        return sum;
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        if (containsVertex(vertex)) {
            return;
        }

        DTNode newNode = new DTNode(vertex);
        vertexToTreeNode.put(vertex, newNode);
        addRoot(newNode);

        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new VertexAdd<>(vertex));
        }
    }

    public void removeVertex(V v) {
        if (!containsVertex(v)) {
            return;
        }

        DTNode node = vertexToTreeNode.get(v); // can't use Map#remove because removeEdge will update this DTNode
        for (E edge : node.nonTreeEdges) {
            removeEdge(edge);
        }
        for (E edge : node.childTreeEdges) {
            removeEdge(edge);
        }
        if (node.parentEdge != null) {
            removeEdge(node.parentEdge);
        }

        DTNode root = vertexToTreeNode.remove(v);
        removeRoot(root);

        // no VertexRemove modification, so don't update stack
    }

    // =============
    // * INSERTION *
    // =============

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        Objects.requireNonNull(edge);

        if (containsEdge(edge)) {
            return;
        }

        // first update the spanning tree
        DTNode nodeU = getNodeOrThrow(vertex1);
        DTNode nodeV = getNodeOrThrow(vertex2);

        Pair<DTNode, Integer> rootUdepth = nodeU.findRootWithDepth();
        Pair<DTNode, Integer> rootVdepth = nodeV.findRootWithDepth();

        DTNode rootU = rootUdepth.getKey();
        DTNode rootV = rootVdepth.getKey();

        boolean treeEdge;
        if (rootU == rootV) {
            // insert non tree edge
            treeEdge = insertNonTreeEdge(rootU, nodeU, rootUdepth.getValue(), nodeV, rootVdepth.getValue(), edge);
        } else {
            // insert tree edge
            insertTreeEdge(rootU, nodeU, rootV, nodeV, edge);
            treeEdge = true;
        }

        edges.put(edge, new Edge(vertex1, vertex2, treeEdge));

        // keep track of modifications
        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new EdgeAdd<>(vertex1, vertex2, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        check();
    }

    private boolean insertNonTreeEdge(DTNode root, DTNode nodeU, int depthU, DTNode nodeV, int depthV, E edge) {
        if (isInMainComponent(root)) {
            checkSavedContext().markEdgeAdded(edge);
        }

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
        if (isInMainComponent(rootV)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootU);
        } else if (isInMainComponent(rootU)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootV);
        }

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
    public void removeEdge(E edge) {
        Objects.requireNonNull(edge);
        Edge e = edges.remove(edge);
        if (e == null) {
            return;
        }

        // update the spanning tree
        DTNode nodeU = vertexToTreeNode.get(e.u);
        DTNode nodeV = vertexToTreeNode.get(e.v);

        if (e.treeEdge) {
            removeTreeEdge(nodeU, nodeV, edge);
        } else {
            removeNonTreeEdge(nodeU, nodeV, edge);
        }

        // keep track of modifications
        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new EdgeRemove<>(e.u, e.v, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        check();
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

        replace(small, large, edge);
    }

    // search a replacement edge by doing a BFS over the smaller tree between rootSmall and rooLarge
    private void replace(DTNode rootSmall, DTNode rootLarge, E removedEdge) {
        boolean replacementEdgeFound = false;
        DTNode newRoot = null; // a potential new root in case no replacement edge is found

        ArrayDeque<DTNode> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        loop:
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
                    insertTreeEdge(rootSmall, n, rootLarge, oppNode, nonTreeEdge);
                    edges.get(nonTreeEdge).treeEdge = true;
                    replacementEdgeFound = true;

                    break loop;
                }
            }

            DTNode child = n.firstChild;
            while (child != null) {
                queue.add(child);
                child = child.nextSibling;
            }
        }

        if (isInMainComponent(rootLarge) || isInMainComponent(rootSmall)) {
            checkSavedContext().markEdgeRemoved(removedEdge);
        }

        if (!replacementEdgeFound) {
            if (isInMainComponent(rootLarge)) {
                markAllRemoved(rootSmall);
            } else if (isInMainComponent(rootSmall)) {
                markAllRemoved(rootLarge);
            }

            if (newRoot != null) {
                newRoot.makeRoot(true);
            }
        }
    }

    private void removeNonTreeEdge(DTNode nodeU, DTNode nodeV, E edge) {
        if (isInMainComponent(nodeU)) {
            checkSavedContext().markEdgeRemoved(edge);
        }

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

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    @Override
    public void startTemporaryChanges(boolean quick) {
        modificationsStack.push(new Modifications(defaultMainComponentVertex, !quick));
    }

    @Override
    public void undoTemporaryChanges() {
        if (modificationsStack.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }

        Modifications modifications = modificationsStack.peek();
        modifications.undoing = true;

        for (var it = modifications.modifications.descendingIterator(); it.hasNext();) {
            switch (it.next()) {
                case EdgeAdd<V, E> edgeAdd -> removeEdge(edgeAdd.e);
                case EdgeRemove<V, E> edgeRemove -> addEdge(edgeRemove.v1, edgeRemove.v2, edgeRemove.e);
                case VertexAdd<V, E> vertexAdd -> removeVertex(vertexAdd.v);
                default -> throw new IllegalStateException("Unexpected value: " + it.next());
            }
        }

        modificationsStack.pop();
    }

    private Modifications checkSavedContext() {
        if (modificationsStack.isEmpty()) {
            throw new PowsyblException("Cannot compute connectivity without a saved state, please call GraphConnectivity::startTemporaryChanges at least once beforehand");
        }
        return modificationsStack.peek();
    }

    @Override
    public int getComponentNumber(V vertex) {
        checkSavedContext();
        DTNode node = getNodeOrThrow(vertex);
        sortComponents();

        return node.findRootOptReroot().rootIndex;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        if (!modificationsStack.isEmpty()) {
            var modifications = modificationsStack.peek();
            modifications.setMainComponentVertex(mainComponentVertex);
        }
        defaultMainComponentVertex = mainComponentVertex;
    }

    private boolean isInMainComponent(DTNode node) {
        Modifications modifications = modificationsStack.peek();

        if (modifications == null) {
            return false;
        } else {
            return getMainComponentRoot(modifications.mainComponentVertex) == node.findRoot();
        }
    }

    /**
     * @param mainComponentVertex a vertex in the main component tree, may be null
     * @return the root of the tree containing mainComponentVertex, if not null,
     * or the root of the biggest tree
     */
    private DTNode getMainComponentRoot(V mainComponentVertex) {
        if (mainComponentVertex != null) {
            return rootOf(mainComponentVertex);
        } else {
            DTNode biggestRoot = roots.getFirst();

            for (int i = 1; i < roots.size(); i++) {
                DTNode root = roots.get(i);
                if (root.size > biggestRoot.size) {
                    biggestRoot = root;
                }
            }

            return biggestRoot;
        }
    }

    @Override
    public int getNbConnectedComponents() {
        checkSavedContext();
        return roots.size();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        DTNode node = getNodeOrThrow(vertex);
        return node.findRootOptReroot().componentView();
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        checkSavedContext();
        sortComponents();

        return roots.getFirst().componentView();
    }

    // =========================
    // * MODIFICATIONS SUPPORT *
    // =========================

    private void markAllAdded(DTNode root) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllAdded(root);
    }

    private void markAllRemoved(DTNode root) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllRemoved(root);
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        return checkSavedContext().verticesState.getRemoved();
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        return checkSavedContext().edgesState.getRemoved();
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        return checkSavedContext().verticesState.getAdded();
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        return checkSavedContext().edgesState.getAdded();
    }

    // ==============
    // * INVARIANTS *
    // ==============

    private void check() {
        if (!debug) {
            return;
        }

        checkEdges();
        checkParentChildRelation();
    }

    private void checkEdges() {
        for (DTNode node : vertexToTreeNode.values()) {
            for (E nonTreeEdge : node.nonTreeEdges) {
                assert edges.containsKey(nonTreeEdge) && !edges.get(nonTreeEdge).treeEdge;
            }
        }

        for (Map.Entry<E, Edge> entry : edges.entrySet()) {
            E e = entry.getKey();
            Edge edge = entry.getValue();

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

    private void checkParentChildRelation() {
        for (DTNode node : vertexToTreeNode.values()) {
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

    /**
     * A DTNode (Dynamic Tree Node) is a node in a spanning tree.
     * Each DTNode maintains the following information:
     * <ul>
     *     <li>the vertex in the graph,</li>
     *     <li>the size of the subtree,</li>
     *     <li>its parent in the tree and the edge linking them,</li>
     *     <li>its children in the tree,</li>
     *     <li>all non tree edges having at least one endpoint that is the DTNode</li>
     * </ul>
     *
     * <p>
     * However, children are stored in a particular way, allowing
     * fast iteration over a tree and insertion and removal of a child,
     * but slow access to an arbitrary element. Instead of storing them
     * in a list or a map, each DTNode has a pointer to its previous sibling,
     * its next sibling and its first child. In other words, the children of
     * a DTNode are stored in a doubly-linked list. A DTNode stores the first
     * element in this list and is also used as an element in its parent doubly
     * linked list of children.
     * </p>
     *
     * <p>
     * Example:
     * <pre>
     * +----- first child ------ 1
     * |                         ^
     * |                         |
     * |                      parent
     * |                         |
     * | +-----------------------+-----------------------+
     * v/                        |                        \
     * 2 <-- previous sibling -- 3 <-- previous sibling -- 4
     *  \_____ next sibling _____^\_____ next sibling _____^
     * </pre>
     * X --> Y indicates that X contains a pointer to Y.
     *</p>
     *
     * <p>
     * This complex structure allows fast insertion and removal as we only need
     * to update the sibling list and eventually the first child pointer. But the
     * biggest advantage is that it allows fast iteration of a tree with 0 memory
     * allocations by only following pointers. See {@link DFSIterator}
     * </p>
     *
     */
    private final class DTNode {

        private final V vertex;

        // the size of this subtree
        private int size;

        private DTNode parent = null;
        private E parentEdge = null;

        // the children of this node. They are stored in a
        // doubly linked list
        private DTNode previousSibling = null;
        private DTNode nextSibling = null;
        private DTNode firstChild = null;

        private final Set<E> childTreeEdges = new LinkedHashSet<>();
        private final Set<E> nonTreeEdges = new LinkedHashSet<>();

        // valid only if this node is a root
        private int rootIndex;

        private ComponentView componentView = null;

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

        private Pair<DTNode, Integer> findRootWithDepth() {
            DTNode node = this;
            int depth = 0;

            while (node.parent != null) {
                node = node.parent;
                depth++;
            }

            return new ImmutablePair<>(node, depth);
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
        @Deprecated
        public Set<V> componentView() {
            if (componentView == null) {
                componentView = new ComponentView(this);
            }

            return componentView;
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
        private DTNode current;

        /**
         * Creates a new depth-first iterator starting at the specified root node
         * and returning node according to the pre-order.
         *
         * @param root the root of the tree to traverse. It must be a root otherwise,
         *             the iterator may visit nodes outside the subtree
         */
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

            current = cursor;

            // Advances to the next node for the next iteration.
            // The iterator try to:
            // - descend one level whenever possible,
            // - otherwise, moves to the next sibling if any,
            // - otherwise, moves up until it finds a node with
            //   a next (unvisited) sibling or the tree is fully visited.

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

            return current.vertex;
        }

        public DTNode node() {
            return current;
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

    private enum State {
        ADDED,
        REMOVED,
    }

    private final class Modifications {

        private final Deque<GraphModification<V, E>> modifications = new ArrayDeque<>();
        private final StateMap<V> verticesState;
        private final StateMap<E> edgesState;

        private V mainComponentVertex;

        private boolean undoing = false;

        Modifications(V mainComponentVertex, boolean computeStates) {
            this.mainComponentVertex = mainComponentVertex;

            if (computeStates) {
                verticesState = new StateMap<>();
                edgesState = new StateMap<>();
            } else {
                verticesState = null;
                edgesState = null;
            }
        }

        public void push(GraphModification<V, E> modification) {
            if (!undoing) {
                modifications.push(modification);
            }
        }

        public void setMainComponentVertex(V mainComponentVertex) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            V old = this.mainComponentVertex;
            this.mainComponentVertex = mainComponentVertex;

            if (old != mainComponentVertex) {
                DTNode root1 = rootOf(old);
                DTNode root2 = rootOf(mainComponentVertex);

                if (root1 != root2) {
                    if (verticesState.get(mainComponentVertex) != State.REMOVED) {
                        throw new PowsyblException("Cannot take the given vertex as main component vertex! This vertex was outside the main component before starting temporary changes");
                    }

                    markAllRemoved(root1);
                    markAllAdded(root2);
                }
            }
        }

        public void markEdgeAdded(E edge) {
            if (edgesState != null && !undoing) {
                edgesState.markAdded(edge);
            }
        }

        public void markEdgeRemoved(E edge) {
            if (edgesState != null && !undoing) {
                edgesState.markRemoved(edge);
            }
        }

        public void markAllAdded(DTNode root) {
            markAll(root, State.ADDED);
        }

        public void markAllRemoved(DTNode root) {
            markAll(root, State.REMOVED);
        }

        public void markAll(DTNode root, State newState) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            for (DFSIterator it = new DFSIterator(root); it.hasNext();) {
                V vertex = it.next();
                verticesState.mark(vertex, newState);

                DTNode node = it.node();
                if (node.parentEdge != null) {
                    edgesState.mark(node.parentEdge, newState);
                }

                for (E nte : node.nonTreeEdges) {
                    if (getEdgeSource(nte).equals(vertex)) {
                        edgesState.mark(nte, newState);
                    }
                }

                // we don't mark child tree edges as removed
                // because for each child tree edge, there is a parentEdge
                // so if we mark a parent edge as removed, we also mark
                // the corresponding child tree edge as removed
            }
        }
    }

    private static final class StateMap<T> extends HashMap<T, State> {

        private Set<T> removed;
        private Set<T> added;

        public void markAdded(T element) {
            mark(element, State.ADDED);
        }

        public void markRemoved(T element) {
            mark(element, State.REMOVED);
        }

        public void mark(T element, State newState) {
            compute(element, (k, state) -> {
                if (state == null || state == newState) {
                    return newState;
                } else {
                    return null;
                }
            });

            removed = null;
            added = null;
        }

        private Set<T> getRemoved() {
            if (removed == null) {
                removed = entrySet().stream()
                        .filter(e -> e.getValue() == State.REMOVED)
                        .map(Entry::getKey)
                        .collect(Collectors.toSet());
            }

            return removed;
        }

        private Set<T> getAdded() {
            if (added == null) {
                added = entrySet()
                        .stream()
                        .filter(e -> e.getValue() == State.ADDED)
                        .map(Entry::getKey)
                        .collect(Collectors.toSet());
            }
            return added;
        }
    }
}
