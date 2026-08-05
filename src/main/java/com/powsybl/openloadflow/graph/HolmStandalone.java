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
import org.jgrapht.util.AVLTree;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class HolmStandalone<V, E> implements SpanningForestGraphConnectivity<V, E> {

    private final Map<V, AVLTree.TreeNode<Occurrence<V, E>>> activeOccurrences = new HashMap<>();
    private final Map<E, Edge<V, E>> edges = new HashMap<>();

    private final Map<V, Set<E>> adjacencyList = new HashMap<>();

    private final List<AVLTree<Occurrence<V, E>>> trees = new ArrayList<>();
    private boolean isSorted = true;

    private final Deque<Modifications> modificationsStack = new ArrayDeque<>();
    private V defaultMainComponentVertex;

    public boolean containsVertex(V vertex) {
        return activeOccurrences.containsKey(vertex);
    }

    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
    }

    public V getEdgeSource(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge<V, E> e -> e.u;
        };
    }

    public V getEdgeTarget(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge<V, E> e -> e.v;
        };
    }

    private AVLTree.TreeNode<Occurrence<V, E>> getActiveOccurrence(V v) {
        AVLTree.TreeNode<Occurrence<V, E>> occurrence = activeOccurrences.get(v);
        if (occurrence == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return occurrence;
    }

    private AVLTree.TreeNode<Occurrence<V, E>> headOf(V vertex) {
        return getActiveOccurrence(vertex).getTreeMin();
    }

    private AVLTree<Occurrence<V, E>> treeOf(V vertex) {
        AVLTree.TreeNode<Occurrence<V, E>> occ = getActiveOccurrence(vertex);
        return trees.get(occ.getTreeMin().getValue().treeIndex);
    }

    private void addTree(AVLTree<Occurrence<V, E>> tree) {
        tree.getMin().getValue().treeIndex = trees.size();
        trees.add(tree);
    }

    private void removeTree(AVLTree<Occurrence<V, E>> tree) {
        // update trees, swapping 'tree' and the last element of trees
        AVLTree<Occurrence<V, E>> last = trees.removeLast();
        if (tree != last) {
            last.getMin().getValue().treeIndex = tree.getMin().getValue().treeIndex;
            trees.set(last.getMin().getValue().treeIndex, last);
        }
    }

    private void sortTrees() {
        if (!isSorted) {
            trees.sort((s1, s2) -> s2.getSize() - s1.getSize());
            for (int i = 0; i < trees.size(); i++) {
                trees.get(i).getMin().getValue().treeIndex = i;
            }
            isSorted = true;
        }
    }

    @Override
    public void addVertex(V vertex) {
        if (containsVertex(vertex)) {
            return;
        }

        AVLTree<Occurrence<V, E>> tree = new AVLTree<>();
        AVLTree.TreeNode<Occurrence<V, E>> occurrence = tree.addMax(new Occurrence<>(vertex, true));
        activeOccurrences.put(vertex, occurrence);
        addTree(tree);

        adjacencyList.put(vertex, new HashSet<>());

        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new VertexAdd<>(vertex));
        }

        checkInvariants();
    }

    public void removeVertex(V v) {
        if (!containsVertex(v)) {
            return;
        }

        for (E edge : adjacencyList.get(v)) {
            removeEdge(edge);
        }
        AVLTree.TreeNode<Occurrence<V, E>> head = activeOccurrences.remove(v);
        removeTree(trees.get(head.getValue().treeIndex));

        adjacencyList.remove(v);

        checkInvariants();

        // no VertexRemove modification, so don't update stack
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        if (containsEdge(edge)) {
            return;
        }

        AVLTree.TreeNode<Occurrence<V, E>> occurrenceV1 = activeOccurrences.get(vertex1);
        AVLTree.TreeNode<Occurrence<V, E>> occurrenceV2 = activeOccurrences.get(vertex2);

        AVLTree.TreeNode<Occurrence<V, E>> headV1 = occurrenceV1.getTreeMin();
        AVLTree.TreeNode<Occurrence<V, E>> headV2 = occurrenceV2.getTreeMin();

        if (headV1 == headV2) {
            // insert non tree edge
            insertNonTreeEdgeRecordModifications(headV1, edge);
            insertNonTreeEdge(occurrenceV1, occurrenceV2, edge);
        } else {
            // insert tree edge
            insertTreeEdgeRecordModifications(headV1, headV2, edge);
            insertTreeEdge(headV1, occurrenceV1, headV2, occurrenceV2, edge);

            if (!modificationsStack.isEmpty()) {
                modificationsStack.peek().notifyInsertTreeEdge(trees.get(headV1.getValue().treeIndex));
            }
        }

        adjacencyList.get(vertex1).add(edge);
        adjacencyList.get(vertex2).add(edge);

        // keep track of modifications
        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new EdgeAdd<>(vertex1, vertex2, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        checkInvariants();
    }

    private void insertNonTreeEdgeRecordModifications(AVLTree.TreeNode<Occurrence<V, E>> head, E edge) {
        if (isInMainComponentBefore(head)) {
            checkSavedContext().markEdgeAdded(edge);
        }
    }

    private void insertNonTreeEdge(AVLTree.TreeNode<Occurrence<V, E>> activeOccurrenceU,
                                   AVLTree.TreeNode<Occurrence<V, E>> activeOccurrenceV,
                                   E edge) {
        Occurrence<V, E> occU = activeOccurrenceU.getValue();
        Occurrence<V, E> occV = activeOccurrenceV.getValue();

        occU.nte.add(edge);
        occV.nte.add(edge);
        edges.put(edge, new Edge<>(occU.vertex, occV.vertex, edge, false));
    }

    private void insertTreeEdgeRecordModifications(AVLTree.TreeNode<Occurrence<V, E>> headU, AVLTree.TreeNode<Occurrence<V, E>> headV, E edge) {
        if (isInMainComponentBefore(headV)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(headU);
        } else if (isInMainComponentBefore(headU)) {
            checkSavedContext().markEdgeAdded(edge);
            markAllAdded(headV);
        }
    }

    private void insertTreeEdge(AVLTree.TreeNode<Occurrence<V, E>> headU, AVLTree.TreeNode<Occurrence<V, E>> occurrenceU,
                                AVLTree.TreeNode<Occurrence<V, E>> headV, AVLTree.TreeNode<Occurrence<V, E>> occurrenceV,
                                E e) {
        AVLTree<Occurrence<V, E>> treeU = trees.get(headU.getValue().treeIndex);
        AVLTree<Occurrence<V, E>> treeV = trees.get(headV.getValue().treeIndex);

        removeTree(treeV); // treeU will store the joined tree

        makeHead(treeU, occurrenceU);
        makeHead(treeV, occurrenceV);

        AVLTree.TreeNode<Occurrence<V, E>> forwardU = treeU.getMax();
        treeV.getMin();
        AVLTree.TreeNode<Occurrence<V, E>> backwardV = treeV.getMax();

        treeU.mergeAfter(treeV);
        treeU.addMax(new Occurrence<>(occurrenceU.getValue().vertex, false));

        Edge<V, E> edge = new Edge<>(occurrenceU.getValue().vertex, occurrenceV.getValue().vertex, e, true);
        edge.uOccPointer = forwardU;
        edge.vOccPointer = backwardV;
        forwardU.getValue().edgeToNextOccurrence = edge;
        backwardV.getValue().edgeToNextOccurrence = edge;

        edges.put(e, edge);
    }

    private void makeHead(AVLTree<Occurrence<V, E>> tree, AVLTree.TreeNode<Occurrence<V, E>> v) {
        AVLTree.TreeNode<Occurrence<V, E>> u = v.getPredecessor();
        if (u == null) {
            return; // v is head of list
        }

        // update trees
        v.getValue().treeIndex = tree.getMin().getValue().treeIndex;

        // make 'v' the head
        // the ET tree is like this: r ... u v w ... r

        AVLTree<Occurrence<V, E>> right = tree.splitBefore(v);
        // tree = r ... u; right = v w ... r

        tree.addMax(new Occurrence<>(v.getValue().vertex, false));
        // tree = r ... u v; right = v w ... r

        mergeAfter(right, tree);
        // tree = v w ... r ... u v; right = v w ... r ... u v;
        tree.mergeAfter(right);
        // tree = v w ... r ... u v; right = empty
    }

    // merge the two list, assuming that left ends and right begins with the same value
    // and store the result in 'left'.
    // That is, it requires that
    // left.getMax().getValue().vertex.equals(right.getMin().getValue().vertex)
    // and it ensures that
    // left = \old(left) + right[1..]
    // and left is a valid euler tour
    private void mergeAfter(AVLTree<Occurrence<V, E>> left, AVLTree<Occurrence<V, E>> right) {
        // left = ... t; right = t ...
        // that is, the two 't' are different occurrences of the same vertex
        AVLTree.TreeNode<Occurrence<V, E>> leftTail = left.getMax();
        AVLTree.TreeNode<Occurrence<V, E>> rightHead = right.getMin();
        AVLTree.TreeNode<Occurrence<V, E>> rightHeadSucc = rightHead.getSuccessor();

        // update active occurrence and non-tree edges
        if (rightHead.getValue().active) {
            leftTail.getValue().active = true;
            leftTail.getValue().nte = rightHead.getValue().nte;
            activeOccurrences.put(leftTail.getValue().vertex, leftTail);
        }

        // right contains only one element.
        // 1. rightHead.getValue().edgeToNextOccurrence is null
        // 2. there is nothing to append at the end of 'left'
        if (rightHeadSucc == null) {
            return;
        }

        // update edge pointers
        Edge<V, E> edge = rightHead.getValue().edgeToNextOccurrence;
        if (edge.uOccPointer == rightHead) {
            edge.uOccPointer = leftTail;
        } else {
            edge.vOccPointer = leftTail;
        }
        leftTail.getValue().edgeToNextOccurrence = edge;

        right.removeMin();
        // left = ... t; right = ...
        left.mergeAfter(right);
        // left = ... t ...
    }

    @Override
    public void removeEdge(E edge) {
        Edge<V, E> e = edges.remove(edge);
        if (e == null) {
            return;
        }

        if (e.isTreeEdge()) {
            removeTreeEdge(e);

            if (!modificationsStack.isEmpty()) {
                modificationsStack.peek().notifyRemoveTreeEdge();
            }
        } else {
            removeNonTreeEdgeRecordModifications(e);
            removeNonTreeEdge(e);
        }
        adjacencyList.get(e.u).remove(e.edge);
        adjacencyList.get(e.v).remove(e.edge);

        // keep track of modifications
        if (!modificationsStack.isEmpty()) {
            modificationsStack.peek().push(new EdgeRemove<>(e.u, e.v, edge));
        }

        // invalidate roots ordering
        isSorted = false;

        checkInvariants();
    }

    private void removeNonTreeEdgeRecordModifications(Edge<V, E> edge) {
        AVLTree.TreeNode<Occurrence<V, E>> occU = activeOccurrences.get(edge.u);
        if (isInMainComponentBefore(occU)) {
            checkSavedContext().markEdgeRemoved(edge.edge);
        }
    }

    private void removeNonTreeEdge(Edge<V, E> edge) {
        activeOccurrences.get(edge.u).getValue().nte.remove(edge.edge);
        activeOccurrences.get(edge.v).getValue().nte.remove(edge.edge);
    }

    private void removeTreeEdge(Edge<V, E> edge) {
        AVLTree<Occurrence<V, E>> tree = trees.get(edge.uOccPointer.getTreeMin().getValue().treeIndex);

        // Two cases:
        // 1.
        //     +-> (u,v)
        //     |       |
        //     v       v
        // ... u v ... v u ...
        // 2.
        //     (u,v) <-+
        //     |       |
        //     v       v
        // ... v u ... u v ...

        AVLTree<Occurrence<V, E>> afterU = tree.splitAfter(edge.uOccPointer);
        // 1. tree = ... u; afterU = v ... v u ...
        // or
        // 2. tree = ... v u ... u; afterU = v ...

        boolean isUVBeforeVU = afterU.getRoot() == edge.vOccPointer.getRoot();

        AVLTree<Occurrence<V, E>> newTree;
        if (isUVBeforeVU) {
            // tree = ... u; afterU = v ... v u ...
            AVLTree<Occurrence<V, E>> afterV = afterU.splitAfter(edge.vOccPointer);
            // tree = ... u; afterU = v ... v; afterV = u ...
            mergeAfter(tree, afterV);
            // tree = ... u ...; afterU = v ... v
            newTree = afterU;
        } else {
            // 2. tree = ... v u ... u; afterU = v ...
            AVLTree<Occurrence<V, E>> afterV = tree.splitAfter(edge.vOccPointer);
            // tree = ... u; afterV = u ... u; afterU = v ...
            mergeAfter(tree, afterU);
            // tree = ... u ...; afterV = v ... v
            newTree = afterV;
        }

        addTree(newTree);

        if (tree.getSize() > newTree.getSize()) {
            replace(newTree, tree, edge.edge);
        } else {
            replace(tree, newTree, edge.edge);
        }
    }

    private void replace(AVLTree<Occurrence<V, E>> small, AVLTree<Occurrence<V, E>> big, E removedEdge) {
        AVLTree.TreeNode<Occurrence<V, E>> smallHead = small.getMin();
        AVLTree.TreeNode<Occurrence<V, E>> bigHead = big.getMin();

        if (isInMainComponentBefore(smallHead) || isInMainComponentBefore(bigHead)) {
            checkSavedContext().markEdgeRemoved(removedEdge);
        }

        for (var it = small.nodeIterator(); it.hasNext();) {
            AVLTree.TreeNode<Occurrence<V, E>> node = it.next();
            Occurrence<V, E> occ = node.getValue();

            if (occ.nte != null && !occ.nte.isEmpty()) {
                for (E nonTreeEdge : occ.nte) {
                    Edge<V, E> edge = edges.get(nonTreeEdge);

                    V opp = edge.opposite(occ.vertex);
                    AVLTree.TreeNode<Occurrence<V, E>> oppNode = activeOccurrences.get(opp);
                    AVLTree.TreeNode<Occurrence<V, E>> oppHead = oppNode.getTreeMin();

                    if (oppHead != small.getMin()) {
                        // found a replacement edge
                        removeNonTreeEdge(edge);
                        insertTreeEdge(small.getMin(), node, oppHead, oppNode, nonTreeEdge);
                        return;
                    }
                }
            }
        }

        if (isInMainComponentBefore(bigHead)) {
            markAllRemoved(smallHead);
        } else if (isInMainComponentBefore(smallHead)) {
            markAllRemoved(bigHead);
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
            AVLTree.TreeNode<Occurrence<V, E>> head = getBiggestTree().getMin();
            mainComponentVertex = head.getValue().vertex;
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
        sortTrees();

        return headOf(vertex).getValue().treeIndex;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        if (!modificationsStack.isEmpty()) {
            Modifications modifications = modificationsStack.peek();
            modifications.setMainComponentVertex(mainComponentVertex);
        }
        defaultMainComponentVertex = mainComponentVertex;
    }

    private boolean isInMainComponentBefore(AVLTree.TreeNode<Occurrence<V, E>> node) {
        Modifications modifications = modificationsStack.peek();

        if (modifications == null) {
            return false;
        } else {
            return headOf(modifications.mainComponentVertex) == node.getTreeMin();
        }
    }

    private AVLTree<Occurrence<V, E>> getBiggestTree() {
        AVLTree<Occurrence<V, E>> biggestTree = trees.getFirst();

        for (int i = 1; i < trees.size(); i++) {
            AVLTree<Occurrence<V, E>> tree = trees.get(i);
            if (tree.getSize() > biggestTree.getSize()) {
                biggestTree = tree;
            }
        }

        return biggestTree;
    }

    @Override
    public int getNbConnectedComponents() {
        checkSavedContext();
        return trees.size();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        return new ComponentView(treeOf(vertex));
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        checkSavedContext();
        sortTrees();

        return new ComponentView(trees.getFirst());
    }

    // =========================
    // * MODIFICATIONS SUPPORT *
    // =========================

    private void markAllAdded(AVLTree.TreeNode<Occurrence<V, E>> head) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllAdded(trees.get(head.getValue().treeIndex));
    }

    private void markAllRemoved(AVLTree.TreeNode<Occurrence<V, E>> head) {
        Modifications modifications = modificationsStack.peek();
        if (modifications == null) {
            return;
        }

        modifications.markAllRemoved(trees.get(head.getValue().treeIndex));
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
    public long computeSd() {
        long sum = 0;
        for (AVLTree.TreeNode<Occurrence<V, E>> occ : activeOccurrences.values()) {
            sum += depth(occ);
        }

        return sum;
    }

    private long depth(AVLTree.TreeNode<Occurrence<V, E>> occ) {
        AVLTree.TreeNode<Occurrence<V, E>> curr = occ;
        long depth = -1; // there is a virtual root
        while (curr != null) {
            curr = curr.getParent();
            depth++;
        }

        return depth;
    }

    @Override
    public int vertexCount() {
        return activeOccurrences.size();
    }

    // ==============
    // * INVARIANTS *
    // ==============

    void checkInvariants() {
        if (false) {
            checkTrees();
            checkOccurrences();
            checkEdges();
        }
    }

    void checkTrees() {
        for (int i = 0; i < trees.size(); i++) {
            AVLTree<Occurrence<V, E>> tree = trees.get(i);
            assert tree.getSize() > 0;
            assert tree.getMin().getValue().treeIndex == i;

            Set<V> activeVertex = new HashSet<>();
            V expectedVertex = tree.getMax().getValue().vertex;
            for (Iterator<AVLTree.TreeNode<Occurrence<V, E>>> it = tree.nodeIterator(); it.hasNext();) {
                AVLTree.TreeNode<Occurrence<V, E>> node = it.next();
                Occurrence<V, E> occ = node.getValue();

                assert activeOccurrences.containsKey(occ.vertex);
                if (occ.active) {
                    assert activeVertex.add(occ.vertex);
                    assert activeOccurrences.get(occ.vertex) == node;
                }

                assert occ.vertex.equals(expectedVertex);
                expectedVertex = it.hasNext() ? occ.edgeToNextOccurrence.opposite(expectedVertex) : null;
            }

            assert activeVertex.size() == (tree.getSize() + 1) / 2;
        }
    }

    void checkOccurrences() {
        for (Map.Entry<V, AVLTree.TreeNode<Occurrence<V, E>>> entry : activeOccurrences.entrySet()) {
            V vertex = entry.getKey();
            AVLTree.TreeNode<Occurrence<V, E>> node = entry.getValue();
            Occurrence<V, E> occ = node.getValue();

            assert vertex.equals(occ.vertex);
            assert occ.active;
            if (node.getSuccessor() != null) {
                assert occ.edgeToNextOccurrence != null;
                assert occ.edgeToNextOccurrence.treeEdge;
                assert occ.edgeToNextOccurrence.u.equals(vertex) || occ.edgeToNextOccurrence.v.equals(vertex);
                assert occ.edgeToNextOccurrence.edge != null;

                Edge<V, E> edge = edges.get(occ.edgeToNextOccurrence.edge);
                assert edge != null;
                assert edge == occ.edgeToNextOccurrence;
            }
        }
    }

    void checkEdges() {
        for (Map.Entry<E, Edge<V, E>> entry : edges.entrySet()) {
            E e = entry.getKey();
            Edge<V, E> edge = entry.getValue();
            assert e == edge.edge;
            assert adjacencyList.get(edge.u).contains(e);
            assert adjacencyList.get(edge.v).contains(e);

            if (edge.treeEdge) {
                assert edge.uOccPointer != null && edge.vOccPointer != null;
                assert edge.uOccPointer.getValue().vertex.equals(edge.u);
                assert edge.uOccPointer.getSuccessor().getValue().vertex.equals(edge.v);
                assert edge.vOccPointer.getValue().vertex.equals(edge.v);
                assert edge.vOccPointer.getSuccessor().getValue().vertex.equals(edge.u);
            } else {
                assert edge.uOccPointer == null;
                assert edge.vOccPointer == null;
                assert activeOccurrences.get(edge.u).getValue().nte.contains(e);
                assert activeOccurrences.get(edge.v).getValue().nte.contains(e);
            }
        }
    }

    // ================
    // * DEBUG METHOD *
    // ================

    public String eulerTour(V vertex) {
        AVLTree<Occurrence<V, E>> tree = treeOf(vertex);

        if (tree == null) {
            return "null";
        } else {
            return eulerTour(tree);
        }
    }

    private String eulerTour(AVLTree<Occurrence<V, E>> tree) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (Occurrence<V, E> occ : tree) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(occ.vertex);
            first = false;
        }
        sb.append("]");

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // add trees
        boolean first = true;
        for (AVLTree<Occurrence<V, E>> tree : trees) {
            if (!first) {
                sb.append(", ");
            }

            sb.append(eulerTour(tree));
            first = false;
        }
        sb.append("]");

        return sb.toString();
    }

    private final class ComponentView extends AbstractSetView<V> {

        private final AVLTree<Occurrence<V, E>> tree;

        ComponentView(AVLTree<Occurrence<V, E>> tree) {
            this.tree = tree;
        }

        @Override
        public Iterator<V> iterator() {
            return new ComponentIterator<>(tree);
        }

        @Override
        public boolean contains(Object o) {
            if (o != null) {
                return activeOccurrences.get(o).getRoot() == tree.getRoot();
            }

            return false;
        }

        @Override
        public int size() {
            return (tree.getSize() + 1) / 2;
        }
    }

    private static final class ComponentIterator<V, E> implements Iterator<V> {

        private final Iterator<Occurrence<V, E>> it;
        private V next;

        ComponentIterator(AVLTree<Occurrence<V, E>> tree) {
            this.it = tree.iterator();
        }

        @Override
        public boolean hasNext() {
            while (next == null && it.hasNext()) {
                Occurrence<V, E> occ = it.next();
                if (occ.active) {
                    next = occ.vertex;
                }
            }

            return next != null;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            V next = this.next;
            this.next = null;
            return next;
        }
    }

    private static final class Occurrence<V, E> {

        private final V vertex;
        private Set<E> nte;
        private Edge<V, E> edgeToNextOccurrence;

        private boolean active;

        // valid only for head (or tree min)
        private int treeIndex;

        Occurrence(V vertex, boolean active) {
            this.vertex = vertex;
            this.active = active;

            if (active) {
                nte = new HashSet<>();
            }
        }
    }

    private static final class Edge<V, E> {
        private final V u;
        private final V v;
        private final E edge;
        private final boolean treeEdge;

        private AVLTree.TreeNode<Occurrence<V, E>> uOccPointer;
        private AVLTree.TreeNode<Occurrence<V, E>> vOccPointer;

        Edge(V u, V v, E edge, boolean treeEdge) {
            this.u = u;
            this.v = v;
            this.edge = edge;
            this.treeEdge = treeEdge;
        }

        public boolean isTreeEdge() {
            return treeEdge;
        }

        public V opposite(V vertex) {
            if (u.equals(vertex)) {
                return v;
            } else {
                return u;
            }
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

                AVLTree<Occurrence<V, E>> oldComponentTree = treeOf(this.mainComponentVertex);
                AVLTree<Occurrence<V, E>> newComponentTree = treeOf(mainComponentVertex);

                if (oldComponentTree != newComponentTree) {
                    // the new main component vertex isn't in the current main component.
                    // But that doesn't mean it wasn't in the main component before starting temporary changes,
                    // it may have been removed.
                    if (verticesState.get(mainComponentVertex) != State.REMOVED) {
                        throw new PowsyblException("Cannot take the given vertex as main component vertex! This vertex was outside the main component before starting temporary changes");
                    }

                    // last thing to do is update state of vertices and edges in the two tree.
                    markAllRemoved(oldComponentTree);
                    markAllAdded(newComponentTree);
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

        public void markAllAdded(AVLTree<Occurrence<V, E>> tree) {
            markAll(tree, State.ADDED);
        }

        public void markAllRemoved(AVLTree<Occurrence<V, E>> tree) {
            markAll(tree, State.REMOVED);
        }

        public void markAll(AVLTree<Occurrence<V, E>> tree, State newState) {
            if (verticesState == null || edgesState == null || undoing) {
                return;
            }

            for (Iterator<AVLTree.TreeNode<Occurrence<V, E>>> it = tree.nodeIterator(); it.hasNext();) {
                AVLTree.TreeNode<Occurrence<V, E>> node = it.next();
                Occurrence<V, E> occ = node.getValue();

                if (occ.active) {
                    verticesState.mark(occ.vertex, newState);

                    for (E nte : occ.nte) {
                        if (getEdgeSource(nte).equals(occ.vertex)) {
                            edgesState.mark(nte, newState);
                        }
                    }
                }

                if (node.getSuccessor() != null && getEdgeSource(occ.edgeToNextOccurrence.edge).equals(occ.vertex)) {
                    edgesState.mark(occ.edgeToNextOccurrence.edge, newState);
                }
            }
        }

        public void notifyInsertTreeEdge(AVLTree<Occurrence<V, E>> newTree) {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(newTree);
            }
        }

        public void notifyRemoveTreeEdge() {
            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(getBiggestTree());
            }
        }

        private void maybeBiggestTreeChanged(AVLTree<Occurrence<V, E>> currentBiggestTree) {
            AVLTree<Occurrence<V, E>> mainComponentVertexTree = treeOf(mainComponentVertex);
            if (currentBiggestTree.getSize() > mainComponentVertexTree.getSize()) {
                // there is a new biggest main component
                markAllRemoved(mainComponentVertexTree);
                markAllAdded(currentBiggestTree);
                mainComponentVertex = currentBiggestTree.getMin().getValue().vertex;
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
