/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;

import java.util.*;

/**
 * DnDTree implementation from <a href="https://dl.acm.org/doi/epdf/10.1145/3698805"/>
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DnDTreeStandalone<V, E> implements SpanningForestGraphConnectivity<V, E> {

    public static boolean debug = false;

    private final Map<V, IDNode> vertexToTreeNode = new HashMap<>();
    private final Map<E, Edge> edges = new HashMap<>();

    private final List<IDNode> roots = new ArrayList<>();
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
            case Edge e -> e.nodeU.vertex;
        };
    }

    public V getEdgeTarget(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge e -> e.nodeV.vertex;
        };
    }

    private IDNode getNodeOrThrow(V v) {
        IDNode node = vertexToTreeNode.get(v);
        if (node == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return node;
    }

    private IDNode rootOf(V vertex) {
        return vertexToTreeNode.get(vertex).findRoot();
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
    public long computeSd() {
        long sum = 0;

        for (IDNode node : vertexToTreeNode.values()) {
            sum += node.depth();
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

        IDNode newNode = new IDNode(vertex);
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

        IDNode node = vertexToTreeNode.get(v); // can't use Map#remove because removeEdge will update this DTNode
        while (!node.incidentEdges.isEmpty()) {
            // this is weird, but removeEdge will modify node.nonTreeEdges, invalidating the iterator...
            E edge = node.incidentEdges.iterator().next();
            removeEdge(edge);
        }
        if (node.parentEdge != null) {
            removeEdge(node.parentEdge);
        }

        IDNode root = vertexToTreeNode.remove(v);
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

        // first update graph
        IDNode nodeU = getNodeOrThrow(vertex1);
        IDNode nodeV = getNodeOrThrow(vertex2);

        // then the spanning tree and vertices and edges state
        IDNode rootU = nodeU.findRoot();
        IDNode rootV = nodeV.findRoot();

        boolean treeEdge;
        IDNode mergedTree = null; // in case two trees were merged
        if (rootU == rootV) {
            // insert non tree edge
            insertNonTreeEdgeRecordModifications(rootU, edge);
            treeEdge = insertNonTreeEdge(rootU, nodeU, nodeV, edge);
        } else {
            // insert tree edge
            insertTreeEdgeRecordModifications(rootU, rootV, edge);
            mergedTree = insertTreeEdge(rootU, nodeU, rootV, nodeV, edge);
            treeEdge = true;
        }

        nodeU.incidentEdges.add(edge);
        nodeV.incidentEdges.add(edge);
        edges.put(edge, new Edge(nodeU, nodeV, treeEdge));

        // keep track of modifications
        Modifications modifications = modificationsStack.peek();
        if (modifications != null) {
            if (mergedTree != null) {
                modifications.notifyInsertTreeEdge(mergedTree);
            }

            modifications.push(new EdgeAdd<>(vertex1, vertex2, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        check();
    }

    private void insertNonTreeEdgeRecordModifications(IDNode root, E edge) {
        if (isInMainComponent(root)) {
            checkSavedContext().markEdgeAdded(edge);
        }
    }

    private boolean insertNonTreeEdge(IDNode root, IDNode nodeU, IDNode nodeV, E edge) {
        // at this point, we don't know if 'deep' depth is greater than 'shallow' depth
        int depthU = nodeU.depth();
        int depthV = nodeV.depth();

        int delta = depthU - depthV;
        IDNode deep = nodeU;
        IDNode shallow = nodeV;
        if (depthV > depthU) {
            deep = nodeV;
            shallow = nodeU;
            delta = -delta;
        }

        if (delta >= 2) {
            IDNode ancestor = deep;
            for (int j = 0; j < delta / 2 - 1; j++) {
                ancestor = ancestor.parent;
            }

            edges.get(ancestor.parentEdge).treeEdge = false;

            ancestor.unlink();
            deep.makeRoot(false);
            deep.linkWithParent(root, shallow, edge);
            return true;
        }
        return false;
    }

    private void insertTreeEdgeRecordModifications(IDNode rootU, IDNode rootV, E edge) {
        if (isInMainComponent(rootV)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootU);
        } else if (isInMainComponent(rootU)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootV);
        }
    }

    private IDNode insertTreeEdge(IDNode rootU, IDNode nodeU, IDNode rootV, IDNode nodeV, E edge) {
        if (rootU.size < rootV.size) {
            // update ID Tree by making nodeU a root and making it a child of nodeV
            nodeU.makeRoot(true);
            IDNode newRootV = nodeU.linkWithParent(rootV, nodeV, edge);
            newRootV.dsNode.link(rootU.dsNode);
            removeRoot(nodeU);
            return newRootV;
        } else {
            // update ID Tree by making nodeV a root and making it a child of nodeU
            nodeV.makeRoot(true);
            IDNode newRootU = nodeV.linkWithParent(rootU, nodeU, edge);
            newRootU.dsNode.link(rootV.dsNode);
            removeRoot(nodeV);
            return newRootU;
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

        // update graph
        e.nodeU.incidentEdges.remove(edge);
        e.nodeV.incidentEdges.remove(edge);

        // update the spanning tree
        if (e.treeEdge) {
            removeTreeEdge(e.nodeU, e.nodeV, edge);
        } else {
            removeNonTreeEdgeRecordModifications(e.nodeU, edge);
        }

        // keep track of modifications
        Modifications modifications = modificationsStack.peek();
        if (modifications != null) {
            if (e.treeEdge) {
                modifications.notifyRemoveTreeEdge();
            }

            modifications.push(new EdgeRemove<>(e.nodeU.vertex, e.nodeV.vertex, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        check();
    }

    private void removeTreeEdge(IDNode nodeU, IDNode nodeV, E edge) {
        IDNode child;
        if (nodeU == nodeV.parent) {
            child = nodeV;
        } else {
            child = nodeU;
        }

        // unlink child from its parent in the ID tree.
        // they are still in the same DS tree after
        IDNode otherTree = child.unlink();
        addRoot(child);

        IDNode small;
        IDNode large;
        if (child.size < otherTree.size) {
            small = child;
            large = otherTree;
        } else {
            small = otherTree;
            large = child;
        }

        // try to reconnect them
        replace(small, large, edge);
    }

    // search a replacement edge by doing a BFS over the smaller tree between rootSmall and rooLarge
    private void replace(IDNode rootSmall, IDNode rootLarge, E removedEdge) {
        boolean replacementEdgeFound = false;
        IDNode newRoot = null; // a potential new root in case no replacement edge is found

        ArrayDeque<IDNode> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        Set<IDNode> visited = new HashSet<>();
        visited.add(rootSmall);

        loop:
        while (!queue.isEmpty()) {
            IDNode n = queue.poll();

            if (n != rootSmall && n.size > rootSmall.size / 2) {
                newRoot = n;
            }

            for (E nonTreeEdge : n.incidentEdges) {
                Edge edge = edges.get(nonTreeEdge);
                IDNode oppNode = edge.opposite(n);

                if (edge.treeEdge) {
                    if (n == oppNode.parent) {
                        visited.add(oppNode);
                        queue.offer(oppNode); // only go down
                    }
                } else if (allAncestorUnvisited(oppNode, visited)) {
                    // found a replacement edge
                    n.makeRoot(true);
                    newRoot = n.linkWithParent(rootLarge, oppNode, nonTreeEdge);
                    newRoot.dsNode.makeRoot();
                    removeRoot(n);
                    edges.get(nonTreeEdge).treeEdge = true;
                    replacementEdgeFound = true;

                    break loop;
                }
            }
        }

        if (!replacementEdgeFound) {
            rootLarge.dsNode.makeRoot();

            rootSmall.dsNode.isolate();
            visited.remove(rootSmall);
            for (IDNode n : visited) {
                n.dsNode.isolate();
                rootSmall.dsNode.link(n.dsNode);
            }

            if (newRoot != null) {
                newRoot.makeRootKeepConsistent();
            }
        }

        replaceRecordModifications(rootSmall, rootLarge, removedEdge, replacementEdgeFound);
    }

    private boolean allAncestorUnvisited(IDNode node, Set<IDNode> visited) {
        IDNode ancestor = node;
        while (ancestor != null) {
            if (!visited.add(ancestor)) {
                return false;
            }
            ancestor = ancestor.parent;
        }

        return true;
    }

    private void replaceRecordModifications(IDNode rootSmall, IDNode rootLarge, E removedEdge, boolean replacementEdgeFound) {
        // /!\ small and large can be both in the main component (when a replacement edge was found)
        // However, in this case, we only need one of the two variables to be true to have
        // the correct behavior (i.e. only the removedEdge is marked as removed).
        // When there is no replacement edge, small and large cannot be simultaneously in the main
        // component as there are in two distinct component.
        boolean smallInMain = isInMainComponent(rootSmall);
        boolean largeInMain = !smallInMain && isInMainComponent(rootLarge); // avoid computing isInMainComponent if we know that small is in the main component

        if (smallInMain || largeInMain) {
            checkSavedContext().markEdgeRemoved(removedEdge);
        }

        if (!replacementEdgeFound) {
            if (largeInMain) {
                markAllRemoved(rootSmall);
            } else if (smallInMain) {
                markAllRemoved(rootLarge);
            }
        }
    }

    private void removeNonTreeEdgeRecordModifications(IDNode node, E edge) {
        if (isInMainComponent(node)) {
            checkSavedContext().markEdgeRemoved(edge);
        }
    }

    private void addRoot(IDNode node) {
        node.rootIndex = roots.size();
        roots.add(node);
    }

    private void removeRoot(IDNode node) {
        // update roots, swapping 'node' and the last element of roots
        IDNode last = roots.removeLast();
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
        IDNode mainComponentNode;
        boolean fictitious = false;
        if (defaultMainComponentVertex == null) {
            mainComponentNode = getBiggestRoot();
            fictitious = true;
        } else {
            mainComponentNode = getNodeOrThrow(defaultMainComponentVertex);
        }

        modificationsStack.push(new Modifications(mainComponentNode, fictitious, computeComparisons));
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
        IDNode node = getNodeOrThrow(vertex);
        sortTrees();

        return node.findRoot().rootIndex;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        if (!modificationsStack.isEmpty()) {
            Modifications modifications = modificationsStack.peek();
            modifications.setMainComponentVertex(mainComponentVertex);
        }
        defaultMainComponentVertex = mainComponentVertex;
    }

    private boolean isInMainComponent(IDNode node) {
        Modifications modifications = modificationsStack.peek();

        if (modifications == null) {
            return false;
        } else {
            return modifications.mainComponentNode.findRoot() == node.findRoot();
        }
    }

    private IDNode getBiggestRoot() {
        IDNode biggestRoot = roots.getFirst();

        for (int i = 1; i < roots.size(); i++) {
            IDNode root = roots.get(i);
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
        IDNode node = getNodeOrThrow(vertex);
        return node.findRoot().componentView();
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

    private void markAllAdded(IDNode root) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllAdded(root);
    }

    private void markAllRemoved(IDNode root) {
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
        checkNodes();
        checkRoots();
    }

    private void checkEdges() {
        for (Map.Entry<E, Edge> entry : edges.entrySet()) {
            E e = entry.getKey();
            Edge edge = entry.getValue();

            IDNode src = edge.nodeU;
            IDNode dest = edge.nodeV;

            assert vertexToTreeNode.containsValue(src);
            assert vertexToTreeNode.containsValue(dest);

            if (edge.treeEdge) {
                assert src.parent == dest && src.parentEdge == e || dest.parent == src && dest.parentEdge == e;
            } else {
                assert src.incidentEdges.contains(e);
                assert dest.incidentEdges.contains(e);
            }
        }
    }

    private void checkNodes() {
        for (IDNode node : vertexToTreeNode.values()) {
            DSNode dsNode = node.dsNode;
            assert dsNode != null;
            assert node.parent == null || node.parent.size > node.size;

            for (E e : node.incidentEdges) {
                Edge edge = edges.get(e);
                assert edge != null;

                assert edge.nodeU == node || edge.nodeV == node;
            }

            // detect cycle in the IDTree
            Set<IDNode> ancestorsID = new HashSet<>();
            while (node != null) {
                assert !ancestorsID.contains(node);
                ancestorsID.add(node);
                node = node.parent;
            }

            // detect cycle in the DSTree
            Set<DSNode> ancestorsDS = new HashSet<>();
            while (dsNode != null) {
                assert !ancestorsDS.contains(dsNode);
                ancestorsDS.add(dsNode);
                dsNode = dsNode.parent;
            }
        }
    }

    private void checkRoots() {
        for (int i = 0; i < roots.size(); i++) {
            IDNode root = roots.get(i);
            assert root.rootIndex == i;

            assert root.parent == null;
            assert root.dsNode.parent == null;

            // assert that the two trees contains the same nodes
            // first explore the DS tree
            Set<IDNode> visitedIDNodeInDSTree = new HashSet<>();
            Set<DSNode> visitedDSNodeInDSTree = new HashSet<>();

            DSNode ptr = root.dsNode;
            while (ptr != null) {
                assert !visitedIDNodeInDSTree.contains(ptr.idNode);
                visitedIDNodeInDSTree.add(ptr.idNode);
                assert !visitedDSNodeInDSTree.contains(ptr);
                visitedDSNodeInDSTree.add(ptr);

                if (ptr.firstChild != null) {
                    ptr = ptr.firstChild;
                } else if (ptr.nextSibling != null) {
                    ptr = ptr.nextSibling;
                } else {
                    while (ptr != null && ptr.nextSibling == null) {
                        ptr = ptr.parent;
                    }

                    if (ptr != null) {
                        ptr = ptr.nextSibling;
                    }
                }
            }

            // then explore the ID tree
            Set<IDNode> visitedIDNodeInIDTree = new HashSet<>();
            Set<DSNode> visitedDSNodeInIDTree = new HashSet<>();
            ArrayDeque<IDNode> queue = new ArrayDeque<>();
            queue.offer(root);

            while (!queue.isEmpty()) {
                IDNode idNode = queue.poll();
                assert !visitedIDNodeInIDTree.contains(idNode);
                visitedIDNodeInIDTree.add(idNode);
                assert !visitedDSNodeInIDTree.contains(idNode.dsNode);
                visitedDSNodeInIDTree.add(idNode.dsNode);

                for (E e : idNode.incidentEdges) {
                    Edge edge = edges.get(e);

                    if (edge.treeEdge) {
                        IDNode opposite = edge.opposite(idNode);

                        if (opposite.parent == idNode) {
                            queue.offer(opposite);
                        }
                    }
                }
            }

            assert visitedIDNodeInIDTree.equals(visitedIDNodeInDSTree);
            assert visitedDSNodeInIDTree.equals(visitedDSNodeInDSTree);
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
     *     <li>if the node is a root, its index in the list of {@link DnDTreeStandalone#roots}</li>
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
    private final class IDNode {

        private final V vertex;
        // the node in the disjoint-set tree holding the same vertex as this IDNode
        private DSNode dsNode = new DSNode(this);

        // the size of this subtree
        private int size;

        private IDNode parent = null;
        private E parentEdge = null;

        private final Set<E> incidentEdges = new HashSet<>();

        // index in the list of roots, valid only if this node is a root
        private int rootIndex;

        private ComponentView componentView = null;

        IDNode(V vertex) {
            this.vertex = vertex;
            this.size = 1;
        }

        /**
         * Make this node a child of {@code parent} whose tree is rooted at {@code parentRoot}.
         * @param parentRoot root of parent
         * @param parent node that will become the parent of {@code this}.
         * @param edge the edge linking {@code this} and {@code parent}
         */
        private IDNode linkWithParent(IDNode parentRoot, IDNode parent, E edge) {
            // first: update parent/child relations
            this.parent = parent;
            this.parentEdge = edge;

            // next: update size attributes in the parent tree
            IDNode newCentroid = null;
            IDNode cur = parent;

            while (cur != null) {
                cur.size += this.size;

                if (newCentroid == null && cur != parentRoot && cur.size > (parentRoot.size + this.size) / 2) {
                    // the new root is the first node in the path from parent to parentRoot
                    // such that it contains more than half of the nodes in the merged
                    // tree. This reduces the sum of distances.
                    newCentroid = cur;
                }

                cur = cur.parent;
            }

            // eventually, change the root to a better one
            if (newCentroid != null) {
                newCentroid.makeRootKeepConsistent();
                return newCentroid;
            } else {
                return parentRoot;
            }
        }

        private IDNode unlink() {
            Objects.requireNonNull(parent);

            // first step: update size attribute in the parent tree
            IDNode newTree = this;
            while (newTree.parent != null) {
                newTree = newTree.parent;
                newTree.size -= size;
            }

            // second step: update parent/child relations
            parent = null;
            parentEdge = null;
            return newTree;
        }

        private void makeRootKeepConsistent() {
            makeRoot(true);
            dsNode.makeRoot();
        }

        /**
         * Make this DTNode the root of the tree in which it is. This is a two steps process
         * with an intermediate optional operation:
         * <ol>
         *     <li>Swap parent-child relationship for each DTNode from this dTNode to the original root</li>
         *     <li>Optionally, update the list of roots. For most cases, it must be {@code true}</li>
         *     <li>Update the subtree size attribute from the original root to the new root (this DTNode)</li>
         * </ol>
         */
        private void makeRoot(boolean updateRoots) {
            if (parent == null) {
                return;
            }

            IDNode child = this;
            IDNode parent = child.parent;
            E parentEdge = child.parentEdge;

            this.parent = null;
            this.parentEdge = null;

            // swap parent/child relation
            while (parent != null) {
                IDNode greatParent = parent.parent;
                E greatParentEdge = parent.parentEdge;

                parent.parent = child;
                parent.parentEdge = parentEdge;

                // process to the next parent/child
                child = parent;
                parent = greatParent;
                parentEdge = greatParentEdge;
            }

            // child is the old root
            IDNode oldRoot = child;

            // update the list of roots
            if (updateRoots) {
                rootIndex = oldRoot.rootIndex;
                roots.set(rootIndex, IDNode.this);
            }

            // update size attributes, going from oldRoot to this DTNode
            while (oldRoot.parent != null) {
                oldRoot.size -= oldRoot.parent.size;
                oldRoot.parent.size += oldRoot.size;
                oldRoot = oldRoot.parent;
            }
        }

        private int depth() {
            IDNode node = this;
            int depth = 0;

            while (node.parent != null) {
                node = node.parent;
                depth++;
            }

            return depth;
        }

        private IDNode findRoot() {
            return dsNode.findRootDS().idNode;
        }

        // This DNode MUST be a root
        public Set<V> componentView() {
            if (componentView == null) {
                componentView = new ComponentView(this);
            }

            return componentView;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("IDTree: ");
            sb.append(vertex).append(" -te-> {");

            Set<Edge> nte = new HashSet<>();
            for (E e : incidentEdges) {
                Edge edge = edges.get(e);
                if (edge.treeEdge) {
                    IDNode opposite = edge.opposite(this);

                    if (opposite.parent == this) {
                        sb.append(opposite.vertex).append(", ");
                    }
                } else {
                    nte.add(edge);
                }
            }

            sb.append("} -nte-> {");
            for (Edge e : nte) {
                IDNode opposite = e.opposite(this);
                sb.append(opposite.vertex).append(", ");
            }
            sb.append("}    -    DSNode: ").append(dsNode);

            return sb.toString();
        }
    }

    private final class DSNode {

        private IDNode idNode;

        private DSNode parent = null;

        // the children of this node. They are stored in a doubly linked list
        // firstChild is the head of the linked list. previousSibling and nextSibling
        // are used to navigate the list.
        private DSNode firstChild = null;
        private DSNode previousSibling = null;
        private DSNode nextSibling = null;

        DSNode(IDNode idNode) {
            this.idNode = idNode;
        }

        private int size() {
            return idNode.size;
        }

        /**
         * Union of two trees in the disjoint-set tree.
         * It adds {@code child} in the doubly linked list of children
         * and then set the parent of {@code child} to {@code this}.
         * The child must be a root. No verification is performed
         *
         * @param child the child node to add
         */
        private void link(DSNode child) {
            DSNode oldFirstChild = this.firstChild;

            firstChild = child;
            child.nextSibling = oldFirstChild;

            if (oldFirstChild != null) {
                oldFirstChild.previousSibling = child;
            }

            child.parent = this;
        }

        /**
         * Disconnect this node from its parent in the disjoint-set tree.
         * It first removes itself from the doubly linked list of children
         * stored in its parent, then set its parent to {@code null}.
         * This node must have a parent. No verification is performed.
         */
        private void unlink() {
            DSNode prev = previousSibling;
            DSNode next = nextSibling;

            previousSibling = null;
            nextSibling = null;

            if (prev != null) {
                prev.nextSibling = next;
            } else {
                // if there is no 'prev' node, that means
                // that 'child' was the first child, and
                // we need to update it
                parent.firstChild = next;
            }
            if (next != null) {
                next.previousSibling = prev;
            }

            parent = null;
        }

        private DSNode findRootDS() {
            if (parent != null) {
                DSNode root = parent.findRootDS();
                unlink();
                root.link(this);
                return root;
            }
            return this;
        }

        private void isolate() {
            DSNode root = findRootDS();
            unlink();
            while (firstChild != null) {
                DSNode child = firstChild;
                child.unlink();
                root.link(child);
            }
        }

        private void makeRoot() {
            DSNode root = findRootDS();
            if (root != this) {
                IDNode thisIDNode = idNode;
                IDNode rootIDNode = root.idNode;

                root.idNode = thisIDNode;
                this.idNode = rootIDNode;

                rootIDNode.dsNode = this;
                thisIDNode.dsNode = root;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(idNode.vertex.toString()).append(" --> {");

            Set<V> set = new HashSet<>();

            DSNode child = firstChild;
            while (child != null) {
                if (!set.add(child.idNode.vertex)) {
                    sb.append("loop detected");
                    break;
                } else {
                    sb.append(child.idNode.vertex).append(", ");
                }
                child = child.nextSibling;
            }
            sb.append("}");

            return sb.toString();
        }
    }

    private final class ComponentView extends AbstractSetView<V> {

        private final IDNode node;

        ComponentView(IDNode node) {
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

                return rootOf((V) o) == node.findRoot();
            }

            return false;
        }

        @Override
        public int size() {
            return node.findRoot().size;
        }
    }

    private final class DFSIterator implements Iterator<V> {

        private DSNode cursor;
        private IDNode current;

        /**
         * Creates a new depth-first iterator starting at the specified root node
         * and returning node according to the pre-order.
         *
         * @param root the root of the tree to traverse. It must be a root otherwise,
         *             the iterator may visit nodes outside the subtree
         */
        DFSIterator(IDNode root) {
            cursor = root.dsNode;
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

            current = cursor.idNode;

            // Advances to the next node for the next iteration.
            // The iterator try to:
            // - descend one level whenever possible,
            // - otherwise, moves to the next sibling if any,
            // - otherwise, moves up until it finds a node with
            //   a next sibling (unvisited by construction) or
            //   the tree is fully visited.

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

        public IDNode node() {
            return current;
        }
    }

    private final class Edge {
        private final IDNode nodeU;
        private final IDNode nodeV;
        private boolean treeEdge;

        private Edge(IDNode nodeU, IDNode nodeV, boolean treeEdge) {
            this.nodeU = nodeU;
            this.nodeV = nodeV;
            this.treeEdge = treeEdge;
        }

        public IDNode opposite(IDNode node) {
            if (nodeU == node) {
                return nodeV;
            } else {
                return nodeU;
            }
        }

        @Override
        public String toString() {
            return "Edge{" +
                    "u=" + nodeU.vertex +
                    ", v=" + nodeV.vertex +
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
        private IDNode mainComponentNode;

        private boolean undoing = false;

        Modifications(IDNode mainComponentVertex, boolean fictitiousMCV, boolean computeComparisons) {
            this.mainComponentNode = mainComponentVertex;
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

            if (this.mainComponentNode.vertex != mainComponentVertex) {
                // two things to do:
                // 1. check if the new main component vertex was in the main component before temporary changes.
                // 2. if the main component vertex isn't in the current main component vertex, we need to
                //    update state of edges and vertices

                IDNode oldComponentRoot = this.mainComponentNode.findRoot();
                IDNode mainComponentNode = getNodeOrThrow(mainComponentVertex);
                IDNode newComponentRoot = mainComponentNode.findRoot();

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

                this.mainComponentNode = mainComponentNode;
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

        public void markAllAdded(IDNode root) {
            markAll(root, StateMap.State.ADDED);
        }

        public void markAllRemoved(IDNode root) {
            markAll(root, StateMap.State.REMOVED);
        }

        public void markAll(IDNode root, StateMap.State newState) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            for (DFSIterator it = new DFSIterator(root); it.hasNext();) {
                V vertex = it.next();
                verticesState.mark(vertex, newState);

                IDNode node = it.node();
                for (E nte : node.incidentEdges) {
                    if (getEdgeSource(nte).equals(vertex)) {
                        edgesState.mark(nte, newState);
                    }
                }
            }
        }

        public void notifyInsertTreeEdge(IDNode newTree) {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(newTree);
            }
        }

        public void notifyRemoveTreeEdge() {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(getBiggestRoot());
            }
        }

        private void maybeBiggestTreeChanged(IDNode currentBiggestRoot) {
            IDNode mainComponentVertexTree = mainComponentNode.findRoot();
            if (currentBiggestRoot.size > mainComponentVertexTree.size) {
                // there is a new biggest main component
                markAllRemoved(mainComponentVertexTree);
                markAllAdded(currentBiggestRoot);
                mainComponentNode = currentBiggestRoot;
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
