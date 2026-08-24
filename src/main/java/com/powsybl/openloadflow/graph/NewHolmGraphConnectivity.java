/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.openloadflow.graph.dtree.AbstractSetView;
import org.jgrapht.util.AVLTree;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class NewHolmGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, NewHolmGraphConnectivity.Graph<V, E>> {

    public NewHolmGraphConnectivity() {
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

        Graph<V, E> graph = getGraph();
        List<AVLTree<Occurrence<V, E>>> roots = graph.trees;

        // sorting roots will sort components as components is a wrapper around roots
        roots.sort((s1, s2) -> s2.getSize() - s1.getSize());
        for (int i = 0; i < graph.trees.size(); i++) {
            roots.get(i).getMin().getValue().treeIndex = i;
        }

        componentSets = graph.components;
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        super.startTemporaryChanges(computeComparisons);
        getGraph().checkInvariants();
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return getGraph().activeOccurrences.get(vertex).getTreeMin().getValue().treeIndex;
    }

    @Override
    public int getNbConnectedComponents() {
        checkSavedContext();
        return getGraph().trees.size();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSavedContext();
        checkVertex(vertex);
        Graph<V, E> graph = getGraph();
        AVLTree.TreeNode<Occurrence<V, E>> head = graph.activeOccurrences.get(vertex).getTreeMin();
        return graph.components.get(head.getValue().treeIndex);
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        checkSavedContext();
        checkVertex(vertex);

        Graph<V, E> graph = getGraph();
        AVLTree<Occurrence<V, E>> excludedTree = graph.treeOf(vertex);

        Set<V> components = new HashSet<>();
        for (AVLTree<Occurrence<V, E>> tree : graph.trees) {
            if (tree != excludedTree) {
                components.addAll(graph.componentView(tree));
            }
        }

        return components;
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    protected static final class Graph<V, E> implements GraphModel<V, E> {

        private final Map<V, AVLTree.TreeNode<Occurrence<V, E>>> activeOccurrences = new HashMap<>();
        private final Map<E, Edge<V, E>> edges = new HashMap<>();

        private final Map<V, Set<E>> adjacencyList = new HashMap<>();

        private final List<AVLTree<Occurrence<V, E>>> trees = new ArrayList<>();

        private final AllComponentsView components = new AllComponentsView();

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
                assert occ.nte != null;
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

        AVLTree.TreeNode<Occurrence<V, E>> headOf(V vertex) {
            var node = activeOccurrences.get(vertex);
            if (node == null) {
                return null;
            }
            return node.getTreeMin();
        }

        AVLTree<Occurrence<V, E>> treeOf(V vertex) {
            var head = headOf(vertex);
            if (head == null) {
                return null;
            }
            return trees.get(head.getValue().treeIndex);
        }

        @Override
        public void addEdge(V v1, V v2, E e) {
            if (containsEdge(e)) {
                return;
            }

            AVLTree.TreeNode<Occurrence<V, E>> occurrenceV1 = activeOccurrences.get(v1);
            AVLTree.TreeNode<Occurrence<V, E>> occurrenceV2 = activeOccurrences.get(v2);

            AVLTree.TreeNode<Occurrence<V, E>> headV1 = occurrenceV1.getTreeMin();
            AVLTree.TreeNode<Occurrence<V, E>> headV2 = occurrenceV2.getTreeMin();

            if (headV1 == headV2) {
                // insert non tree edge
                insertNonTreeEdge(occurrenceV1, occurrenceV2, e);
            } else {
                // insert tree edge
                insertTreeEdge(headV1, occurrenceV1, headV2, occurrenceV2, e);
            }

            adjacencyList.get(v1).add(e);
            adjacencyList.get(v2).add(e);

            checkInvariants();
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

        private void insertTreeEdge(AVLTree.TreeNode<Occurrence<V, E>> headU, AVLTree.TreeNode<Occurrence<V, E>> occurrenceU,
                                    AVLTree.TreeNode<Occurrence<V, E>> headV, AVLTree.TreeNode<Occurrence<V, E>> occurrenceV,
                                    E e) {
            AVLTree<Occurrence<V, E>> treeU = trees.get(headU.getValue().treeIndex);
            AVLTree<Occurrence<V, E>> treeV = trees.get(headV.getValue().treeIndex);

            removeTree(treeV); // treeU will store the joined tree

            makeHead(treeU, occurrenceU);
            makeHead(treeV, occurrenceV);

            var forwardU = treeU.getMax();
            var forwardV = treeV.getMin();
            var backwardV = treeV.getMax();

            treeU.mergeAfter(treeV);
            var backwardU = treeU.addMax(new Occurrence<>(occurrenceU.getValue().vertex, false));

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
        public void removeEdge(E e) {
            Edge<V, E> edge = edges.remove(e);
            if (edge == null) {
                return;
            }

            if (edge.isTreeEdge()) {
                removeTreeEdge(edge);
            } else {
                removeNonTreeEdge(edge);
            }
            adjacencyList.get(edge.u).remove(edge.edge);
            adjacencyList.get(edge.v).remove(edge.edge);

            checkInvariants();
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
                replace(newTree, tree);
            } else {
                replace(tree, newTree);
            }
        }

        private void replace(AVLTree<Occurrence<V, E>> small, AVLTree<Occurrence<V, E>> big) {
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
        }

        @Override
        public void addVertex(V v) {
            if (containsVertex(v)) {
                return;
            }

            AVLTree<Occurrence<V, E>> tree = new AVLTree<>();
            AVLTree.TreeNode<Occurrence<V, E>> occurrence = tree.addMax(new Occurrence<>(v, true));
            activeOccurrences.put(v, occurrence);
            addTree(tree);

            adjacencyList.put(v, new HashSet<>());

            checkInvariants();
        }

        @Override
        public void removeVertex(V v) {
            if (!containsVertex(v)) {
                return;
            }

            for (E edge : getNeighborEdgesOf(v)) {
                removeEdge(edge);
            }
            AVLTree.TreeNode<Occurrence<V, E>> head = activeOccurrences.remove(v);
            removeTree(trees.get(head.getValue().treeIndex));

            adjacencyList.remove(v);

            checkInvariants();
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

        @Override
        public boolean containsVertex(V vertex) {
            return activeOccurrences.containsKey(vertex);
        }

        @Override
        public boolean containsEdge(E edge) {
            return edges.containsKey(edge);
        }

        @Override
        public V getEdgeSource(E edge) {
            return switch (edges.get(edge)) {
                case null -> null;
                case Edge<V, E> e -> e.u;
            };
        }

        @Override
        public V getEdgeTarget(E edge) {
            return switch (edges.get(edge)) {
                case null -> null;
                case Edge<V, E> e -> e.v;
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
            return adjacencyList.get(v);
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

        ComponentView componentView(AVLTree<Occurrence<V, E>> tree) {
            return new ComponentView(tree);
        }

        private final class AllComponentsView extends AbstractList<Set<V>> {

            @Override
            public Set<V> get(int index) {
                return new ComponentView(trees.get(index));
            }

            @Override
            public int size() {
                return trees.size();
            }
        }

        private final class ComponentView extends AbstractSetView<V> {

            private final AVLTree<Occurrence<V, E>> tree;

            ComponentView(AVLTree<Occurrence<V, E>> tree) {
                this.tree = tree;
            }

            @Override
            public Iterator<V> iterator() {
                return new ComponentIterator(tree);
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

        private final class ComponentIterator implements Iterator<V> {

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
}
