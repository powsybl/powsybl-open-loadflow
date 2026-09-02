/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.graph.StateMap.State;
import com.powsybl.openloadflow.graph.dtree.AbstractSetView;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeStandalone<V, E> implements SpanningForestGraphConnectivity<V, E> {

    public static boolean debug = false;

    private final Map<V, DTNode<V, E>> vertexToTreeNode = new HashMap<>();
    private final Map<E, Edge<V, E>> edges = new HashMap<>();

    private final List<DTNode<V, E>> roots = new ArrayList<>();
    private boolean isSorted = true;

    private final Deque<Modifications> modificationsStack = new ArrayDeque<>();
    private V defaultMainComponentVertex;

    public boolean containsVertex(V vertex) {
        return vertexToTreeNode.containsKey(vertex);
    }

    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
    }

    private DTNode<V, E> getNodeOrThrow(V v) {
        DTNode<V, E> node = vertexToTreeNode.get(v);
        if (node == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return node;
    }

    private DTNode<V, E> rootOf(V vertex) {
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

        for (DTNode<V, E> root : roots) {
            int currentDepth = 0;

            DTNode<V, E> ptr = root;
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

        DTNode<V, E> newNode = new DTNode<>(this, vertex);
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

        DTNode<V, E> node = vertexToTreeNode.get(v); // can't use Map#remove because removeEdge will update this DTNode

        // remove non tree edges
        while (!node.nonTreeEdges.isEmpty()) {
            // this is weird, but removeEdge will modify node.nonTreeEdges, invalidating the iterator...
            Edge<V, E> edge = node.nonTreeEdges.iterator().next();
            removeEdge(edge.edgeData);
        }

        // remove child tree edges
        while (node.firstChild != null) {
            removeEdge(node.firstChild.parentEdge.edgeData);
        }

        // remove parent tree edge
        if (node.parentEdge != null) {
            removeEdge(node.parentEdge.edgeData);
        }

        DTNode<V, E> root = vertexToTreeNode.remove(v);
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

        DTNode<V, E> nodeU = getNodeOrThrow(vertex1);
        DTNode<V, E> nodeV = getNodeOrThrow(vertex2);

        // update edges
        Edge<V, E> e = new Edge<>(nodeU, nodeV, edge);
        edges.put(edge, e);

        // update the spanning trees
        Pair<DTNode<V, E>, Integer> rootUdepth = nodeU.findRootWithDepth();
        Pair<DTNode<V, E>, Integer> rootVdepth = nodeV.findRootWithDepth();

        DTNode<V, E> rootU = rootUdepth.getKey();
        DTNode<V, E> rootV = rootVdepth.getKey();

        DTNode<V, E> mergedTree = null; // in case two trees were merged
        if (rootU == rootV) {
            // insert non tree edge
            insertNonTreeEdgeRecordModifications(rootU, edge);
            insertNonTreeEdge(rootU, nodeU, rootUdepth.getValue(), nodeV, rootVdepth.getValue(), e);
        } else {
            // insert tree edge
            insertTreeEdgeRecordModifications(rootU, rootV, edge);
            mergedTree = insertTreeEdge(rootU, nodeU, rootV, nodeV, e);
        }

        // keep track of modifications
        Modifications modifications = modificationsStack.peek();
        if (modifications != null) {
            if (mergedTree != null) {
                modifications.notifyConnection(mergedTree);
            }

            modifications.push(new EdgeAdd<>(vertex1, vertex2, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        check();
    }

    private void insertNonTreeEdgeRecordModifications(DTNode<V, E> root, E edge) {
        Modifications modifications = modificationsStack.peek();

        if (modifications != null && !modifications.undoing && modifications.isInMainComponent(root)) {
            modifications.markEdgeAdded(edge);
        }
    }

    /**
     * Insert a non tree edge between {@code nodeU} (whose depth is {@code depthU})
     * and {@code nodeV} (whose depth is {@code depthV}). The two node must be in the
     * same tree rooted at {@code root}.
     * <p>
     * If the difference of depth, delta, is less than two, the edge is inserted
     * as a non-tree edge. Otherwise, assuming depthU < depthV, the delta / 2 - 1 ancestor of
     * {@code nodeU} is unlinked from the tree. Then {@code nodeU} and {@code nodeV} are
     * linked with a tree edge.
     * </p>
     *
     * @param root the root of the tree in which an edge is to be added.
     * @param nodeU one endpoint of the edge to add.
     * @param depthU the depth of {@code nodeU}.
     * @param nodeV the other endpoint of the edge to add.
     * @param depthV the depth of {@code nodeU}
     * @param edge edge linking {@code nodeU} and {@code nodeV}
     */
    private void insertNonTreeEdge(DTNode<V, E> root, DTNode<V, E> nodeU, int depthU, DTNode<V, E> nodeV, int depthV, Edge<V, E> edge) {
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
            // get the (delta / 2 - 1) DTNode.
            DTNode<V, E> ancestor = deep;
            for (int j = 0, bound = ancestorBound(delta); j < bound; j++) {
                ancestor = ancestor.parent;
            }

            // replace the edge between ancestor and its parent by a non tree edge
            ancestor.parent.nonTreeEdges.add(ancestor.parentEdge);
            ancestor.nonTreeEdges.add(ancestor.parentEdge);
            ancestor.unlink();

            // updating roots is useless because 'deep' will be
            // connected to 'shallow' juste after. Updating is also impossible
            // because the tree created by the previous unlink isn't in 'roots'
            deep.makeRoot(false);
            deep.link(root, shallow, edge);
        }
    }

    protected int ancestorBound(int delta) {
        return delta / 2 - 1;
    }

    private void insertTreeEdgeRecordModifications(DTNode<V, E> rootU, DTNode<V, E> rootV, E edge) {
        Modifications modifications = modificationsStack.peek();

        if (modifications != null && !modifications.undoing) {
            if (modifications.isInMainComponent(rootV)) {
                modifications.markEdgeAdded(edge);
                modifications.markAllAdded(rootU);
            } else if (modifications.isInMainComponent(rootU)) {
                modifications.markEdgeAdded(edge);
                modifications.markAllAdded(rootV);
            }
        }
    }

    /**
     * Insert a tree edge between {@code nodeU} (in tree rooted at {@code rootU})
     * and {@code nodeV} (in tree rooted at {@code rootV}).
     * Assuming the size of rootU is less than the size of rootV, we simply
     * make {@code nodeU} a root and link it with {@code nodeV}.
     *
     * @param rootU {@code nodeU} tree root
     * @param nodeU one endpoint of the edge to add.
     * @param rootV {@code nodeV} tree root
     * @param nodeV the other endpoint of the edge to add.
     * @param edge edge linking {@code nodeU} and {@code nodeV}
     * @return root of the merge tree.
     */
    private DTNode<V, E> insertTreeEdge(DTNode<V, E> rootU, DTNode<V, E> nodeU, DTNode<V, E> rootV, DTNode<V, E> nodeV, Edge<V, E> edge) {
        if (rootU.size < rootV.size) {
            nodeU.makeRoot(true);
            removeRoot(nodeU);
            return nodeU.link(rootV, nodeV, edge);
        } else {
            nodeV.makeRoot(true);
            removeRoot(nodeV);
            return nodeV.link(rootU, nodeU, edge);
        }
    }

    // ===========
    // * REMOVAL *
    // ===========

    @Override
    public void removeEdge(E edge) {
        Objects.requireNonNull(edge);
        Edge<V, E> e = edges.remove(edge);
        if (e == null) {
            return;
        }

        // update the spanning tree
        if (e.isTreeEdge()) {
            removeTreeEdge(e);
        } else {
            removeNonTreeEdgeRecordModifications(e.nodeU, edge);
            removeNonTreeEdge(e);
        }

        // keep track of modifications
        Modifications modifications = modificationsStack.peek();
        if (modifications != null) {
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
    private void removeTreeEdge(Edge<V, E> edge) {
        DTNode<V, E> child;
        if (edge.nodeU == edge.nodeV.parent) {
            child = edge.nodeV;
        } else {
            child = edge.nodeU;
        }

        // unlink child from its parent
        DTNode<V, E> otherTree = child.unlink();
        addRoot(child);

        DTNode<V, E> small;
        DTNode<V, E> large;
        if (child.size < otherTree.size) {
            small = child;
            large = otherTree;
        } else {
            small = otherTree;
            large = child;
        }

        // try to reconnect them
        ReplaceResult<V, E> replaceResult;
        if (replaceWithBest()) {
            replaceResult = replaceWithBest(small, large);
        } else {
            replaceResult = replace(small);
        }

        removeTreeEdgeRecordModifications(replaceResult, edge, large);
    }

    // search a replacement edge by doing a BFS over the smaller tree between rootSmall and rooLarge
    private ReplaceResult<V, E> replace(DTNode<V, E> rootSmall) {
        DTNode<V, E> newRoot = null; // a potential new root in case no replacement edge is found

        ArrayDeque<DTNode<V, E>> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        while (!queue.isEmpty()) {
            DTNode<V, E> n = queue.poll();

            if (n != rootSmall && n.size > rootSmall.size / 2) {
                newRoot = n;
            }

            // search for a replacement edge
            for (Edge<V, E> nonTreeEdge : n.nonTreeEdges) {
                DTNode<V, E> oppNode = nonTreeEdge.opposite(n);
                DTNode<V, E> oppRoot = oppNode.findRoot();

                if (oppRoot != rootSmall) {
                    // found a replacement edge
                    removeNonTreeEdge(nonTreeEdge);
                    DTNode<V, E> mergedRoot = insertTreeEdge(rootSmall, n, oppRoot, oppNode, nonTreeEdge);

                    return new ReplaceResult<>(true, mergedRoot);
                }
            }

            DTNode<V, E> child = n.firstChild;
            while (child != null) {
                queue.add(child);
                child = child.nextSibling;
            }
        }

        if (newRoot != null) {
            newRoot.makeRoot(true);
            return new ReplaceResult<>(false, newRoot);
        }

        return new ReplaceResult<>(false, rootSmall);
    }

    // search a replacement edge by doing a BFS over the smaller tree between rootSmall and rooLarge
    private ReplaceResult<V, E> replaceWithBest(DTNode<V, E> rootSmall, DTNode<V, E> rootLarge) {
        boolean replacementEdgeFound = false;
        DTNode<V, E> newRoot = null; // a potential new root in case no replacement edge is found

        Edge<V, E> bestNonTreeEdge = null;
        DTNode<V, E> smallNode = null;
        DTNode<V, E> largeNode = null;
        int bestDepth = Integer.MAX_VALUE;

        for (DFSIterator<V, E> it = new DFSIterator<>(rootSmall); it.hasNext();) {
            it.next();
            DTNode<V, E> n = it.node();

            if (n != rootSmall && n.size > rootSmall.size / 2) {
                newRoot = n;
            }

            // search for a replacement edge
            for (Edge<V, E> nonTreeEdge : n.nonTreeEdges) {
                DTNode<V, E> oppNode = nonTreeEdge.opposite(n);
                Pair<DTNode<V, E>, Integer> oppRoot = oppNode.findRootWithDepth();

                if (oppRoot.getKey() != rootSmall && oppRoot.getValue() < bestDepth) {
                    // found a replacement edge
                    bestNonTreeEdge = nonTreeEdge;
                    smallNode = n;
                    largeNode = oppNode;
                    replacementEdgeFound = true;
                }
            }
        }

        if (replacementEdgeFound) {
            removeNonTreeEdge(bestNonTreeEdge);
            DTNode<V, E> mergeRoot = insertTreeEdge(rootSmall, smallNode, rootLarge, largeNode, bestNonTreeEdge);
            return new ReplaceResult<>(true, mergeRoot);
        } else if (newRoot != null) {
            newRoot.makeRoot(true);
            return new ReplaceResult<>(false, newRoot);
        }

        return new ReplaceResult<>(false, rootSmall);
    }

    private record ReplaceResult<V, E>(boolean replacementEdgeFound, DTNode<V, E> root) { }

    protected boolean replaceWithBest() {
        return false;
    }

    private void removeTreeEdgeRecordModifications(ReplaceResult<V, E> replaceResult, Edge<V, E> edge, DTNode<V, E> large) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null || modifications.undoing) {
            return;
        }

        if (replaceResult.replacementEdgeFound) {
            if (modifications.isInMainComponent(replaceResult.root)) {
                modifications.markEdgeRemoved(edge.edgeData);
            }
        } else {
            // /!\ small and large can be both in the main component (when a replacement edge was found)
            // However, in this case, we only need one of the two variables to be true to have
            // the correct behavior (i.e. only the removedEdge is marked as removed).
            // When there is no replacement edge, small and large cannot be simultaneously in the main
            // component as there are in two distinct components.
            boolean smallInMain = modifications.isInMainComponent(replaceResult.root);
            boolean largeInMain = !smallInMain && modifications.isInMainComponent(large); // avoid computing isInMainComponent if we know that small is in the main component

            if (smallInMain || largeInMain) {
                modifications.markEdgeRemoved(edge.edgeData);
            }

            modifications.notifyDisconnection();

            if (largeInMain) {
                modifications.markAllRemoved(replaceResult.root);
            } else if (smallInMain) {
                modifications.markAllRemoved(large);
            }
        }
    }

    private void removeNonTreeEdgeRecordModifications(DTNode<V, E> node, E edge) {
        Modifications modifications = modificationsStack.peek();
        if (modifications != null && !modifications.undoing && modifications.isInMainComponent(node)) {
            modifications.markEdgeRemoved(edge);
        }
    }

    /**
     * Remove a non tree edge between {@code edge.nodeU} and {@code edge.nodeV}.
     * @param edge the edge to remove.
     */
    private void removeNonTreeEdge(Edge<V, E> edge) {
        edge.nodeU.nonTreeEdges.remove(edge);
        edge.nodeV.nonTreeEdges.remove(edge);
    }

    // ======================
    // * TREE MANIPULATIONS *
    // ======================

    private void addRoot(DTNode<V, E> node) {
        node.rootIndex = roots.size();
        roots.add(node);
    }

    private void removeRoot(DTNode<V, E> node) {
        // update roots, swapping 'node' and the last element of roots
        DTNode<V, E> last = roots.removeLast();
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
        DTNode<V, E> mainComponentNode;
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
        DTNode<V, E> node = getNodeOrThrow(vertex);
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

    private DTNode<V, E> getBiggestRoot() {
        DTNode<V, E> biggestRoot = roots.getFirst();

        for (int i = 1; i < roots.size(); i++) {
            DTNode<V, E> root = roots.get(i);
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
        DTNode<V, E> node = getNodeOrThrow(vertex);
        return node.findRoot().componentView();
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        checkSavedContext();
        sortTrees();

        return roots.getFirst().componentView();
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
    }

    private void checkEdges() {
        for (DTNode<V, E> node : vertexToTreeNode.values()) {
            for (Edge<V, E> nonTreeEdge : node.nonTreeEdges) {
                assert !nonTreeEdge.isTreeEdge();
            }
        }

        for (Map.Entry<E, Edge<V, E>> entry : edges.entrySet()) {
            E e = entry.getKey();
            Edge<V, E> edge = entry.getValue();

            DTNode<V, E> src = edge.nodeU;
            DTNode<V, E> dest = edge.nodeV;
            assert vertexToTreeNode.containsValue(src) && vertexToTreeNode.containsValue(dest);

            if (edge.isTreeEdge()) {
                assert src.parent == dest && src.parentEdge == edge || dest.parent == src && dest.parentEdge == edge;
            } else {
                assert src.nonTreeEdges.contains(edge);
                assert dest.nonTreeEdges.contains(edge);
            }
        }
    }

    private void checkParentChildRelation() {
        for (DTNode<V, E> node : vertexToTreeNode.values()) {
            DTNode<V, E> child = node.firstChild;

            while (child != null) {
                assert child.parent == node;
                child = child.nextSibling;
            }

            if (node.parent != null) {
                DTNode<V, E> parentChild = node.parent.firstChild;
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
     *     <li>its children in the tree and the edges linking them,</li>
     *     <li>all non tree edges having at least one endpoint that is the DTNode</li>
     *     <li>if the node is a root, its index in the list of {@link DTreeStandalone#roots}</li>
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
    private static final class DTNode<V, E> {

        private final DTreeStandalone<V, E> dtree;

        private final V vertex;

        // the size of this subtree
        private int size;

        private DTNode<V, E> parent = null;
        private Edge<V, E> parentEdge = null;

        // the children of this node. They are stored in a doubly linked list
        // firstChild is the head of the linked list. previousSibling and nextSibling
        // are used to navigate the list.
        private DTNode<V, E> firstChild = null;
        private DTNode<V, E> previousSibling = null;
        private DTNode<V, E> nextSibling = null;

        private final Set<Edge<V, E>> nonTreeEdges = new HashSet<>();

        // index in the list of roots, valid only if this node is a root
        private int rootIndex;

        private ComponentView<V, E> componentView = null;

        DTNode(DTreeStandalone<V, E> dtree, V vertex) {
            this.dtree = dtree;
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

            DTNode<V, E> child = this;
            DTNode<V, E> parent = child.parent;
            Edge<V, E> parentEdge = child.parentEdge;
            parent.removeChildUnchecked(child); // remove before making parentEdge null

            this.parent = null;
            this.parentEdge = null;

            // swap parent/child relation
            while (parent != null) {
                DTNode<V, E> greatParent = parent.parent;
                Edge<V, E> greatParentEdge = parent.parentEdge;

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
            DTNode<V, E> oldRoot = child;

            // update the list of roots
            if (updateRoots) {
                rootIndex = oldRoot.rootIndex;
                dtree.roots.set(rootIndex, DTNode.this);
            }

            // update size attributes, going from oldRoot to this DTNode
            while (oldRoot.parent != null) {
                oldRoot.size -= oldRoot.parent.size;
                oldRoot.parent.size += oldRoot.size;
                oldRoot = oldRoot.parent;
            }
        }

        /**
         * Make this node a child of {@code parent} whose tree is rooted at {@code parentRoot}.
         * @param parentRoot root of parent
         * @param parent node that will become the parent of {@code this}.
         * @param edge the edge linking {@code this} and {@code parent}
         * @return root of the merged tree. In some cases {@code parentRoot} won't be the final root
         * because of the restoration of the centroid property
         */
        private DTNode<V, E> link(DTNode<V, E> parentRoot, DTNode<V, E> parent, Edge<V, E> edge) {
            // first: update parent/child relations
            parent.addChildUnchecked(this);
            this.parent = parent;
            this.parentEdge = edge;

            // next: update size attributes in the parent tree
            DTNode<V, E> newCentroid = null;
            DTNode<V, E> cur = parent;

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
                newCentroid.makeRoot(true);
                return newCentroid;
            } else {
                return parentRoot;
            }
        }

        /**
         * Unlink this node from its parent, creating two trees, one
         * whose root is {@code this} and one whose root is returned and
         * was previously the root of the linked tree.
         *
         * @return the root of the other tree
         */
        private DTNode<V, E> unlink() {
            Objects.requireNonNull(parent);

            // first step: update size attribute in the parent tree
            DTNode<V, E> newTree = this;
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

        /**
         * Add child in the doubly linked list of children.
         * The child mustn't be in a linked list. No verification
         * is performed.
         *
         * @param child the child node to add
         */
        private void addChildUnchecked(DTNode<V, E> child) {
            DTNode<V, E> oldFirstChild = this.firstChild;

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
        private void removeChildUnchecked(DTNode<V, E> child) {
            DTNode<V, E> prev = child.previousSibling;
            DTNode<V, E> next = child.nextSibling;

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

        private DTNode<V, E> findRoot() {
            DTNode<V, E> node = this;

            while (node.parent != null) {
                node = node.parent;
            }

            return node;
        }

        private Pair<DTNode<V, E>, Integer> findRootWithDepth() {
            DTNode<V, E> node = this;
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
                componentView = new ComponentView<>(this);
            }

            return componentView;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(vertex.toString()).append(" -te-> {");

            Set<V> set = new HashSet<>();

            DTNode<V, E> child = firstChild;
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

            for (Edge<V, E> nte : nonTreeEdges) {
                if (nte.nodeU.vertex.equals(vertex)) {
                    sb.append(nte.nodeV.vertex).append(", ");
                } else if (nte.nodeV.vertex.equals(vertex)) {
                    sb.append(nte.nodeU.vertex).append(", ");
                } else {
                    sb.append("nte error, ");
                }
            }

            return sb.toString();
        }
    }

    private static final class ComponentView<V, E> extends AbstractSetView<V> {

        private final DTNode<V, E> node;

        ComponentView(DTNode<V, E> node) {
            this.node = node;
        }

        @Override
        public Iterator<V> iterator() {
            return new DFSIterator<>(node.findRoot());
        }

        @Override
        public boolean contains(Object o) {
            if (o != null) {
                // node might not be the root anymore, so need to use findRoot on node.
                return node.dtree.rootOf((V) o) == node.findRoot();
            }

            return false;
        }

        @Override
        public int size() {
            return node.findRoot().size;
        }
    }

    private static final class DFSIterator<V, E> implements Iterator<V> {

        private DTNode<V, E> cursor;
        private DTNode<V, E> current;

        /**
         * Creates a new depth-first iterator starting at the specified root node
         * and returning node according to the pre-order.
         *
         * @param root the root of the tree to traverse. It must be a root otherwise,
         *             the iterator may visit nodes outside the subtree
         */
        DFSIterator(DTNode<V, E> root) {
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

        public DTNode<V, E> node() {
            return current;
        }
    }

    private record Edge<V, E>(DTNode<V, E> nodeU, DTNode<V, E> nodeV, E edgeData) {

        public DTNode<V, E> opposite(DTNode<V, E> node) {
            if (nodeU == node) {
                return nodeV;
            } else {
                return nodeU;
            }
        }

        public boolean isTreeEdge() {
            return !nodeU.nonTreeEdges.contains(this);
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
        private DTNode<V, E> mainComponentNode;

        private boolean undoing = false;

        Modifications(DTNode<V, E> mainComponentVertex, boolean fictitiousMCV, boolean computeComparisons) {
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

                DTNode<V, E> oldComponentRoot = this.mainComponentNode.findRoot();
                DTNode<V, E> mainComponentNode = getNodeOrThrow(mainComponentVertex);
                DTNode<V, E> newComponentRoot = mainComponentNode.findRoot();

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

        public boolean isInMainComponent(DTNode<V, E> node) {
            return mainComponentNode.findRoot() == node.findRoot();
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

        public void markAllAdded(DTNode<V, E> root) {
            markAll(root, State.ADDED);
        }

        public void markAllRemoved(DTNode<V, E> root) {
            markAll(root, State.REMOVED);
        }

        public void markAll(DTNode<V, E> root, State newState) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            for (DFSIterator<V, E> it = new DFSIterator<>(root); it.hasNext();) {
                V vertex = it.next();
                verticesState.mark(vertex, newState);

                DTNode<V, E> node = it.node();
                if (node.parentEdge != null) {
                    edgesState.mark(node.parentEdge.edgeData, newState);
                }

                for (Edge<V, E> nte : node.nonTreeEdges) {
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

        public void notifyConnection(DTNode<V, E> newTree) {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(newTree);
            }
        }

        public void notifyDisconnection() {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(getBiggestRoot());
            }
        }

        private void maybeBiggestTreeChanged(DTNode<V, E> currentBiggestRoot) {
            DTNode<V, E> mainComponentVertexTree = mainComponentNode.findRoot();
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
