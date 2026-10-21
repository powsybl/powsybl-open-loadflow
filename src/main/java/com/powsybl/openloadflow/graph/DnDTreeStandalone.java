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
import com.powsybl.openloadflow.graph.dtreedyn.DTGraph;
import com.powsybl.openloadflow.graph.dtreedyn.StateMap;

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
    private Modifications currentModificationsContext;
    private V defaultMainComponentVertex;

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);

        if (tryAddVertex(vertex)) {
            // keep track of modifications
            Modifications modifications = modificationsStack.peek();

            if (modifications != null) {
                modifications.push(new VertexAdd<>(vertex));
            }
        }
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        Objects.requireNonNull(edge);

        if (tryAddEdge(vertex1, vertex2, edge)) {
            // keep track of modifications
            Modifications modifications = modificationsStack.peek();

            if (modifications != null) {
                modifications.push(new EdgeAdd<>(vertex1, vertex2, edge));
            }
        }
    }

    @Override
    public void removeEdge(E edge) {
        Objects.requireNonNull(edge);

        Edge e = tryRemoveEdge(edge);
        if (e != null) {
            // keep track of modifications
            Modifications modifications = modificationsStack.peek();

            if (modifications != null) {
                modifications.push(new EdgeRemove<>(e.nodeU.vertex, e.nodeV.vertex, e.edgeData));
            }
        }
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        modificationsStack.push(new Modifications(defaultMainComponentVertex, computeComparisons));
        currentModificationsContext = modificationsStack.peek();
    }

    @Override
    public void undoTemporaryChanges() {
        if (modificationsStack.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }

        Modifications modifications = modificationsStack.peek();
        currentModificationsContext = null;

        for (GraphModification<V, E> gm : modifications) {
            switch (gm) {
                case EdgeAdd<V, E> edgeAdd -> tryRemoveEdge(edgeAdd.e);
                case EdgeRemove<V, E> edgeRemove -> tryAddEdge(edgeRemove.v1, edgeRemove.v2, edgeRemove.e);
                case VertexAdd<V, E> vertexAdd -> tryRemoveVertex(vertexAdd.v());
                default -> throw new IllegalStateException("Unexpected value: " + gm);
            }
        }

        modificationsStack.pop();
        currentModificationsContext = modificationsStack.peek();
    }

    private Modifications checkSavedContext() {
        if (modificationsStack.isEmpty()) {
            throw new PowsyblException("Cannot compute connectivity without a saved state, please call GraphConnectivity::startTemporaryChanges at least once beforehand");
        }
        return modificationsStack.peek();
    }

    @Override
    public int getComponentNumber(V vertex) {
        return getConnectedComponent(vertex).getNumber();
    }

    @Override
    public boolean connected(V vertex1, V vertex2) {
        return rootOf(vertex1) == rootOf(vertex2);
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        if (!modificationsStack.isEmpty()) {
            Modifications modifications = modificationsStack.peek();
            modifications.setMainComponentVertex(mainComponentVertex);
        }
        defaultMainComponentVertex = mainComponentVertex;
    }

    private IDNode getBiggestRoot() {
        if (isSorted) {
            return roots.getFirst();
        } else {
            IDNode biggestRoot = roots.getFirst();

            for (int i = 1; i < roots.size(); i++) {
                IDNode root = roots.get(i);
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
    public Component<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        IDNode node = getNodeOrThrow(vertex);
        return node.findRoot().componentView();
    }

    @Override
    public Component<V> getLargestConnectedComponent() {
        checkSavedContext();
        return getBiggestRoot().componentView();
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

    @Override
    public long computeSumOfDistances() {
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

    // ===================
    // * GRAPH OPERATION *
    // ===================

    public boolean containsVertex(V vertex) {
        return vertexToTreeNode.containsKey(vertex);
    }

    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
    }

    private IDNode getNodeOrThrow(V v) {
        IDNode node = vertexToTreeNode.get(v);
        if (node == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return node;
    }

    private IDNode rootOf(V vertex) {
        return getNodeOrThrow(vertex).findRoot();
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

    private boolean tryAddVertex(V vertex) {
        if (containsVertex(vertex)) {
            return false;
        }

        IDNode newNode = new IDNode(vertex);
        vertexToTreeNode.put(vertex, newNode);
        addRoot(newNode);
        return true;
    }

    private boolean tryRemoveVertex(V v) {
        if (!containsVertex(v)) {
            return false;
        }

        IDNode node = vertexToTreeNode.get(v); // can't use Map#remove because removeEdge will update this DTNode
        for (Edge edge : new HashSet<>(node.incidentEdges)) {
            tryRemoveEdge(edge.edgeData);
        }

        IDNode root = vertexToTreeNode.remove(v);
        removeRoot(root);

        return true;
    }

    // =============
    // * INSERTION *
    // =============

    private boolean tryAddEdge(V vertex1, V vertex2, E e) {
        if (containsEdge(e)) {
            return false;
        }

        // invalidate roots ordering
        isSorted = false;

        // first update graph
        IDNode nodeU = getNodeOrThrow(vertex1);
        IDNode nodeV = getNodeOrThrow(vertex2);

        Edge edge = new Edge(nodeU, nodeV, e, false);
        edges.put(e, edge);

        // then the spanning tree and vertices and edges state
        IDNode rootU = nodeU.findRoot();
        IDNode rootV = nodeV.findRoot();

        if (rootU == rootV) {
            // insert non tree edge
            nodeU.incidentEdges.add(edge);
            nodeV.incidentEdges.add(edge);

            beforeInsertingEdgeInComponent(rootU, edge);
            edge.treeEdge = insertEdgeInComponent(rootU, nodeU, nodeV, edge);
        } else {
            // insert tree edge
            beforeInsertingEdgeBetweenComponents(rootU, rootV, edge);

            edge.treeEdge = true;
            nodeU.incidentEdges.add(edge);
            nodeV.incidentEdges.add(edge);

            IDNode mergedTree = insertEdgeBetweenComponents(rootU, nodeU, rootV, nodeV, edge);
            afterInsertingEdgeBetweenComponents(mergedTree);
        }

        check();
        return true;
    }

    private boolean insertEdgeInComponent(IDNode root, IDNode nodeU, IDNode nodeV, Edge edge) {
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

            ancestor.parentEdge.treeEdge = false;

            ancestor.unlink();
            deep.makeRoot(false);
            deep.linkWithParent(root, shallow, edge);
            return true;
        }
        return false;
    }

    private IDNode insertEdgeBetweenComponents(IDNode rootU, IDNode nodeU, IDNode rootV, IDNode nodeV, Edge edge) {
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

    private Edge tryRemoveEdge(E e) {
        Objects.requireNonNull(e);
        Edge edge = edges.remove(e);
        if (edge == null) {
            return null;
        }

        // invalidate roots ordering
        isSorted = false;

        // update graph
        edge.nodeU.incidentEdges.remove(edge);
        edge.nodeV.incidentEdges.remove(edge);

        // update the spanning tree
        if (edge.treeEdge) {
            removeTreeEdge(edge.nodeU, edge.nodeV, edge);
        } else {
            afterRemovingNonBreakingConnectivityEdge(edge.nodeU.findRoot(), edge);
        }

        check();
        return edge;
    }

    private void removeTreeEdge(IDNode nodeU, IDNode nodeV, Edge edge) {
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
    private void replace(IDNode rootSmall, IDNode rootLarge, Edge removedEdge) {
        boolean replacementEdgeFound = false;
        IDNode newRoot = null; // a potential new root in case no replacement edge is found

        ArrayDeque<IDNode> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        List<IDNode> visited = new ArrayList<>();
        visited.add(rootSmall);
        rootSmall.visited = true;

        loop:
        while (!queue.isEmpty()) {
            IDNode n = queue.poll();

            if (n != rootSmall && n.size > rootSmall.size / 2) {
                newRoot = n;
            }

            for (Edge nonTreeEdge : n.incidentEdges) {
                IDNode oppNode = nonTreeEdge.opposite(n);

                if (nonTreeEdge.treeEdge) {
                    if (n == oppNode.parent) {
                        if (!oppNode.visited) {
                            visited.add(oppNode);
                            oppNode.visited = true;
                        }
                        queue.offer(oppNode); // only go down
                    }
                } else if (allAncestorUnvisited(oppNode, visited)) {
                    // found a replacement edge
                    n.makeRoot(true);
                    IDNode mergedTreeRoot = n.linkWithParent(rootLarge, oppNode, nonTreeEdge);
                    mergedTreeRoot.dsNode.makeRoot();
                    removeRoot(n);
                    nonTreeEdge.treeEdge = true;
                    replacementEdgeFound = true;
                    afterRemovingNonBreakingConnectivityEdge(mergedTreeRoot, removedEdge);

                    break loop;
                }
            }
        }

        visited.forEach(n -> n.visited = false);

        if (!replacementEdgeFound) {
            rootLarge.dsNode.makeRoot();

            rootSmall.dsNode.isolate();
            for (int i = 1; i < visited.size(); i++) { // rootSmall is at index 0 and mustn't be linked with itself
                IDNode n = visited.get(i);
                n.dsNode.isolate();
                rootSmall.dsNode.link(n.dsNode);
            }

            if (newRoot != null) {
                newRoot.makeRootKeepConsistent();
                afterRemovingEdgeBreakingConnectivity(newRoot, rootLarge, removedEdge);
            } else {
                afterRemovingEdgeBreakingConnectivity(rootSmall, rootLarge, removedEdge);
            }
        }
    }

    private boolean allAncestorUnvisited(IDNode node, List<IDNode> visited) {
        IDNode ancestor = node;
        while (ancestor != null) {
            if (ancestor.visited) {
                return false;
            }
            visited.add(ancestor);
            ancestor.visited = true;
            ancestor = ancestor.parent;
        }

        return true;
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

    // =========================
    // * MODIFICATIONS SUPPORT *
    // =========================

    private void beforeInsertingEdgeInComponent(IDNode treeRoot, Edge edge) {
        if (currentModificationsContext != null) {
            currentModificationsContext.beforeInsertingEdgeInComponent(treeRoot, edge);
        }
    }

    private void beforeInsertingEdgeBetweenComponents(IDNode rootU, IDNode rootV, Edge edge) {
        if (currentModificationsContext != null) {
            currentModificationsContext.beforeInsertingEdgeBetweenComponents(rootU, rootV, edge);
        }
    }

    private void afterInsertingEdgeBetweenComponents(IDNode mergedTree) {
        if (currentModificationsContext != null) {
            currentModificationsContext.afterInsertingEdgeBetweenComponents(mergedTree);
        }
    }

    private void afterRemovingNonBreakingConnectivityEdge(IDNode treeRoot, Edge edge) {
        if (currentModificationsContext != null) {
            currentModificationsContext.afterRemovingNonBreakingConnectivityEdge(treeRoot, edge);
        }
    }

    private void afterRemovingEdgeBreakingConnectivity(IDNode smallRoot, IDNode largeRoot, Edge edge) {
        if (currentModificationsContext != null) {
            currentModificationsContext.afterRemovingEdgeBreakingConnectivity(smallRoot, largeRoot, edge);
        }
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
            assert edge.edgeData == e;

            IDNode src = edge.nodeU;
            IDNode dest = edge.nodeV;

            assert vertexToTreeNode.containsValue(src);
            assert vertexToTreeNode.containsValue(dest);

            if (edge.treeEdge) {
                assert src.parent == dest && src.parentEdge == edge || dest.parent == src && dest.parentEdge == edge;
            } else {
                assert src.incidentEdges.contains(edge);
                assert dest.incidentEdges.contains(edge);
            }
        }
    }

    private void checkNodes() {
        for (IDNode node : vertexToTreeNode.values()) {
            DSNode dsNode = node.dsNode;
            assert dsNode != null;
            assert node.parent == null || node.parent.size > node.size;

            for (Edge edge : node.incidentEdges) {
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

                for (Edge edge : idNode.incidentEdges) {
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
        private Edge parentEdge = null;

        private final Set<Edge> incidentEdges = new HashSet<>();

        // index in the list of roots, valid only if this node is a root
        private int rootIndex;

        private ComponentView componentView = null;

        private boolean visited = false;

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
        private IDNode linkWithParent(IDNode parentRoot, IDNode parent, Edge edge) {
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
            Edge parentEdge = child.parentEdge;

            this.parent = null;
            this.parentEdge = null;

            // swap parent/child relation
            while (parent != null) {
                IDNode greatParent = parent.parent;
                Edge greatParentEdge = parent.parentEdge;

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

        public Component<V> componentView() {
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
            for (Edge edge : incidentEdges) {
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

    private final class ComponentView extends AbstractSetView<V> implements Component<V> {

        private final IDNode node;

        ComponentView(IDNode node) {
            this.node = node;
        }

        @Override
        public Iterator<V> iterator() {
            return new DFSIterator(getRoot());
        }

        @Override
        public boolean contains(Object o) {
            if (o != null) {
                // node might not be the root anymore, so need to use findRoot on node.
                // However, don't use findRootOptReroot on node, it might change the root
                // after we got the root of 'o'.

                return rootOf((V) o) == getRoot();
            }

            return false;
        }

        @Override
        public int size() {
            return getRoot().size;
        }

        @Override
        public int getNumber() {
            sortTrees();
            return getRoot().rootIndex;
        }

        @Override
        public Set<V> toOwnedSet() {
            return new HashSet<>(this);
        }

        IDNode getRoot() {
            return node.findRoot();
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
        private final E edgeData;
        private boolean treeEdge;

        private Edge(IDNode nodeU, IDNode nodeV, E edgeData, boolean treeEdge) {
            this.nodeU = nodeU;
            this.nodeV = nodeV;
            this.edgeData = edgeData;
            this.treeEdge = treeEdge;
        }

        public IDNode opposite(IDNode node) {
            if (nodeU == node) {
                return nodeV;
            } else {
                return nodeU;
            }
        }

        public boolean isTreeEdge() {
            return treeEdge;
        }

        @Override
        public String toString() {
            return "Edge{" +
                    "nodeU=" + nodeU +
                    ", nodeV=" + nodeV +
                    ", edgeData=" + edgeData +
                    ", treeEdge=" + treeEdge +
                    '}';
        }
    }

    /**
     * Contains modifications performed on the {@link DTGraph} between
     * the last call to {@link GraphConnectivity#startTemporaryChanges}
     * and the current instant. It stores a stack of {@link GraphModification}
     * and optionally the set of vertices and edges added to the
     * main component or removed from it.
     *
     * <p>
     * Topological comparisons are always computed relative to some main component
     * vertex, even when the user didn't specify one. When an edge is added in (or
     * removed from) a {@link DTGraph}, a DFS is performed to compute every vertex
     * that are now connected to (disconnected from) the main component vertex. It
     * is the responsibility of {@link DTGraph} to call the appropriate methods from
     * this class.
     * </p>
     *
     * <p>
     * Specifying a main component vertex is optional. In this case the main component
     * vertex is a vertex in the biggest component and is considered as fictitious.
     * It may lose its fictitious status when {@link #setMainComponentVertex(Object)}
     * is called. Over the time, this vertex may automatically change to another one
     * as the biggest main component can change.
     * </p>
     *
     * @see StateMap
     */
    private final class Modifications implements Iterable<GraphModification<V, E>> {

        private final Deque<GraphModification<V, E>> modificationsStack = new ArrayDeque<>();
        private final StateMap<V> verticesState;
        private final StateMap<E> edgesState;

        // true when the user didn't set the main component vertex
        // in this case, we set the main component vertex as a node
        // in the biggest component to avoid mainComponentVertex being
        // null and keep this class functional. However, it has an
        // impact on how edges/vertices removed from/added to are computed
        private boolean isMainComponentVertexFictitious;
        private IDNode mainComponentNode;

        Modifications(V mainComponentVertex, boolean computeComparisons) {
            if (mainComponentVertex == null) {
                mainComponentNode = getBiggestRoot();
                isMainComponentVertexFictitious = true;
            } else {
                mainComponentNode = getNodeOrThrow(mainComponentVertex);
                isMainComponentVertexFictitious = false;
            }

            if (computeComparisons) {
                verticesState = new StateMap<>();
                edgesState = new StateMap<>();
            } else {
                verticesState = null;
                edgesState = null;
            }
        }

        public void push(GraphModification<V, E> modification) {
            modificationsStack.push(modification);
        }

        public void beforeInsertingEdgeInComponent(IDNode treeRoot, Edge edge) {
            if (topologicalComparisonsDisabled()) {
                return;
            }

            if (isInMainComponent(treeRoot)) {
                edgesState.markAsAdded(edge.edgeData);
            }
        }

        public void beforeInsertingEdgeBetweenComponents(IDNode rootU, IDNode rootV, Edge edge) {
            if (topologicalComparisonsDisabled()) {
                return;
            }

            if (isInMainComponent(rootV)) {
                edgesState.markAsAdded(edge.edgeData);
                markAll(rootU, StateMap.State.ADDED);
            } else if (isInMainComponent(rootU)) {
                edgesState.markAsAdded(edge.edgeData);
                markAll(rootV, StateMap.State.ADDED);
            }
        }

        public void afterInsertingEdgeBetweenComponents(IDNode mergedTree) {
            if (topologicalComparisonsDisabled()) {
                return;
            }

            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(mergedTree);
            }
        }

        public void afterRemovingNonBreakingConnectivityEdge(IDNode treeRoot, Edge edge) {
            if (topologicalComparisonsDisabled()) {
                return;
            }

            if (isInMainComponent(treeRoot)) {
                edgesState.markAsRemoved(edge.edgeData);
            }
        }

        public void afterRemovingEdgeBreakingConnectivity(IDNode smallRoot, IDNode largeRoot, Edge edge) {
            if (topologicalComparisonsDisabled()) {
                return;
            }

            // compute the main component root once to answer two connectivity queries faster.
            IDNode mainComponentRoot = mainComponentNode.findRoot();
            boolean smallInMainComponent = mainComponentRoot == smallRoot;
            boolean largeInMainComponent = mainComponentRoot == largeRoot;

            if (smallInMainComponent || largeInMainComponent) {
                edgesState.markAsRemoved(edge.edgeData);
            }

            if (largeInMainComponent) {
                markAll(smallRoot, StateMap.State.REMOVED);
            } else if (smallInMainComponent) {
                markAll(largeRoot, StateMap.State.REMOVED);
            }

            // handle unspecified main component vertex
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(getBiggestRoot());
            }
        }

        /**
         * Change the main component vertex to the specified one.
         *
         * @param mainComponentVertex new vertex identifying the main component.
         */
        public void setMainComponentVertex(V mainComponentVertex) {
            if (topologicalComparisonsDisabled()) {
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
                    if (verticesState.getState(mainComponentVertex) != StateMap.State.REMOVED) {
                        throw new PowsyblException("Cannot take the given vertex as main component vertex! This vertex was outside the main component before starting temporary changes");
                    }

                    // last thing to do is update state of vertices and edges in the two trees.
                    markAll(oldComponentRoot, StateMap.State.REMOVED);
                    markAll(newComponentRoot, StateMap.State.ADDED);
                }

                this.mainComponentNode = mainComponentNode;
            }

            isMainComponentVertexFictitious = false;
        }

        private boolean isInMainComponent(IDNode node) {
            return mainComponentNode.findRoot() == node.findRoot();
        }

        private void markAll(IDNode root, StateMap.State newState) {
            for (DFSIterator it = new DFSIterator(root); it.hasNext();) {
                V vertex = it.next();
                verticesState.updateState(vertex, newState);

                IDNode node = it.node();
                for (Edge nte : node.incidentEdges) {
                    if (nte.nodeU == node) {
                        edgesState.updateState(nte.edgeData, newState);
                    }
                }
            }
        }

        private void maybeBiggestTreeChanged(IDNode currentBiggestRoot) {
            IDNode mainComponentVertexTree = mainComponentNode.findRoot();
            if (currentBiggestRoot.size > mainComponentVertexTree.size) {
                // there is a new biggest main component
                markAll(mainComponentVertexTree, StateMap.State.REMOVED);
                markAll(currentBiggestRoot, StateMap.State.ADDED);
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

        private boolean topologicalComparisonsDisabled() {
            return verticesState == null || edgesState == null;
        }

        @Override
        public Iterator<GraphModification<V, E>> iterator() {
            return modificationsStack.iterator();
        }
    }
}
