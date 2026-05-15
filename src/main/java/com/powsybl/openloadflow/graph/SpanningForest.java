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
 * Implements the Euler Tour data structure as described in <i>Monika Rauch Henzinger,
 * Valerie King: Randomized dynamic graph algorithms with polylogarithmic time per operation.
 * STOC 1995: 519-527</i>
 * Trees are represented by Euler Tour, themselves encoded in self-balancing binary trees.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpanningForest<V, E> {

    // map a vertex to one of the node in which it is stored.
    private final Map<V, Occurrence> vertexToOccurrence = new HashMap<>();

    // map an edge to a unique forward edge
    private final Map<E, DirectedEdge> forwardEdges = new HashMap<>();

    // map a root to the AVLTree in which it is. Indeed, a TreeNode doesn't
    // store the AVLTree in which it is. This is only for root node because
    // maintaining this information for every node is too costly.
    private final Map<AVLTree.TreeNode<DirectedEdge>, AVLTree<DirectedEdge>> rootNodeToTree = new HashMap<>();

    private AVLTree<DirectedEdge> find(V vertex) {
        Occurrence occurrence = vertexToOccurrence.get(vertex);
        return occurrence.getTree();
    }

    public boolean connected(V u, V v) {
        AVLTree<DirectedEdge> treeU = find(u);
        AVLTree<DirectedEdge> treeV = find(v);
        // this method should return false when one of two nodes isn't in the spanning tree
        // therefore the null check.
        return treeU != null && treeU == treeV;
    }

    public int componentSize(V vertex) {
        Occurrence occurrence = vertexToOccurrence.get(vertex);

        if (occurrence == null) {
            return -1;
        } else if (occurrence.isInSingleton()) {
            return 1;
        } else {
            // the size of an euler tour tree is 2 * n - 2, except when the tree contains only one node
            return (occurrence.getTree().getSize() + 1) / 2;
        }
    }

    public int treeCount() {
        return vertexToOccurrence.size() - forwardEdges.size();
    }

    public boolean addVertex(V vertex) {
        if (contains(vertex)) {
            return false;
        }

        AVLTree<DirectedEdge> tree = new AVLTree<>();
        vertexToOccurrence.put(vertex, new Occurrence(tree));

        return true;
    }

    private void addIfAbsent(V element) {
        if (!contains(element)) {
            addVertex(element);
        }
    }

    public boolean contains(V vertex) {
        return vertexToOccurrence.containsKey(vertex);
    }

    public boolean addEdge(V u, V v, E edge) {
        addIfAbsent(u);
        addIfAbsent(v);

        if (connected(u, v)) {
            return false;
        }

        Occurrence occurrenceU = vertexToOccurrence.get(u);
        Occurrence occurrenceV = vertexToOccurrence.get(v);

        AVLTree<DirectedEdge> treeU = occurrenceU.getTree();
        AVLTree<DirectedEdge> treeV = occurrenceV.getTree();

        rootNodeToTree.remove(treeU.getRoot());
        rootNodeToTree.remove(treeV.getRoot());

        // from https://github.com/tomtseng/dynamic-connectivity-hdt/blob/6e02d0e63c35e156f10cb56058ee68a96b9d0507/src/dynamic_graph/src/dynamic_forest.cpp#L121
        // Considering two trees
        //        treeNodeU                                           treeNodeV
        //        |                                                   |
        //        v                                                   v
        // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,1)] - [(6,7) (7,6) (6,8) (8,6) (6,9) (9,6)]
        // and an edge to insert: (u, v) = (2, 7)

        DirectedEdge uv = new DirectedEdge(edge, true);
        DirectedEdge vu = new DirectedEdge(edge, false);

        AVLTree<DirectedEdge> uSuccessor = occurrenceU.splitTreeAfter(treeU);
        AVLTree<DirectedEdge> vSuccessor = occurrenceV.splitTreeAfter(treeV);

        // treeU     uSuccessor                                    treeV     vSuccessor
        // [(1,2)] - [(2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,1)] - [(6,7)] - [(7,6) (6,8) (8,6) (6,9) (9,6)]
        // will be transformed into
        // treeU     uv        vSuccessor                        treeV     vu        uSuccessor
        // [(1,2)] + [(2,7)] + [(7,6) (6,8) (8,6) (6,9) (9,6)] + [(6,7)] + [(7,2)] + [(2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,1)]
        AVLTree.TreeNode<DirectedEdge> uvNode = treeU.addMax(uv);
        treeU.mergeAfter(vSuccessor);
        treeU.mergeAfter(treeV);
        AVLTree.TreeNode<DirectedEdge> vuNode = treeU.addMax(vu);
        treeU.mergeAfter(uSuccessor);
        // done

        // finally, update directedEdges and rootNodeToTree
        uv.setNodes(uvNode, vuNode);
        uv.setNodes(vuNode, uvNode);

        occurrenceU.setEdge(uvNode);
        occurrenceV.setEdge(vuNode);

        forwardEdges.put(edge, uv);
        rootNodeToTree.put(treeU.getRoot(), treeU);

        return true;
    }

    public boolean removeEdge(V u, V v, E edge) {
        if (!connected(u, v)) {
            return false;
        }

        DirectedEdge forwardEdge = forwardEdges.remove(edge);

        AVLTree<DirectedEdge> tree = forwardEdge.getTree();
        AVLTree.TreeNode<DirectedEdge> forwardNode = forwardEdge.forwardNode;
        AVLTree.TreeNode<DirectedEdge> backwardNode = forwardEdge.backwardNode;

        rootNodeToTree.remove(tree.getRoot());

        // Considering an edge to remove and one tree
        // to remove: (u,v) = (1,5)
        //                                      forwardNode                               backwardNode
        //                                      |                                         |
        //                                      v                                         v
        // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,5) (5,6) (6,5) (5,7) (7,5) (5,8) (8,5) (5,1) (1,9) (9,1)]
        // split the tree in three parts:
        // tree                                    newTree                                             right
        // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1)] - [(1,5) (5,6) (6,5) (5,7) (7,5) (5,8) (8,5) (5,1)] - [(1,9) (9,1)]
        // FIXME: is it possible that forwardEdge > backwardEdge?

        AVLTree<DirectedEdge> newTree = tree.splitBefore(forwardNode);
        AVLTree<DirectedEdge> right = tree.splitAfter(backwardNode);
        AVLTree.TreeNode<DirectedEdge> rightMin = right.getMin();
        if (rightMin == null) {
            rightMin = tree.getMin();
        }

        tree.mergeAfter(right);
        newTree.removeMin();
        newTree.removeMax();
        // which gives:
        // tree                                                newTree
        // [(1,2) (2,3) (3,2) (2,4) (4,2) (2,1) (1,9) (9,1)] - [(5,6) (6,5) (5,7) (7,5) (5,8) (8,5)]

        // TODO: update rootNodeToTree and occurrences accordingly
        if (tree.isEmpty()) {
            vertexToOccurrence.get(u).setSingleton(new AVLTree<>());
        } else {
            vertexToOccurrence.get(u).setEdge(rightMin);
            rootNodeToTree.put(tree.getRoot(), tree);
        }
        if (newTree.isEmpty()) {
            vertexToOccurrence.get(v).setSingleton(new AVLTree<>());
        } else {
            vertexToOccurrence.get(v).setEdge(newTree.getMin());
            rootNodeToTree.put(newTree.getRoot(), newTree);
        }

        return true;
    }

    public Iterator<E> edgesInComponent(V vertex) {
        return null;
    }

    public Iterator<V> verticesInComponent(V vertex) {
        return null;
    }

    public Iterator<V> roots() {
        return null;
    }

    private final class Occurrence {
        private AVLTree<DirectedEdge> singletonTree;
        private AVLTree.TreeNode<DirectedEdge> edge;

        Occurrence(AVLTree<DirectedEdge> singletonTree) {
            this.singletonTree = singletonTree;
        }

        public void setEdge(AVLTree.TreeNode<DirectedEdge> edge) {
            this.edge = edge;
            singletonTree = null;
        }

        public void setSingleton(AVLTree<DirectedEdge> singletonTree) {
            this.singletonTree = singletonTree;
            edge = null;
        }

        public boolean isInSingleton() {
            return singletonTree != null;
        }

        public AVLTree<DirectedEdge> getTree() {
            if (isInSingleton()) {
                return singletonTree;
            } else {
                return rootNodeToTree.get(edge.getRoot());
            }
        }

        public AVLTree<DirectedEdge> splitTreeAfter(AVLTree<DirectedEdge> treeU) {
            if (isInSingleton()) {
                return new AVLTree<>();
            } else {
                return treeU.splitAfter(edge);
            }
        }
    }

    private final class DirectedEdge {
        private final E edge;
        private final boolean forward;
        private AVLTree.TreeNode<DirectedEdge> forwardNode;
        private AVLTree.TreeNode<DirectedEdge> backwardNode;

        DirectedEdge(E edge, boolean forward) {
            this.edge = edge;
            this.forward = forward;
        }

        public AVLTree<DirectedEdge> getTree() {
            return rootNodeToTree.get(forwardNode.getRoot());
        }

        public void setNodes(AVLTree.TreeNode<DirectedEdge> forwardNode, AVLTree.TreeNode<DirectedEdge> backwardNode) {
            this.forwardNode = forwardNode;
            this.backwardNode = backwardNode;
        }
    }
}
