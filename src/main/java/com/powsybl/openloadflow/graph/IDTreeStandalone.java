/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.graph.dtree.AbstractSetView;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class IDTreeStandalone<V, E> implements SpanningForestGraphConnectivity<V, E> {

    public static boolean debug = false;

    private final Map<V, IDTNode> vertexToTreeNode = new HashMap<>();
    private final Map<E, Edge> edges = new HashMap<>();

    private final List<IDTNode> roots = new ArrayList<>();
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

    private IDTNode getNodeOrThrow(V v) {
        IDTNode node = vertexToTreeNode.get(v);
        if (node == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return node;
    }

    private IDTNode rootOf(V vertex) {
        return vertexToTreeNode.get(vertex).findRoot();
    }

    private IDTNode rootOfOptReroot(V vertex) {
        return vertexToTreeNode.get(vertex).findRootOptReroot();
    }

    private void sortTrees() {
        if (!isSorted) {
            roots.sort((s1, s2) -> s2.size - s1.size);
            for (int i = 0; i < roots.size(); i++) {
                roots.get(i).rootIndex = i;
            }
            isSorted = true;
        }
    }

    @Override
    public long computeSumOfDistances() {
        long sum = 0;

        for (IDTNode node : vertexToTreeNode.values()) {
            sum += node.findRootWithDepth().getValue();
        }

        return sum;
    }

    @Override
    public int vertexCount() {
        return vertexToTreeNode.size();
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        if (containsVertex(vertex)) {
            return;
        }

        IDTNode newNode = new IDTNode(vertex);
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

        IDTNode node = vertexToTreeNode.get(v); // can't use Map#remove because removeEdge will update this DTNode
        while (!node.incidentEdges.isEmpty()) {
            // this is weird, but removeEdge will modify node.incidentEdges, invalidating the iterator...
            E edge = node.incidentEdges.iterator().next();
            removeEdge(edge);
        }

        IDTNode root = vertexToTreeNode.remove(v);
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
        IDTNode nodeU = getNodeOrThrow(vertex1);
        IDTNode nodeV = getNodeOrThrow(vertex2);

        Pair<IDTNode, Integer> rootUdepth = nodeU.findRootWithDepth();
        Pair<IDTNode, Integer> rootVdepth = nodeV.findRootWithDepth();

        IDTNode rootU = rootUdepth.getKey();
        IDTNode rootV = rootVdepth.getKey();

        boolean treeEdge;
        if (rootU == rootV) {
            // insert non tree edge
            insertNonTreeEdgeRecordModifications(rootU, edge);
            treeEdge = insertNonTreeEdge(rootU, nodeU, rootUdepth.getValue(), nodeV, rootVdepth.getValue(), edge);
        } else {
            // insert tree edge
            insertTreeEdgeRecordModifications(rootU, rootV, edge);
            IDTNode newRoot = insertTreeEdge(rootU, nodeU, rootV, nodeV, edge);

            if (!modificationsStack.isEmpty()) {
                modificationsStack.peek().notifyInsertTreeEdge(newRoot);
            }

            treeEdge = true;
        }

        edges.put(edge, new Edge(vertex1, vertex2, treeEdge));
        nodeU.incidentEdges.add(edge);
        nodeV.incidentEdges.add(edge);

        // keep track of modifications
        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new EdgeAdd<>(vertex1, vertex2, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        check();
    }

    private void insertNonTreeEdgeRecordModifications(IDTNode root, E edge) {
        if (isInMainComponentBefore(root)) {
            checkSavedContext().markEdgeAdded(edge);
        }
    }

    private boolean insertNonTreeEdge(IDTNode root, IDTNode nodeU, int depthU, IDTNode nodeV, int depthV, E edge) {
        IDTNode deep;
        IDTNode shallow;
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

        if (delta >= 2) {
            IDTNode i = deep;
            for (int j = 0; j < delta / 2 - 1; j++) {
                i = i.parent;
            }

            edges.get(i.parentEdge).treeEdge = false;

            unlink(i);
            // updating roots is useless because 'deep' will be
            // connected to 'shallow' juste after.
            deep.makeRoot(false);
            link(root, shallow, deep, edge);
            return true;
        }
        return false;
    }

    private void insertTreeEdgeRecordModifications(IDTNode rootU, IDTNode rootV, E edge) {
        if (isInMainComponentBefore(rootV)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootU);
        } else if (isInMainComponentBefore(rootU)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootV);
        }
    }

    private IDTNode insertTreeEdge(IDTNode rootU, IDTNode nodeU, IDTNode rootV, IDTNode nodeV, E edge) {
        if (rootU.size < rootV.size) {
            nodeU.makeRoot(true);
            link(rootV, nodeV, nodeU, edge);
            removeRoot(nodeU);
            return nodeV;
        } else {
            nodeV.makeRoot(true);
            link(rootU, nodeU, nodeV, edge);
            removeRoot(nodeV);
            return nodeU;
        }
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
        IDTNode nodeU = vertexToTreeNode.get(e.u);
        IDTNode nodeV = vertexToTreeNode.get(e.v);

        nodeU.incidentEdges.remove(edge);
        nodeV.incidentEdges.remove(edge);

        if (e.treeEdge) {
            removeTreeEdge(nodeU, nodeV, edge);

            if (!modificationsStack.isEmpty()) {
                modificationsStack.peek().notifyRemoveTreeEdge();
            }
        } else {
            removeNonTreeEdgeRecordModifications(nodeU, edge);
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

    private void removeTreeEdge(IDTNode nodeU, IDTNode nodeV, E edge) {
        IDTNode child;

        if (nodeU == nodeV.parent) {
            child = nodeV;
        } else {
            child = nodeU;
        }

        IDTNode otherTree = unlink(child);
        addRoot(child);

        IDTNode small;
        IDTNode large;
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
    private void replace(IDTNode rootSmall, IDTNode rootLarge, E removedEdge) {
        boolean replacementEdgeFound = false;

        ArrayDeque<IDTNode> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        Set<IDTNode> visited = new HashSet<>();
        visited.add(rootSmall);

        loop:
        while (!queue.isEmpty()) {
            IDTNode n = queue.poll();

            for (E nonTreeEdge : n.incidentEdges) {
                Edge edge = edges.get(nonTreeEdge);
                V opp = edge.opposite(n.vertex);
                IDTNode oppNode = vertexToTreeNode.get(opp);

                if (edge.treeEdge) {
                    if (n == oppNode.parent) {
                        visited.add(oppNode);
                        queue.offer(oppNode);
                    }
                } else {
                    boolean inLargeTree = true;

                    IDTNode w = oppNode;
                    while (w != null && inLargeTree) {
                        if (!visited.add(w)) {
                            inLargeTree = false;
                            break;
                        }
                        w = w.parent;
                    }

                    if (inLargeTree) {
                        // found a replacement edge
                        removeNonTreeEdge(n, oppNode, nonTreeEdge);
                        insertTreeEdge(rootSmall, n, rootLarge, oppNode, nonTreeEdge);
                        edges.get(nonTreeEdge).treeEdge = true;
                        replacementEdgeFound = true;

                        break loop;
                    }
                }
            }
        }

        if (isInMainComponentBefore(rootLarge) || isInMainComponentBefore(rootSmall)) {
            checkSavedContext().markEdgeRemoved(removedEdge);
        }

        if (!replacementEdgeFound) {
            if (isInMainComponentBefore(rootLarge)) {
                markAllRemoved(rootSmall);
            } else if (isInMainComponentBefore(rootSmall)) {
                markAllRemoved(rootLarge);
            }
        }
    }

    private void removeNonTreeEdgeRecordModifications(IDTNode node, E edge) {
        if (isInMainComponentBefore(node)) {
            checkSavedContext().markEdgeRemoved(edge);
        }
    }

    private void removeNonTreeEdge(IDTNode nodeU, IDTNode nodeV, E edge) {

    }

    // ======================
    // * TREE MANIPULATIONS *
    // ======================

    private void link(IDTNode rootU, IDTNode nodeU, IDTNode rootV, E edge) {
        // first: update parent/child relations
        rootV.parent = nodeU;
        rootV.parentEdge = edge;

        // next: update size attributes in the parent tree
        IDTNode newCentroid = null;
        IDTNode cur = nodeU;

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

    private IDTNode unlink(IDTNode node) {
        Objects.requireNonNull(node.parent);

        // first step: update size attribute in the parent tree
        IDTNode newTree = node;
        while (newTree.parent != null) {
            newTree = newTree.parent;
            newTree.size -= node.size;
        }

        // second step: update parent/child relations
        node.parent = null;
        node.parentEdge = null;
        return newTree;
    }

    private void addRoot(IDTNode node) {
        node.rootIndex = roots.size();
        roots.add(node);
    }

    private void removeRoot(IDTNode node) {
        // update roots, swapping 'node' and the last element of roots
        IDTNode last = roots.removeLast();
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
    public void startTemporaryChanges(boolean computeComparisons) {
        V mainComponentVertex = defaultMainComponentVertex;
        boolean fictitious = false;
        if (mainComponentVertex == null) {
            IDTNode root = getBiggestRoot();
            mainComponentVertex = root.vertex;
            fictitious = true;
        }

        modificationsStack.push(new Modifications(mainComponentVertex, fictitious, computeComparisons));
    }

    @Override
    public void undoTemporaryChanges() {
        if (modificationsStack.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }

        Modifications modifications = modificationsStack.peek();
        modifications.undoing = true;

        for (GraphModification<V, E> gm : modifications.modifications) {
            switch (gm) {
                case EdgeAdd<V, E> edgeAdd -> removeEdge(edgeAdd.e);
                case EdgeRemove<V, E> edgeRemove -> addEdge(edgeRemove.v1, edgeRemove.v2, edgeRemove.e);
                case VertexAdd<V, E> vertexAdd -> removeVertex(vertexAdd.v);
                default -> throw new IllegalStateException("Unexpected value: " + gm);
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
        IDTNode node = getNodeOrThrow(vertex);
        sortTrees();

        return node.findRootOptReroot().rootIndex;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        if (!modificationsStack.isEmpty()) {
            Modifications modifications = modificationsStack.peek();
            modifications.setMainComponentVertex(mainComponentVertex);
        }
        defaultMainComponentVertex = mainComponentVertex;
    }

    private boolean isInMainComponentBefore(IDTNode node) {
        Modifications modifications = modificationsStack.peek();

        if (modifications == null) {
            return false;
        } else {
            return rootOfOptReroot(modifications.mainComponentVertex) == node.findRoot();
        }
    }

    private IDTNode getBiggestRoot() {
        IDTNode biggestRoot = roots.getFirst();

        for (int i = 1; i < roots.size(); i++) {
            IDTNode root = roots.get(i);
            if (root.size > biggestRoot.size) {
                biggestRoot = root;
            }
        }

        return biggestRoot;
    }

    @Override
    public int getNbConnectedComponents() {
        checkSavedContext();
        return roots.size();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        IDTNode node = getNodeOrThrow(vertex);
        return node.findRootOptReroot().componentView();
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        checkSavedContext();
        sortTrees();

        return roots.getFirst().componentView();
    }

    // =========================
    // * MODIFICATIONS SUPPORT *
    // =========================

    private void markAllAdded(IDTNode root) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllAdded(root);
    }

    private void markAllRemoved(IDTNode root) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllRemoved(root);
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        return checkSavedContext().getVerticesRemovedFromMainComponent();
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        return checkSavedContext().getEdgesRemovedFromMainComponent();
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        return checkSavedContext().getVerticesAddedToMainComponent();
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        return checkSavedContext().getEdgesAddedToMainComponent();
    }

    // ==============
    // * INVARIANTS *
    // ==============

    private void check() {
        if (!debug) {
            return;
        }

        checkEdges();
        checkIDTNodes();
    }

    private void checkIDTNodes() {
        for (IDTNode node : vertexToTreeNode.values()) {
            for (E e : node.incidentEdges) {
                Edge edge = edges.get(e);
                assert node.vertex.equals(edge.u) || node.vertex.equals(edge.v);
            }
        }
    }

    private void checkEdges() {
        for (Map.Entry<E, Edge> entry : edges.entrySet()) {
            E e = entry.getKey();
            Edge edge = entry.getValue();

            IDTNode src = Objects.requireNonNull(vertexToTreeNode.get(edge.u));
            IDTNode dest = Objects.requireNonNull(vertexToTreeNode.get(edge.v));

            if (edge.treeEdge) {
                assert src.parent == dest && src.parentEdge == e || dest.parent == src && dest.parentEdge == e;
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
     *     <li>its children in the tree and the edges linking them,</li>
     *     <li>all non tree edges having at least one endpoint that is the DTNode</li>
     *     <li>if the node is a root, its index in the list of {@link IDTreeStandalone#roots}</li>
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
    private final class IDTNode {

        private final V vertex;

        // the size of this subtree
        private int size;

        private IDTNode parent = null;
        private E parentEdge = null;

        private final Set<E> incidentEdges = new HashSet<>();

        // index in the list of roots, valid only if this node is a root
        private int rootIndex;

        private ComponentView componentView = null;

        IDTNode(V vertex) {
            this.vertex = vertex;
            this.size = 1;
        }

        /**
         * Make this DTNode the root of the tree in which it is. This is a two steps process
         * with an intermediate optional operation:
         * <ol>
         *     <li>Swap parent-child relationship for each DTNode from this dTNode to the original root</li>
         *     <li>Optionally, update the list of roots. For most cases, it must be {@code true}</li>
         *     <li>Update the subtree size attribute from the original root to the new root (this DTNode)</li>
         * </ol>
         *
         * @param updateRoots {@code true} to update the list of roots
         */
        private void makeRoot(boolean updateRoots) {
            if (parent == null) {
                return;
            }

            IDTNode child = this;
            IDTNode parent = child.parent;
            E parentEdge = child.parentEdge;

            this.parent = null;
            this.parentEdge = null;

            // swap parent/child relation
            while (parent != null) {
                IDTNode greatParent = parent.parent;
                E greatParentEdge = parent.parentEdge;

                // At this point:
                // - 'parent' is in the linked list of children of 'greatParent', and must be
                //   removed from it because adding 'parent' as a child of 'child' will break
                //   this linked list.
                // - 'child' is NOT in the linked list of children of 'parent'.
                //   It was removed by the last iteration or before entering in the loop (for the first iteration)
                // - the parent of 'parent' aka 'greatParent' should be changed to child

                parent.parent = child;
                parent.parentEdge = parentEdge;

                // At this point:
                // - parent isn't anymore is the linked list of child of greatParent
                // - parent is a child of 'child'

                // process to the next parent/child
                child = parent;
                parent = greatParent;
                parentEdge = greatParentEdge;
            }

            // child is the old root
            IDTNode oldRoot = child;

            // update the list of roots
            if (updateRoots) {
                rootIndex = oldRoot.rootIndex;
                roots.set(rootIndex, IDTNode.this);
            }

            // update size attributes, going from oldRoot to this DTNode
            while (oldRoot.parent != null) {
                oldRoot.size -= oldRoot.parent.size;
                oldRoot.parent.size += oldRoot.size;
                oldRoot = oldRoot.parent;
            }
        }

        private IDTNode findRoot() {
            IDTNode node = this;

            while (node.parent != null) {
                node = node.parent;
            }

            return node;
        }

        private Pair<IDTNode, Integer> findRootWithDepth() {
            IDTNode node = this;
            int depth = 0;

            while (node.parent != null) {
                node = node.parent;
                depth++;
            }

            return new ImmutablePair<>(node, depth);
        }

        /**
         * Returns the root of the tree containing this node
         * and restores the centroid property by rerooting the
         * tree. This helps reduce the height of the tree.
         * <strong>Warning</strong>: rerooting the tree may
         * break connectivity queries.
         *
         * @return the root of the tree
         */
        private IDTNode findRootOptReroot() {
            IDTNode nodeRoot = this;
            IDTNode nodeRootChild = null; // the child of nodeRoot in the path from nodeRoot to node

            // find the parent
            while (nodeRoot.parent != null) {
                nodeRootChild = nodeRoot;
                nodeRoot = nodeRoot.parent;
            }

            // Restores the centroid property. See Theorem 5.12.
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

        /*@Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(vertex.toString()).append(" -te-> {");

            Set<V> set = new HashSet<>();

            IDTNode child = firstChild;
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
        }*/
    }

    private final class ComponentView extends AbstractSetView<V> {

        private final IDTNode node;

        ComponentView(IDTNode node) {
            this.node = node;
        }

        @Override
        public Iterator<V> iterator() {
            return new DFSIterator(node.findRoot());
        }

        @Override
        public boolean contains(Object o) {
            if (o != null) {
                // node might not be the root anymore, so need to use findRoot on node.
                // However, don't use findRootOptReroot on node, it might change the root
                // after we got the root of 'o'.

                return rootOfOptReroot((V) o) == node.findRoot();
            }

            return false;
        }

        @Override
        public int size() {
            return node.findRoot().size;
        }
    }

    private final class DFSIterator implements Iterator<V> {

        private ArrayDeque<IDTNode> queue;
        private IDTNode current;

        /**
         * Creates a new depth-first iterator starting at the specified root node
         * and returning node according to the pre-order.
         *
         * @param root the root of the tree to traverse. It must be a root otherwise,
         *             the iterator may visit nodes outside the subtree
         */
        DFSIterator(IDTNode root) {
            queue = new ArrayDeque<>();
            queue.offer(root);
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            current = queue.remove();
            for (E e : current.incidentEdges) {
                Edge edge = edges.get(e);
                if (edge.treeEdge) {
                    V opposite = edge.opposite(current.vertex);
                    IDTNode node = vertexToTreeNode.get(opposite);

                    if (node.parent == current) {
                        queue.offer(node);
                    }
                }
            }

            return current.vertex;
        }

        public IDTNode node() {
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

    /**
     * Contains modifications performed on the graph between
     * the last call to {@link #startTemporaryChanges(boolean)}
     * and the current instant. It stores a stack of {@link GraphModification}
     * and optionally the set of vertices and edges added to the
     * main component or removed from it.
     */
    private final class Modifications {

        private final Deque<GraphModification<V, E>> modifications = new ArrayDeque<>();
        private final StateMap<V> verticesState;
        private final StateMap<E> edgesState;

        // true when the user didn't set the main component vertex
        // in this case, we set the main component vertex as a node
        // in the biggest component to avoid mainComponentVertex being
        // null and keep this class functional. However, it has an
        // impact on how edges/vertices removed from/added to are computed
        private boolean isMainComponentVertexFictitious;
        private V mainComponentVertex;

        private boolean undoing = false;

        Modifications(V mainComponentVertex, boolean fictitiousMCV, boolean computeComparisons) {
            this.mainComponentVertex = mainComponentVertex;
            this.isMainComponentVertexFictitious = fictitiousMCV;

            if (computeComparisons) {
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

        /**
         * Change the main component vertex to the specified one.
         *
         * @param mainComponentVertex new vertex identifying the main component.
         */
        public void setMainComponentVertex(V mainComponentVertex) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            if (this.mainComponentVertex != mainComponentVertex) {
                // two things to do:
                // 1. check if the new main component vertex was in the main component before temporary changes.
                // 2. if the main component vertex isn't in the current main component vertex, we need to
                //    update state of edges and vertices

                IDTNode oldComponentRoot = rootOfOptReroot(this.mainComponentVertex);
                IDTNode newComponentRoot = rootOf(mainComponentVertex);

                if (oldComponentRoot != newComponentRoot) {
                    // the new main component vertex isn't in the current main component.
                    // But that doesn't mean it wasn't in the main component before starting temporary changes,
                    // it may have been removed.
                    if (verticesState.get(mainComponentVertex) != StateMap.State.REMOVED) {
                        throw new PowsyblException("Cannot take the given vertex as main component vertex! This vertex was outside the main component before starting temporary changes");
                    }

                    // last thing to do is update state of vertices and edges in the two trees.
                    markAllRemoved(oldComponentRoot);
                    markAllAdded(newComponentRoot);
                }

                this.mainComponentVertex = mainComponentVertex;
            }

            isMainComponentVertexFictitious = false;
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

        public void markAllAdded(IDTNode root) {
            markAll(root, StateMap.State.ADDED);
        }

        public void markAllRemoved(IDTNode root) {
            markAll(root, StateMap.State.REMOVED);
        }

        public void markAll(IDTNode root, StateMap.State newState) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            for (DFSIterator it = new DFSIterator(root); it.hasNext();) {
                V vertex = it.next();
                verticesState.mark(vertex, newState);

                IDTNode node = it.node();
                for (E nte : node.incidentEdges) {
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

        public void notifyInsertTreeEdge(IDTNode newTree) {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(newTree);
            }
        }

        public void notifyRemoveTreeEdge() {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(getBiggestRoot());
            }
        }

        private void maybeBiggestTreeChanged(IDTNode currentBiggestRoot) {
            IDTNode mainComponentVertexTree = rootOf(mainComponentVertex);
            if (currentBiggestRoot.size > mainComponentVertexTree.size) {
                // there is a new biggest main component
                markAllRemoved(mainComponentVertexTree);
                markAllAdded(currentBiggestRoot);
                mainComponentVertex = currentBiggestRoot.vertex;
            }
        }

        public Set<V> getVerticesRemovedFromMainComponent() {
            if (verticesState == null) {
                throw new PowsyblException("Topological comparisons are disabled for the current temporary changes context!");
            }
            return verticesState.getRemoved();
        }

        public Set<E> getEdgesRemovedFromMainComponent() {
            if (edgesState == null) {
                throw new PowsyblException("Topological comparisons are disabled for the current temporary changes context!");
            }
            return edgesState.getRemoved();
        }

        public Set<V> getVerticesAddedToMainComponent() {
            if (verticesState == null) {
                throw new PowsyblException("Topological comparisons are disabled for the current temporary changes context!");
            }
            return verticesState.getAdded();
        }

        public Set<E> getEdgesAddedToMainComponent() {
            if (edgesState == null) {
                throw new PowsyblException("Topological comparisons are disabled for the current temporary changes context!");
            }
            return edgesState.getAdded();
        }
    }
}
