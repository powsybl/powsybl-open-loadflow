/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.google.common.collect.Iterators;
import org.jgrapht.util.AVLTree;

import java.util.*;

/**
 * Implements the Euler Tour data structure as described in <i>Monika Rauch Henzinger,
 * Valerie King: Randomized dynamic graph algorithms with polylogarithmic time per operation.
 * STOC 1995: 519-527</i>
 * Trees are represented by Euler Tour, themselves encoded in self-balancing binary trees.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpanningForest<V, E> {

    // map a vertex to one of the node in which it is stored.
    private final Map<V, Occurrences> vertexToOccurrences = new HashMap<>();

    // map an edge to a unique forward edge
    private final Map<E, DirectedEdge> forwardEdges = new HashMap<>();

    // map a root to the AVLTree in which it is. Indeed, a TreeNode doesn't
    // store the AVLTree in which it is. This is only for root node because
    // maintaining this information for every node is too costly.
    private final Map<AVLTree.TreeNode<DirectedEdge>, AVLTree<DirectedEdge>> rootNodeToTree = new LinkedHashMap<>();

    private final Set<V> singletons = new HashSet<>();

    private AVLTree<DirectedEdge> find(V vertex) {
        Occurrences occurrence = vertexToOccurrences.get(vertex);
        return occurrence == null ? null : occurrence.getTree();
    }

    public boolean connected(V u, V v) {
        AVLTree<DirectedEdge> treeU = find(u);
        AVLTree<DirectedEdge> treeV = find(v);
        // this method should return false when one of two nodes isn't in the spanning tree
        // therefore the null check.
        return treeU != null && treeU == treeV;
    }

    public int treeSize(V vertex) {
        Occurrences occurrence = vertexToOccurrences.get(vertex);

        if (occurrence == null) {
            return -1;
        } else if (occurrence.isInSingleton()) {
            return 1;
        } else {
            // the size of an euler tour tree is 2 * n - 2, except when the tree contains only one node
            return (occurrence.getTree().getSize() + 2) / 2;
        }
    }

    public int treeCount() {
        return vertexToOccurrences.size() - forwardEdges.size();
    }

    public boolean addVertex(V vertex) {
        if (contains(vertex)) {
            return false;
        }

        AVLTree<DirectedEdge> tree = new AVLTree<>();
        vertexToOccurrences.put(vertex, new Occurrences(tree));
        singletons.add(vertex);

        return true;
    }

    public boolean removeVertex(V vertex) {
        Occurrences occ = vertexToOccurrences.get(vertex);
        if (occ == null) {
            return false;
        }

        while (!occ.edges.isEmpty()) {
            DirectedEdge edge = occ.edges.getFirst();
            removeEdge(edge.src, edge.dest(), edge.undirectedEdge);
        }
        vertexToOccurrences.remove(vertex);
        singletons.remove(vertex);

        return true;
    }

    private void addIfAbsent(V element) {
        if (!contains(element)) {
            addVertex(element);
        }
    }

    public boolean contains(V vertex) {
        return vertexToOccurrences.containsKey(vertex);
    }

    public boolean addEdge(V u, V v, E edge) {
        if (u == v || connected(u, v) || forwardEdges.containsKey(edge)) {
            return false;
        }

        addIfAbsent(u);
        addIfAbsent(v);

        Occurrences occurrenceU = vertexToOccurrences.get(u);
        Occurrences occurrenceV = vertexToOccurrences.get(v);

        AVLTree<DirectedEdge> treeU = occurrenceU.getTree();
        AVLTree<DirectedEdge> treeV = occurrenceV.getTree();

        rootNodeToTree.remove(treeU.getRoot());
        rootNodeToTree.remove(treeV.getRoot());

        DirectedEdge uv = new DirectedEdge(edge, u, true);
        DirectedEdge vu = uv.backward(v);

        AVLTree.TreeNode<DirectedEdge> uvNode;
        AVLTree.TreeNode<DirectedEdge> vuNode;
        if (occurrenceU.isInSingleton() && occurrenceV.isInSingleton()) {
            uvNode = treeU.addMax(uv);
            vuNode = treeU.addMax(vu);

            singletons.remove(u);
            singletons.remove(v);

        } else if (occurrenceU.isInSingleton()) {
            makeRoot(treeV, occurrenceV.activeEdge());
            vuNode = treeV.addMax(vu);
            uvNode = treeV.addMax(uv);

            singletons.remove(u);

            treeU = treeV;

        } else if (occurrenceV.isInSingleton()) {
            makeRoot(treeU, occurrenceU.activeEdge());
            uvNode = treeU.addMax(uv);
            vuNode = treeU.addMax(vu);

            singletons.remove(v);

        } else {
            // Considering two trees
            //        treeNodeU                                           treeNodeV
            //        |                                                   |
            //        v                                                   v
            // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,1)] - [(6,7) (7,6) (6,8) (8,6) (6,9) (9,6)]
            // and an edge to insert: (u, v) = (2, 7)

            AVLTree.TreeNode<DirectedEdge> treeNodeU = occurrenceU.activeEdge();
            AVLTree.TreeNode<DirectedEdge> treeNodeV = occurrenceV.activeEdge();
            makeRoot(treeU, treeNodeU);
            makeRoot(treeV, treeNodeV);

            // treeU                                               treeV
            // [(2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,1) (1,2)] - [(7,6) (6,8) (8,6) (6,9) (9,6) (6,7)]
            //  ^ treeNodeU                                         ^ treeNodeV

            uvNode = treeU.addMax(uv);
            treeU.mergeAfter(treeV);
            vuNode = treeU.addMax(vu);
            // [(2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,1) (1,2)] + [(2,7)] [(7,6) (6,8) (8,6) (6,9) (9,6) (6,7)] + [(7, 2])

            // done
        }

        // finally, update directedEdges and rootNodeToTree
        uv.setNodes(uvNode, vuNode);
        vu.setNodes(vuNode, uvNode);

        occurrenceU.addEdge(uv);
        occurrenceV.addEdge(vu);

        forwardEdges.put(edge, uv);
        rootNodeToTree.put(treeU.getRoot(), treeU);

        return true;
    }

    private void makeRoot(AVLTree<DirectedEdge> tree, AVLTree.TreeNode<DirectedEdge> newRoot) {
        AVLTree<DirectedEdge> left = tree.splitBefore(newRoot);
        tree.mergeBefore(left);
    }

    public boolean removeEdge(V u, V v, E edge) {
        if (!connected(u, v)) {
            return false;
        }

        DirectedEdge forwardEdge = forwardEdges.remove(edge);
        if (forwardEdge == null) {
            return false;
        }

        AVLTree<DirectedEdge> tree = forwardEdge.getTree();
        AVLTree.TreeNode<DirectedEdge> forwardNode = forwardEdge.forwardNode; // uv
        AVLTree.TreeNode<DirectedEdge> backwardNode = forwardEdge.backwardNode; // vu

        rootNodeToTree.remove(tree.getRoot());

        // Considering an edge to remove and one tree
        // to remove: (u,v) = (1,5)
        //                                      forward/backwardNode                      forward/backwardNode
        //                                      |                                         |
        //                                      v                                         v
        // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,6) (6,5) (5,7) (7,5) (5,8) (8,5) (5,1) (1,9) (9,1)]
        // split the tree in two parts using forwardNode, we have two possibilities because we
        // don't know the relative position between forwardNode and backwardNode

        AVLTree<DirectedEdge> newTree = tree.splitBefore(forwardNode);
        boolean isUVBeforeVU = backwardNode.getRoot() == newTree.getRoot(); // it means that forwardNode < backwardNode (index-wise)

        if (isUVBeforeVU) {
            AVLTree<DirectedEdge> right = newTree.splitAfter(backwardNode);
            // tree                                    newTree                                             right
            // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1)] - [(1,5) (5,6) (6,5) (5,7) (7,5) (5,8) (8,5) (5,1)] - [(1,9) (9,1)]
            //                                          ^ forwardNode                             ^ backwardNode
            tree.mergeAfter(right);
            newTree.removeMin();
            newTree.removeMax();
            // tree                                                newTree
            // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,9) (9,1)] - [(5,6) (6,5) (5,7) (7,5) (5,8) (8,5)]
        } else {
            AVLTree<DirectedEdge> middle = tree.splitBefore(backwardNode);

            // tree                                    middle                                        newTree
            // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1)] - [(1,5) (5,6) (6,5) (5,7) (7,5) (5,8) (8,5)] - [(5,1) (1,9) (9,1)]
            //                                         ^ backwardNode                                 ^ forwardNode
            newTree.removeMin();
            middle.removeMin();
            tree.mergeAfter(newTree);
            // tree                                                middle
            // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,9) (9,1)] - [(5,6) (6,5) (5,7) (7,5) (5,8) (8,5)]

            newTree = middle;
        }

        // Finally:
        // tree                                                newTree
        // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,9) (9,1)] - [(5,6) (6,5) (5,7) (7,5) (5,8) (8,5)]

        Occurrences src = vertexToOccurrences.get(forwardEdge.src);
        src.removeEdge(forwardEdge);
        if (src.isInSingleton()) {
            singletons.add(forwardEdge.src);
        }

        Occurrences dest = vertexToOccurrences.get(forwardEdge.dest());
        dest.removeEdge(forwardEdge.backwardNode.getValue());
        if (dest.isInSingleton()) {
            singletons.add(forwardEdge.dest());
        }

        if (!tree.isEmpty()) {
            rootNodeToTree.put(tree.getRoot(), tree);
        }
        if (!newTree.isEmpty()) {
            rootNodeToTree.put(newTree.getRoot(), newTree);
        }

        return true;
    }

    public Iterator<E> edgesInComponent(V vertex) {
        AVLTree<DirectedEdge> tree = find(vertex);

        if (tree == null || tree.isEmpty()) {
            return Collections.emptyIterator();
        }

        return new Iterator<>() {
            private final Iterator<DirectedEdge> it = tree.iterator();
            private E next = null;

            @Override
            public boolean hasNext() {
                while (next == null && it.hasNext()) {
                    DirectedEdge edge = it.next();
                    if (edge.forward) {
                        next = edge.undirectedEdge;
                    }
                }

                return next != null;
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                E next = this.next;
                this.next = null;
                return next;
            }
        };
    }

    public Iterator<V> verticesInComponent(V vertex) {
        AVLTree<DirectedEdge> tree = find(vertex);

        if (tree == null) {
            return Collections.emptyIterator(); // vertex isn't in the forest
        } else if (tree.isEmpty()) {
            return Iterators.singletonIterator(vertex);
        } else {
            return new Iterator<V>() {
                private final Iterator<DirectedEdge> it = tree.iterator();
                private V next = null;

                @Override
                public boolean hasNext() {
                    while (next == null && it.hasNext()) {
                        DirectedEdge edge = it.next();

                        if (vertexToOccurrences.get(edge.src).activeEdge() == edge.forwardNode) {
                            next = edge.src;
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
            };
        }
    }

    public Iterator<E> adjacentEdges(V vertex) {
        Occurrences occ = vertexToOccurrences.get(vertex);

        if (occ == null || occ.edges.isEmpty()) {
            return Collections.emptyIterator();
        }

        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < occ.edges.size();
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                DirectedEdge e = occ.edges.get(index);
                index++;
                return e.undirectedEdge;
            }
        };
    }

    // TODO: simplify this method
    public Iterator<V> roots() {
        Iterator<V> roots = Iterators.transform(rootNodeToTree.keySet().iterator(),
                rootNode -> Objects.requireNonNull(rootNode).getValue().src);

        return Iterators.concat(singletons.iterator(), roots);
    }

    public List<Set<V>> getComponents() {
        List<Set<V>> components = new ArrayList<>();

        for (AVLTree<DirectedEdge> trees : rootNodeToTree.values()) {
            Set<V> component = new HashSet<>();
            trees.iterator().forEachRemaining(d -> component.add(d.src));
            components.add(component);
        }

        for (V singleton : singletons) {
            components.add(Set.of(singleton));
        }

        return components;
    }

    public String eulerTour(V vertex) {
        AVLTree<DirectedEdge> tree = find(vertex);

        if (tree == null) {
            return "null";
        } else {
            return eulerTour(tree);
        }
    }

    private String eulerTour(AVLTree<DirectedEdge> tree) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (DirectedEdge edge : tree) {
            if (!first) {
                sb.append(", ");
            }
            DirectedEdge backward = edge.backwardNode.getValue();

            sb.append("(");
            sb.append(edge.src).append(", ").append(backward.src);
            sb.append(")");
            first = false;
        }
        sb.append("]");

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // first add singleton nodes
        boolean first = true;
        for (Map.Entry<V, Occurrences> entry : vertexToOccurrences.entrySet()) {
            if (entry.getValue().isInSingleton()) {
                if (!first) {
                    sb.append(", ");
                }

                sb.append("[").append(entry.getKey()).append("]");
                first = false;
            }
        }

        // add trees
        for (AVLTree<DirectedEdge> tree : rootNodeToTree.values()) {
            if (!first) {
                sb.append(", ");
            }

            sb.append(eulerTour(tree.getRoot().getValue().src));
            first = false;
        }
        sb.append("]");

        return sb.toString();
    }

    /*public void checkInvariants() {
        checkOccurrences();
        checkForwardEdges();
        checkRootNodeToTree();
    }

    private void checkOccurrences() {
        for (Map.Entry<V, Occurrences> entry : vertexToOccurrences.entrySet()) {
            Occurrences occurrences = entry.getValue();

            if (occurrences.isInSingleton()) {
                // assert that no tree contains V
                assert occurrences.edges.isEmpty();

                for (AVLTree<DirectedEdge> tree : rootNodeToTree.values()) {
                    for (DirectedEdge edge : tree) {
                        assert edge.src != entry.getValue();
                    }
                }

            } else {
                for (DirectedEdge edge : occurrences.edges) {
                    assert edge.src == entry.getKey();
                }
                for (DirectedEdge edge : forwardEdges.values()) {
                    if (!occurrences.edges.contains(edge)) {
                        assert edge.src != entry.getValue();
                    }
                }
            }
        }
    }

    private void checkForwardEdges() {
        for (Map.Entry<E, DirectedEdge> edges : forwardEdges.entrySet()) {
            DirectedEdge edge = edges.getValue();

            assert edge.undirectedEdge == edges.getKey();
            assert edge.forward;
            assert edge.forwardNode != null && edge.forwardNode.getValue() == edge;
            assert edge.backwardNode != null && edge.backwardNode.getValue().undirectedEdge == edge.undirectedEdge;
        }
    }

    private void checkRootNodeToTree() {
        for (Map.Entry<AVLTree.TreeNode<DirectedEdge>, AVLTree<DirectedEdge>> entry : rootNodeToTree.entrySet()) {
            AVLTree<DirectedEdge> tree = entry.getValue();

            assert entry.getKey() == tree.getRoot();
            assert tree.getSize() > 0;
            assert tree.getSize() % 2 == 0;

            // check tree is an euler tour
            DirectedEdge first = null;
            DirectedEdge prev = null;
            for (DirectedEdge edge : entry.getValue()) {
                if (prev != null) {
                    assert prev.backwardNode.getValue().src == edge.src;
                } else {
                    first = edge;
                }

                prev = edge;
            }

            assert first != null && prev.backwardNode.getValue().src == first.src;
        }
    }*/

    private final class Occurrences {
        private AVLTree<DirectedEdge> singletonTree; // TODO: remove
        // edges in which this vertex is the source
        private final List<DirectedEdge> edges = new ArrayList<>();

        Occurrences(AVLTree<DirectedEdge> singletonTree) {
            this.singletonTree = singletonTree;
        }

        public AVLTree.TreeNode<DirectedEdge> activeEdge() {
            return edges.getFirst().forwardNode;
        }

        public void addEdge(DirectedEdge edge) {
            this.edges.add(edge);
            singletonTree = null;
        }

        public void removeEdge(DirectedEdge edge) {
            this.edges.remove(edge); // TODO: replace with linked list
            if (edges.isEmpty()) {
                singletonTree = new AVLTree<>();
            }
        }

        public boolean isInSingleton() {
            return edges.isEmpty();
        }

        public AVLTree<DirectedEdge> getTree() {
            if (isInSingleton()) {
                return singletonTree;
            } else {
                return rootNodeToTree.get(activeEdge().getRoot());
            }
        }
    }

    private final class DirectedEdge {
        private final E undirectedEdge;
        private final V src;
        private final boolean forward;
        private AVLTree.TreeNode<DirectedEdge> forwardNode;
        private AVLTree.TreeNode<DirectedEdge> backwardNode;

        DirectedEdge(E undirectedEdge, V src, boolean forward) {
            this.undirectedEdge = undirectedEdge;
            this.src = src;
            this.forward = forward;
        }

        public DirectedEdge backward(V dest) {
            return new DirectedEdge(undirectedEdge, dest, !forward);
        }

        public AVLTree<DirectedEdge> getTree() {
            return rootNodeToTree.get(forwardNode.getRoot());
        }

        public void setNodes(AVLTree.TreeNode<DirectedEdge> forwardNode, AVLTree.TreeNode<DirectedEdge> backwardNode) {
            this.forwardNode = forwardNode;
            this.backwardNode = backwardNode;
        }

        public V dest() {
            return backwardNode.getValue().src;
        }
    }
}
