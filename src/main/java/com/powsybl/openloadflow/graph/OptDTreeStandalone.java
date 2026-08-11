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

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class OptDTreeStandalone<V, E> implements SpanningForestGraphConnectivity<V, E> {

    public static boolean debug = false;

    private final Map<V, DTNode> vertexToTreeNode = new HashMap<>();
    private final Map<E, Edge> edges = new HashMap<>();

    private final List<DTNode> roots = new ArrayList<>();
    private boolean isSorted = true;

    private final Deque<Modifications> modificationsStack = new ArrayDeque<>();
    private V defaultMainComponentVertex;

    private long counter = 1;

    public boolean containsVertex(V vertex) {
        return vertexToTreeNode.containsKey(vertex);
    }

    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
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

        for (DTNode root : roots) {
            sum += root.sumOfDistance();
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

        // remove non tree edges
        while (!node.incidentEdges.isEmpty()) {
            // this is weird, but removeEdge will modify node.nonTreeEdges, invalidating the iterator...
            Edge edge = node.incidentEdges.iterator().next();
            removeEdge(edge.edgeData);
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

        DTNode nodeU = getNodeOrThrow(vertex1);
        DTNode nodeV = getNodeOrThrow(vertex2);

        // update edges
        Edge e = new Edge(nodeU, nodeV, edge, false);
        edges.put(edge, e);

        // update the spanning trees
        Pair<DTNode, Integer> rootUdepth = nodeU.findRootWithDepth();
        Pair<DTNode, Integer> rootVdepth = nodeV.findRootWithDepth();

        DTNode rootU = rootUdepth.getKey();
        DTNode rootV = rootVdepth.getKey();

        DTNode mergedTree = null; // in case two trees were merged
        if (rootU == rootV) {
            // insert non tree edge
            insertNonTreeEdgeRecordModifications(rootU, edge);
            insertNonTreeEdge(rootU, nodeU, rootUdepth.getValue(), nodeV, rootVdepth.getValue(), e);
        } else {
            // insert tree edge
            insertTreeEdgeRecordModifications(rootU, rootV, edge);
            mergedTree = insertTreeEdge(rootU, nodeU, rootV, nodeV, e, true);
        }

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

    private void insertNonTreeEdgeRecordModifications(DTNode root, E edge) {
        if (isInMainComponent(root)) {
            checkSavedContext().markEdgeAdded(edge);
        }
    }

    private void insertNonTreeEdge(DTNode root, DTNode nodeU, int depthU, DTNode nodeV, int depthV, Edge edge) {
        nodeU.incidentEdges.add(edge);
        nodeV.incidentEdges.add(edge);
        int delta = Math.abs(depthU - depthV);

        if (delta >= 2) {
            findBestRoot(root);
        }
    }

    private void insertTreeEdgeRecordModifications(DTNode rootU, DTNode rootV, E edge) {
        if (isInMainComponent(rootV)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootU);
        } else if (isInMainComponent(rootU)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(rootV);
        }
    }

    private DTNode insertTreeEdge(DTNode rootU, DTNode nodeU, DTNode rootV, DTNode nodeV, Edge edge, boolean findBestRoot) {
        nodeU.incidentEdges.add(edge);
        nodeV.incidentEdges.add(edge);

        edge.treeEdge = true;
        if (rootU.size < rootV.size) {
            nodeU.makeRoot(true);
            removeRoot(nodeU);
            nodeU.link(nodeV, edge);
            return findBestRoot ? findBestRoot(rootV) : rootV;
        } else {
            nodeV.makeRoot(true);
            removeRoot(nodeV);
            nodeV.link(nodeU, edge);
            return findBestRoot ? findBestRoot(rootU) : rootU;
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
        if (e.treeEdge) {
            removeTreeEdge(e);
        } else {
            removeNonTreeEdgeRecordModifications(e.nodeU, edge);
            removeIncidentEdge(e);
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

    /**
     * Remove the tree edge between {@code nodeU} and {@code nodeV}.
     * Assuming nodeU is a child of nodeV, this is a two steps process :
     * <ol>
     *     <li>Unlink nodeU from nodeV. This creates two trees with a smaller one called {@code small},</li>
     *     <li>Search for a replacement edge and a potential new centroid by iterating over {@code small}.</li>
     *     <ul>
     *         <li>if one is found, it is a non-tree edge so it is removed and then added as a tree edge</li>
     *         <li>if none is found, fix the centroid property</li>
     *     </ul>
     * </ol>
     *
     * @param edge the tree edge to remove
     */
    private void removeTreeEdge(Edge edge) {
        removeIncidentEdge(edge);

        DTNode child;
        if (edge.nodeU == edge.nodeV.parent) {
            child = edge.nodeV;
        } else {
            child = edge.nodeU;
        }

        // unlink child from its parent
        DTNode otherTree = child.unlink();
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

        // try to reconnect them
        replace(small, large, edge.edgeData);
    }

    // search a replacement edge by doing a BFS over the smaller tree between rootSmall and rooLarge
    @SuppressWarnings("checkstyle:ParameterAssignment")
    private void replace(DTNode rootSmall, DTNode rootLarge, E removedEdge) {
        boolean replacementEdgeFound = false;

        ArrayDeque<DTNode> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        loop:
        while (!queue.isEmpty()) {
            DTNode n = queue.poll();

            // search for a replacement edge
            for (Edge incidentEdge : n.incidentEdges) {
                if (incidentEdge.treeEdge) {
                    continue;
                }

                DTNode oppNode = incidentEdge.opposite(n);
                DTNode oppRoot = oppNode.findRoot();

                if (oppRoot != rootSmall) {
                    // found a replacement edge
                    removeIncidentEdge(incidentEdge);
                    insertTreeEdge(rootSmall, n, oppRoot, oppNode, incidentEdge, false);
                    incidentEdge.treeEdge = true;
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

        if (replacementEdgeFound) {
            rootLarge = findBestRoot(rootLarge.findRoot());
        } else {
            rootLarge = findBestRoot(rootLarge);
            rootSmall = findBestRoot(rootSmall);
        }

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

    private void removeNonTreeEdgeRecordModifications(DTNode node, E edge) {
        if (isInMainComponent(node)) {
            checkSavedContext().markEdgeRemoved(edge);
        }
    }

    private void removeIncidentEdge(Edge edge) {
        edge.nodeU.incidentEdges.remove(edge);
        edge.nodeV.incidentEdges.remove(edge);
    }

    private DTNode findBestRoot(DTNode root) {
        DTNode best = root;
        int min = Integer.MAX_VALUE;

        // this doesn't necessarily find the root whose BFS tree minimizes Sd
        // it can be enhanced by doing a local search, that is, iterate over
        // centroid candidate, choose the best one, and restart until no
        // amelioration is found. However, it is slower and the improvement is small.
        // Ensuring root is a root of a BFS tree can also help.
        for (CentroidIterator it = new CentroidIterator(root); it.hasNext();) {
            DTNode node = it.next();
            int sd = node.sumOfDistanceIfRootedAndBFSTree();
            if (sd < min) {
                min = sd;
                best = node;
            }
        }

        best.makeBFSTree(true);
        return best;
    }

    private void unVisitAll() {
        counter++;
    }

    // ======================
    // * TREE MANIPULATIONS *
    // ======================

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
    public void startTemporaryChanges(boolean computeComparisons) {
        DTNode mainComponentNode;
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
        DTNode node = getNodeOrThrow(vertex);
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

    private boolean isInMainComponent(DTNode node) {
        Modifications modifications = modificationsStack.peek();

        if (modifications == null) {
            return false;
        } else {
            return modifications.mainComponentNode.findRoot() == node.findRoot();
        }
    }

    private DTNode getBiggestRoot() {
        DTNode biggestRoot = roots.getFirst();

        for (int i = 1; i < roots.size(); i++) {
            DTNode root = roots.get(i);
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
        DTNode node = getNodeOrThrow(vertex);
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
        checkParentChildRelation();
        checkSize();
        checkTrees();
    }

    private void checkEdges() {
        for (Map.Entry<E, Edge> entry : edges.entrySet()) {
            E e = entry.getKey();
            Edge edge = entry.getValue();

            DTNode src = edge.nodeU;
            DTNode dest = edge.nodeV;
            assert vertexToTreeNode.containsValue(src) && vertexToTreeNode.containsValue(dest);
            assert src.incidentEdges.contains(edge);
            assert dest.incidentEdges.contains(edge);

            if (edge.treeEdge) {
                assert src.parent == dest && src.parentEdge == edge || dest.parent == src && dest.parentEdge == edge;
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

    private void checkSize() {
        for (DTNode node : vertexToTreeNode.values()) {
            int expectedSize = 1;
            DTNode child = node.firstChild;
            while (child != null) {
                expectedSize += child.size;
                child = child.nextSibling;
            }

            assert node.size == expectedSize;
        }
    }

    private void checkTrees() {
        for (DTNode root : roots) {
            for (DFSIterator it = new DFSIterator(root); it.hasNext();) {
                V vertex = it.next();
                assert vertexToTreeNode.get(vertex) == it.node();
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
     *     <li>if the node is a root, its index in the list of {@link OptDTreeStandalone#roots}</li>
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
        private int depth;
        private long visited = OptDTreeStandalone.this.counter - 1;

        private DTNode parent = null;
        private Edge parentEdge = null;

        // the children of this node. They are stored in a doubly linked list
        // firstChild is the head of the linked list. previousSibling and nextSibling
        // are used to navigate the list.
        private DTNode firstChild = null;
        private DTNode previousSibling = null;
        private DTNode nextSibling = null;

        private final Set<Edge> incidentEdges = new HashSet<>();

        // index in the list of roots, valid only if this node is a root
        private int rootIndex;

        private ComponentView componentView = null;

        DTNode(V vertex) {
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

            DTNode child = this;
            DTNode parent = child.parent;
            Edge parentEdge = child.parentEdge;
            parent.removeChildUnchecked(child); // remove before making parentEdge null

            this.parent = null;
            this.parentEdge = null;

            // swap parent/child relation
            while (parent != null) {
                DTNode greatParent = parent.parent;
                Edge greatParentEdge = parent.parentEdge;

                // At this point:
                // - 'parent' is in the linked list of children of 'greatParent', and must be
                //   removed from it because adding 'parent' as a child of 'child' will break
                //   this linked list.
                // - 'child' is NOT in the linked list of children of 'parent'.
                //   It was removed by the last iteration or before entering in the loop (for the first iteration)
                // - the parent of 'parent' aka 'greatParent' should be changed to child
                if (greatParent != null) {
                    greatParent.removeChildUnchecked(parent);
                }

                child.addChildUnchecked(parent);
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
            DTNode oldRoot = child;

            // update the list of roots
            if (updateRoots) {
                rootIndex = oldRoot.rootIndex;
                roots.set(rootIndex, DTNode.this);
            }

            // update size attributes, going from oldRoot to this DTNode
            while (oldRoot.parent != null) {
                oldRoot.size -= oldRoot.parent.size;
                oldRoot.parent.size += oldRoot.size;
                oldRoot = oldRoot.parent;
            }
        }

        private void makeBFSTree(boolean updateRoots) {
            makeRoot(updateRoots);

            Queue<DTNode> queue = new ArrayDeque<>();
            queue.offer(this);

            unVisitAll();
            this.setVisited();

            while (!queue.isEmpty()) {
                DTNode node = queue.poll();
                node.size = 1;

                for (Edge incidentEdge : node.incidentEdges) {
                    // path to child in the tree is already the fastest path
                    // so we only need to check non tree edges
                    if (incidentEdge.treeEdge) {
                        continue;
                    }

                    DTNode dest = incidentEdge.opposite(node);
                    if (dest.setVisited()) {
                        dest.replaceParentLinkByNTE();
                        dest.replaceNTEByTE(node, incidentEdge);
                        incidentEdge.treeEdge = true;
                    }
                }

                DTNode child = node.firstChild;
                while (child != null) {
                    queue.add(child);
                    child.setVisited();
                    child = child.nextSibling;
                }
            }

            recomputeSize();
        }

        private void recomputeSize() {
            DTNode ptr = this;

            while (ptr != null) {
                if (ptr.firstChild != null) {
                    ptr = ptr.firstChild;
                } else if (ptr.nextSibling != null) {
                    if (ptr.parent != null) {
                        ptr.parent.size += ptr.size;
                    }

                    ptr = ptr.nextSibling;
                } else {
                    while (ptr != null && ptr.nextSibling == null) {
                        if (ptr.parent != null) {
                            ptr.parent.size += ptr.size;
                        }

                        ptr = ptr.parent;
                    }

                    if (ptr != null) {
                        if (ptr.parent != null) {
                            ptr.parent.size += ptr.size;
                        }

                        ptr = ptr.nextSibling;
                    }
                }
            }
        }

        private int sumOfDistanceIfRootedAndBFSTree() {
            ArrayDeque<DTNode> queue = new ArrayDeque<>();
            queue.offer(this);

            unVisitAll();
            this.setVisited();

            this.depth = 0;

            int sum = 0;

            while (!queue.isEmpty()) {
                DTNode node = queue.poll();
                sum += node.depth;

                for (Edge edge : node.incidentEdges) {
                    DTNode dest = edge.opposite(node);

                    if (dest.setVisited()) {
                        dest.depth = node.depth + 1;
                        queue.offer(dest);
                    }
                }
            }

            return sum;
        }

        private boolean setVisited() {
            boolean notVisited = visited != OptDTreeStandalone.this.counter;
            visited = OptDTreeStandalone.this.counter;
            return notVisited;
        }

        private boolean isVisited() {
            return visited == OptDTreeStandalone.this.counter;
        }

        private int sumOfDistance() {
            int sum = 0;
            int currentDepth = 0;

            DTNode ptr = this;
            while (ptr != null) {
                if (ptr.firstChild != null) {
                    ptr = ptr.firstChild;
                    currentDepth++;
                    // go deeper
                } else if (ptr.nextSibling != null) {
                    ptr = ptr.nextSibling;
                    // same height
                } else {
                    while (ptr != null && ptr.nextSibling == null) {
                        ptr = ptr.parent;
                        currentDepth--;
                        // go up
                    }

                    if (ptr != null) {
                        ptr = ptr.nextSibling;
                    }
                }

                if (ptr != null) {
                    sum += currentDepth; // update sum
                }
            }

            return sum;
        }

        private void link(DTNode parent, Edge edge) {
            // first: update parent/child relations
            parent.addChildUnchecked(this);
            this.parent = parent;
            this.parentEdge = edge;

            // next: update size attributes in the parent tree
            DTNode cur = parent;

            while (cur != null) {
                cur.size += this.size;
                cur = cur.parent;
            }
        }

        /**
         * Unlink this node from its parent, creating two trees, one
         * whose root is {@code this} and one whose root is returned and
         * was previously the root of the linked tree.
         *
         * @return the root of the other tree
         */
        private DTNode unlink() {
            Objects.requireNonNull(parent);

            // first step: update size attribute in the parent tree
            DTNode newTree = this;
            while (newTree.parent != null) {
                newTree = newTree.parent;
                newTree.size -= size;
            }

            // second step: update parent/child relations
            parent.removeChildUnchecked(this);
            parent = null;
            parentEdge = null;
            return newTree;
        }

        private void replaceNTEByTE(DTNode parent, Edge edge) {
            parent.addChildUnchecked(this);
            this.parent = parent;
            this.parentEdge = edge;
            parentEdge.treeEdge = true;
        }

        private void replaceParentLinkByNTE() {
            parentEdge.treeEdge = false;
            parent.removeChildUnchecked(this);
            parent = null;
            parentEdge = null;
        }

        /**
         * Add child in the doubly linked list of children.
         * The child mustn't be in a linked list. No verification
         * is performed.
         *
         * @param child the child node to add
         */
        private void addChildUnchecked(DTNode child) {
            DTNode oldFirstChild = this.firstChild;

            firstChild = child;
            child.nextSibling = oldFirstChild;

            if (oldFirstChild != null) {
                oldFirstChild.previousSibling = child;
            }
        }

        /**
         * Remove child from the doubly linked list of children.
         * The child must be in the linked list. No verification
         * is performed.
         *
         * @param child the child node to remove
         */
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

            for (Edge edge : incidentEdges) {
                if (edge.treeEdge) {
                    continue;
                }

                if (edge.nodeU.vertex.equals(vertex)) {
                    sb.append(edge.nodeV.vertex).append(", ");
                } else if (edge.nodeV.vertex.equals(vertex)) {
                    sb.append(edge.nodeU.vertex).append(", ");
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
            return new DFSIterator(node.findRoot());
        }

        @Override
        public boolean contains(Object o) {
            if (o != null) {
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

        public DTNode node() {
            return current;
        }
    }

    private final class CentroidIterator implements Iterator<DTNode> {

        private final DTNode root;
        private DTNode cursor;

        private CentroidIterator(DTNode root) {
            this.root = root;
            this.cursor = root;
        }

        @Override
        public boolean hasNext() {
            return cursor != null;
        }

        @Override
        public DTNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            DTNode next = cursor;

            do {
                if (cursor.firstChild != null && isCentroidCandidate(cursor)) { // only go deeper if the *current* node is a centroid, some children may be a centroid
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
            } while (cursor != null && !isCentroidCandidate(cursor));

            return next;
        }

        private boolean isCentroidCandidate(DTNode node) {
            return node.size >= root.size / 2;
        }
    }

    private final class Edge {
        private final DTNode nodeU;
        private final DTNode nodeV;
        private final E edgeData;
        private boolean treeEdge;

        private Edge(DTNode nodeU, DTNode nodeV, E edgeData, boolean treeEdge) {
            this.nodeU = nodeU;
            this.nodeV = nodeV;
            this.edgeData = edgeData;
            this.treeEdge = treeEdge;
        }

        public DTNode opposite(DTNode node) {
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
        private DTNode mainComponentNode;

        private boolean undoing = false;

        Modifications(DTNode mainComponentVertex, boolean fictitiousMCV, boolean computeComparisons) {
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

                DTNode oldComponentRoot = this.mainComponentNode.findRoot();
                DTNode mainComponentNode = getNodeOrThrow(mainComponentVertex);
                DTNode newComponentRoot = mainComponentNode.findRoot();

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

        public void markAllAdded(DTNode root) {
            markAll(root, StateMap.State.ADDED);
        }

        public void markAllRemoved(DTNode root) {
            markAll(root, StateMap.State.REMOVED);
        }

        public void markAll(DTNode root, StateMap.State newState) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            for (DFSIterator it = new DFSIterator(root); it.hasNext();) {
                V vertex = it.next();
                verticesState.mark(vertex, newState);

                DTNode node = it.node();
                for (Edge nte : node.incidentEdges) {
                    if (nte.nodeU == it.node()) { // only if current node is edge source
                        edgesState.mark(nte.edgeData, newState);
                    }
                }

                // we don't mark child tree edges as removed
                // because for each child tree edge, there is a parentEdge
                // so if we mark a parent edge as removed, we also mark
                // the corresponding child tree edge as removed
            }
        }

        public void notifyInsertTreeEdge(DTNode newTree) {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(newTree);
            }
        }

        public void notifyRemoveTreeEdge() {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(getBiggestRoot());
            }
        }

        private void maybeBiggestTreeChanged(DTNode currentBiggestRoot) {
            DTNode mainComponentVertexTree = mainComponentNode.findRoot();
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
