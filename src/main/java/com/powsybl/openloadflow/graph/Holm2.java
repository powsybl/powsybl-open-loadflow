/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.util.AVLTree;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class Holm2<V, E> extends AbstractGraphConnectivity<V, E, Holm2.Graph<V, E>> {

    public Holm2() {
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
            roots.get(i).getRoot().getValue().treeIndex = i;
        }

        componentSets = graph.components;
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return getGraph().activeOccurrences.get(vertex).getRoot().getValue().treeIndex;
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
        AVLTree.TreeNode<Occurrence<V, E>> root = graph.activeOccurrences.get(vertex).getRoot();
        return graph.components.get(root.getValue().treeIndex);
    }

    @Override
    protected Set<V> getNonConnectedVertices(V vertex) {
        updateComponents();
        return super.getNonConnectedVertices(vertex);
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
            checkTrees();
            checkOccurrences();
            checkEdges();
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
                    assert edge.pointers != null && (edge.pointers.size() == 3 || edge.pointers.size() == 4);
                } else {
                    assert edge.pointers == null;
                }
            }
        }

        AVLTree.TreeNode<Occurrence<V, E>> roofOf(V vertex) {
            var node = activeOccurrences.get(vertex);
            if (node == null) {
                return null;
            }
            return node.getRoot();
        }

        AVLTree<Occurrence<V, E>> treeOf(V vertex) {
            var root = roofOf(vertex);
            if (root == null) {
                return null;
            }
            return trees.get(root.getValue().treeIndex);
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
            edge.addPointer(forwardU);
            edge.addPointer(forwardV);
            if (forwardV != backwardV) {
                edge.addPointer(backwardV);
            }
            edge.addPointer(backwardU);
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
            AVLTree.TreeNode<Occurrence<V, E>> w = v.getSuccessor();
            // the ET tree is like this: r ... u v w ... r

            AVLTree<Occurrence<V, E>> right = tree.splitBefore(v);
            // tree = r ... u; right = v w ... r

            var node = tree.addMax(new Occurrence<>(v.getValue().vertex, false));
            // tree = r ... u v; right = v w ... r

            // don't forget to update tree edge pointers
            Edge<V, E> uv = u.getValue().edgeToNextOccurrence;
            if (!u.getValue().vertex.equals(w.getValue().vertex)) {
                // r ... u;  v w ... r
                uv.removePointer(v);
            }
            uv.addPointer(node);

            mergeAfter(right, tree);
            tree.mergeAfter(right);
            // right = v w ... r ... u v; tree = empty
        }

        private void mergeAfter(AVLTree<Occurrence<V, E>> left, AVLTree<Occurrence<V, E>> right) {
            // left = ... t; right = t ...
            // that is, the two 't' are different occurrence of the same vertex
            AVLTree.TreeNode<Occurrence<V, E>> leftTail = left.getMax();
            AVLTree.TreeNode<Occurrence<V, E>> rightHead = right.getMin();
            AVLTree.TreeNode<Occurrence<V, E>> rightHeadSucc = rightHead.getSuccessor();

            if (rightHeadSucc == null) {
                return;
            }

            Edge<V, E> edge = rightHead.getValue().edgeToNextOccurrence;
            edge.removePointer(rightHead);
            edge.addPointer(leftTail);
            leftTail.getValue().edgeToNextOccurrence = edge;

            if (rightHead.getValue().active) {
                leftTail.getValue().active = true;
                activeOccurrences.put(leftTail.getValue().vertex, leftTail);
            }

            right.removeMin();
            left.mergeAfter(right);
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
            AVLTree<Occurrence<V, E>> tree = trees.get(edge.pointers.getFirst().getValue().treeIndex);
            edge.sortPointers(tree);

            AVLTree.TreeNode<Occurrence<V, E>> first = edge.pointers.getFirst();
            AVLTree.TreeNode<Occurrence<V, E>> last = edge.pointers.getLast();

            AVLTree<Occurrence<V, E>> middle = tree.splitAfter(first);
            AVLTree<Occurrence<V, E>> right = middle.splitBefore(last);
            mergeAfter(tree, right);

            addTree(middle);

            if (tree.getSize() > middle.getSize()) {
                replace(middle, tree);
            } else {
                replace(tree, middle);
            }
        }

        private void replace(AVLTree<Occurrence<V, E>> small, AVLTree<Occurrence<V, E>> big) {
            for (var it = small.nodeIterator(); it.hasNext();) {
                AVLTree.TreeNode<Occurrence<V, E>> node = it.next();
                Occurrence<V, E> occ = node.getValue();

                if (!occ.nte.isEmpty()) {
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
        private final Set<E> nte = new HashSet<>();
        private Edge<V, E> edgeToNextOccurrence;

        private boolean active;

        private int treeIndex;

        Occurrence(V vertex, boolean active) {
            this.vertex = vertex;
            this.active = active;
        }
    }

    private static final class Edge<V, E> {
        private final V u;
        private final V v;
        private final E edge;

        private boolean treeEdge;
        private List<AVLTree.TreeNode<Occurrence<V, E>>> pointers;

        Edge(V u, V v, E edge, boolean treeEdge) {
            this.u = u;
            this.v = v;
            this.edge = edge;
            this.treeEdge = treeEdge;

            if (treeEdge) {
                pointers = new ArrayList<>(4);
            }
        }

        public void addPointer(AVLTree.TreeNode<Occurrence<V, E>> pointer) {
            pointers.add(pointer);
        }

        public void removePointer(AVLTree.TreeNode<Occurrence<V, E>> pointer) {
            pointers.remove(pointer);
        }

        public void sortPointers(AVLTree<Occurrence<V, E>> tree) {
            List<AVLTree.TreeNode<Occurrence<V, E>>> tmp = pointers;

            pointers = new ArrayList<>();
            for (Iterator<AVLTree.TreeNode<Occurrence<V, E>>> it = tree.nodeIterator(); it.hasNext();) {
                AVLTree.TreeNode<Occurrence<V, E>> node = it.next();

                if (tmp.contains(node)) {
                    pointers.add(node);
                }
            }

            assert new HashSet<>(pointers).equals(new HashSet<>(tmp));
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
