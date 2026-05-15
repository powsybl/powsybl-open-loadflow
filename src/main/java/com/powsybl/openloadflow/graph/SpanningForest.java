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
    private final Map<V, AVLTree.TreeNode<V>> vertexToNode = new HashMap<>();

    private final Map<E, DirectedEdges> directedEdges = new HashMap<>();

    // map a root to the AVLTree in which it is. Indeed, a TreeNode doesn't
    // store the AVLTree in which it is. This is only for root node because
    // maintaining this information for every node is too costly.
    private final Map<AVLTree.TreeNode<V>, AVLTree<V>> rootNodeToTree = new HashMap<>();

    private AVLTree<V> find(V vertex) {
        AVLTree.TreeNode<V> treeNode = vertexToNode.get(vertex);
        if (treeNode == null) {
            return null;
        }
        return rootNodeToTree.get(treeNode.getRoot());
    }

    public boolean connected(V u, V v) {
        AVLTree<V> treeU = find(u);
        AVLTree<V> treeV = find(v);
        // this method should return false when one of two nodes isn't in the spanning tree
        // therefore the null check.
        return treeU != null && treeU == treeV;
    }

    public int componentSize(V vertex) {
        AVLTree<V> eulerTourTree = find(vertex);

        if (eulerTourTree != null) {
            // the size of an euler tour tree is 2 * n - 1
            return (eulerTourTree.getSize() + 1) / 2;
        } else {
            return -1;
        }
    }

    public boolean addVertex(V vertex) {
        if (contains(vertex)) {
            return false;
        }

        AVLTree<V> tree = new AVLTree<>();
        tree.addMax(vertex);

        vertexToNode.put(vertex, tree.getRoot());
        rootNodeToTree.put(tree.getRoot(), tree);

        return true;
    }

    private void addIfAbsent(V element) {
        if (!contains(element)) {
            addVertex(element);
        }
    }

    public boolean contains(V vertex) {
        return vertexToNode.containsKey(vertex);
    }

    public boolean addEdge(V u, V v, E edge) {
        addIfAbsent(u);
        addIfAbsent(v);

        if (connected(u, v)) {
            return false;
        }

        AVLTree.TreeNode<V> treeNodeU = vertexToNode.get(u);
        AVLTree.TreeNode<V> treeNodeV = vertexToNode.get(v);

        AVLTree<V> treeU = rootNodeToTree.get(treeNodeU);
        AVLTree<V> treeV = rootNodeToTree.get(treeNodeV);

        rootNodeToTree.remove(treeU.getRoot());
        rootNodeToTree.remove(treeV.getRoot());

        makeRoot(treeU, treeNodeU);
        makeRoot(treeV, treeNodeV);

        // Considering two rooted trees
        //  treeU root            treeV root
        //  |                     |
        //  v                     v
        // [1 2 3 2 4 2 1 5 1] - [6 7 6 8 6 9 6]
        // and an edge to insert: (u, v) = (1, 6)
        // First, add '1' at the end of treeV
        treeV.addMax(v);

        // [1 2 3 2 4 2 1 5 1] - [6 7 6 8 6 9 6 1]
        // Then insert treeV just after any occurrence of '1' in treeU (here juste after the root)
        treeU.mergeAfter(treeV);

        // And we got in treeU:
        // [1 6 7 6 8 6 9 6 1 2 3 2 4 2 1 5 1]

        // finally, we should update rootNodeToTree
        rootNodeToTree.put(treeU.getRoot(), treeU);

        DirectedEdges dEdge = new DirectedEdges(treeNodeU, treeNodeV);
        directedEdges.put(edge, dEdge);

        return true;
    }

    private void makeRoot(AVLTree<V> tree, AVLTree.TreeNode<V> newRoot) {
        // Considering one tree and an element of its Euler tour that will become the new root
        // [1 2 3 2 4 2 1 5 1] - 4
        AVLTree<V> right = tree.splitBefore(newRoot);
        // right=[1 2 3 2] tree=[4 2 1 5 1]
        right.removeMin();
        // right=[2 3 2] tree=[4 2 1 5 1]
        tree.mergeAfter(right);
        // [4 2 1 5 1 2 3 2]
        tree.addMax(newRoot.getValue());
        // [4 2 1 5 1 2 3 2 4]
    }

    public boolean removeEdge(V u, V v, E edge) {
        if (!connected(u, v)) {
            return false;
        }

        DirectedEdges directedEdge = directedEdges.remove(edge);

        AVLTree<V> tree = directedEdge.getTree();
        rootNodeToTree.remove(tree.getRoot());

        // Considering one tree and an edge to remove
        // [1 2 3 2 4 2 1 5 6 5 7 5 8 5 1 9 1] - (u, v) = (1, 5)
        //              ^             ^
        //              uvStart       vuStart
        // FIXME: it is possible that uvStart > vuStart !!!

        AVLTree<V> newTree = tree.splitAfter(directedEdge.uvStart);
        // tree=[1 2 3 2 4 2 1] - newTree=[5 6 5 7 5 8 5 1 9 1]

        AVLTree<V> right = newTree.splitAfter(directedEdge.vuStart);
        // tree=[1 2 3 2 4 2 1] - newTree=[5 6 5 7 5 8 5] - right=[1 9 1]
        right.removeMin();
        // tree=[1 2 3 2 4 2 1] - newTree=[5 6 5 7 5 8 5] - right=[9 1]
        tree.mergeAfter(right);
        // tree=[1 2 3 2 4 2 1 9 1] - newTree=[5 6 5 7 5 8 5]

        return true;
    }

    public Iterator<E> edgesInComponent(V vertex) {
        return null;
    }

    public Iterator<V> verticesInComponent(V vertex) {
        return null;
    }

    private final class DirectedEdges {
        private final AVLTree.TreeNode<V> uvStart;
        private final AVLTree.TreeNode<V> vuStart;

        DirectedEdges(AVLTree.TreeNode<V> uvStart, AVLTree.TreeNode<V> vuStart) {
            this.uvStart = uvStart;
            this.vuStart = vuStart;
        }

        public AVLTree<V> getTree() {
            return rootNodeToTree.get(uvStart.getRoot());
        }
    }
}
