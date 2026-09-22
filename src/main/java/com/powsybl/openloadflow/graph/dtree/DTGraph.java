/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtree;

import com.powsybl.openloadflow.graph.GraphModel;

import java.util.*;

/**
 * A graph maintaining a spanning forest over its connected component.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 * @see DTreeGraphConnectivity
 */
public class DTGraph<V, E> implements GraphModel<V, E> {

    /**
     * map a vertex to a node in a spanning tree
     */
    private final Map<V, DTNode<V, E>> vertexToTreeNode = new HashMap<>();
    /**
     * map an edge to an edge in a spanning tree
     */
    private final Map<E, Edge<V, E>> edges = new HashMap<>();

    /**
     * the list of tree roots. Roots are maintained in a way such that
     * the value of the attribute 'rootIndex' of the DTNode at index i is i.
     * In other words: roots.get(i).rootIndex == i
     */
    private final Set<DTNode<V, E>> roots = new LinkedHashSet<>();

    public DTNode<V, E> getNodeOrThrow(V v) {
        DTNode<V, E> node = vertexToTreeNode.get(v);
        if (node == null) {
            throw new IllegalArgumentException("given vertex " + v + " is not in the graph");
        }

        return node;
    }

    /**
     * Return the root of the tree in which {@code vertex}.
     *
     * @param vertex the vertex whose tree root is to be returned.
     * @return the root of the tree in which {@code vertex} is.
     */
    DTNode<V, E> rootOf(V vertex) {
        return getNodeOrThrow(vertex).findRoot();
    }

    @Override
    public void addVertex(V v) {
        if (containsVertex(v)) {
            return;
        }

        DTNode<V, E> newNode = new DTNode<>(this, v);
        vertexToTreeNode.put(v, newNode);
        addRoot(newNode);
    }

    @Override
    public void removeVertex(V v) {
        if (!containsVertex(v)) {
            return;
        }

        for (E edge : getNeighborEdgesOf(v)) {
            removeEdge(edge);
        }

        DTNode<V, E> root = vertexToTreeNode.remove(v);
        removeRoot(root);
    }

    @Override
    public void addEdge(V u, V v, E e) {
        if (containsEdge(e)) {
            return;
        }

        DTNode<V, E> nodeU = getNodeOrThrow(u);
        DTNode<V, E> nodeV = getNodeOrThrow(v);

        // update edges
        Edge<V, E> edge = new Edge<>(nodeU, nodeV, e);
        edges.put(e, edge);

        // update spanning trees
        DTNodeWithDepth<V, E> rootUdepth = nodeU.findRootWithDepth();
        DTNodeWithDepth<V, E> rootVdepth = nodeV.findRootWithDepth();

        if (rootUdepth.node() == rootVdepth.node()) {
            // insert non tree edge
            insertEdgeInComponent(rootUdepth.node(), nodeU, rootUdepth.depth(), nodeV, rootVdepth.depth(), edge);
        } else {
            // insert tree edge
            insertTreeEdge(rootUdepth.node(), nodeU, rootVdepth.node(), nodeV, edge);
        }
    }

    /**
     * Insert an edge between {@code nodeU} (whose depth is {@code depthU})
     * and {@code nodeV} (whose depth is {@code depthV}). The two nodes must be in the
     * same tree rooted at {@code root}. Depending on the difference of depth, delta, between
     * the two nodes, the edge may be inserted as a non-tree edge or a tree edge.
     *
     * <ul>
     *     <li>delta <= 1: the edge is inserted as a non-tree edge</li>
     *     <li>delta >= 2: assuming depthU < depthV, the delta / 2 - 1 ancestor of
     *     {@code nodeU} is unlinked from the tree. Then {@code nodeU} and {@code nodeV} are
     *     linked with a tree edge.</li>
     * </ul>
     *
     * <p>
     * The original DTree paper uses delta - 2 instead of delta / 2 - 1. But a more recent
     * article indicates better results with delta / 2 - 1. Experimentation confirms this,
     * the average depth is smaller with the new upper bound.
     * </p>
     *
     * @param root   the root of the tree in which an edge is to be added.
     * @param nodeU  one endpoint of the edge to add.
     * @param depthU the depth of {@code nodeU}.
     * @param nodeV  the other endpoint of the edge to add.
     * @param depthV the depth of {@code nodeU}
     * @param edge   edge linking {@code nodeU} and {@code nodeV}
     */
    private void insertEdgeInComponent(DTNode<V, E> root, DTNode<V, E> nodeU, int depthU, DTNode<V, E> nodeV, int depthV, Edge<V, E> edge) {
        DTNode<V, E> shallow = nodeV;
        DTNode<V, E> deep = nodeU;
        int delta = Math.abs(depthU - depthV);

        if (depthU <= depthV) {
            shallow = nodeU;
            deep = nodeV;
        }

        if (delta < 2) {
            // no changes in the BFS tree
            nodeU.addNonTreeEdge(edge);
            nodeV.addNonTreeEdge(edge);
        } else {
            // get the (delta / 2 - 1) DTNode.
            DTNode<V, E> ancestor = deep;
            for (int j = 0; j < delta / 2 - 1; j++) {
                ancestor = ancestor.getParent();
            }

            // replace the edge between ancestor and its parent by a non tree edge
            ancestor.replaceParentLinkByNonTreeEdge();

            // updating roots is useless because 'deep' will be
            // connected to 'shallow' juste after. Updating is also impossible
            // because the tree created by the previous unlink isn't in 'roots'
            deep.makeRoot(false);
            deep.link(root, shallow, edge);
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
     * @param edge  edge linking {@code nodeU} and {@code nodeV}
     */
    private void insertTreeEdge(DTNode<V, E> rootU, DTNode<V, E> nodeU, DTNode<V, E> rootV, DTNode<V, E> nodeV, Edge<V, E> edge) {
        if (rootU.size() < rootV.size()) {
            nodeU.makeRoot(true);
            removeRoot(nodeU);
            nodeU.link(rootV, nodeV, edge);
        } else {
            nodeV.makeRoot(true);
            removeRoot(nodeV);
            nodeV.link(rootU, nodeU, edge);
        }
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
    }

    /**
     * Let {@code nodeU} be {@code edge.nodeU()} and {@code nodeV} be {@code edge.nodeV()}.
     * Remove the tree edge between {@code nodeU} and {@code nodeV}.
     * Assuming nodeU is a child of {@code nodeV}, this is a two steps process :
     * <ol>
     *     <li>Unlink nodeU from nodeV. This creates two trees with a smaller one called {@code small},</li>
     *     <li>Unlink {@code nodeU} from {@code nodeV}. This creates two trees with a smaller one called {@code small},</li>
     *     <ul>
     *         <li>if one is found, it is a non-tree edge so it is removed and then added as a tree edge</li>
     *         <li>if none is found, fix the centroid property</li>
     *     </ul>
     * </ol>
     *
     * @param edge the edge to remove
     */
    private void removeTreeEdge(Edge<V, E> edge) {
        DTNode<V, E> child;

        if (edge.nodeU() == edge.nodeV().getParent()) {
            child = edge.nodeV();
        } else {
            child = edge.nodeU();
        }

        // unlink child from its parent
        DTNode<V, E> otherTree = child.unlink();
        addRoot(child);

        DTNode<V, E> small;
        if (child.size() < otherTree.size()) {
            small = child;
        } else {
            small = otherTree;
        }

        // try to reconnect them
        replace(small);
    }

    private void replace(DTNode<V, E> rootSmall) {
        DTNode<V, E> newRoot = null; // a potential new root in case no replacement edge is found

        // iterate over the nodes of rootSmall using a BFS.
        ArrayDeque<DTNode<V, E>> queue = new ArrayDeque<>();
        queue.offer(rootSmall);

        while (!queue.isEmpty()) {
            DTNode<V, E> n = queue.poll();

            // search for a new centroid
            if (n != rootSmall && n.size() > rootSmall.size() / 2) {
                newRoot = n;
            }

            // search for a replacement edge
            for (Edge<V, E> nonTreeEdge : n.getNonTreeEdges()) {
                DTNode<V, E> oppNode = nonTreeEdge.opposite(n);
                DTNode<V, E> oppRoot = oppNode.findRoot();

                if (oppRoot != rootSmall) {
                    // found a replacement edge
                    removeNonTreeEdge(nonTreeEdge);
                    insertTreeEdge(rootSmall, n, oppRoot, oppNode, nonTreeEdge);

                    return;
                }
            }

            // add all children to the queue
            DTNode<V, E> child = n.getFirstChild();
            while (child != null) {
                queue.add(child);
                child = child.getNextSibling();
            }
        }

        // fix centroid property
        if (newRoot != null) {
            newRoot.makeRoot(true);
        }
    }

    /**
     * Remove a non tree edge between {@code edge.nodeU} and {@code edge.nodeV}.
     *
     * @param edge the edge to remove.
     */
    private void removeNonTreeEdge(Edge<V, E> edge) {
        edge.nodeU().removeNonTreeEdge(edge);
        edge.nodeV().removeNonTreeEdge(edge);
    }

    /**
     * Add {@code newRoot} in the set of {@link #roots}.
     *
     * @param newRoot the root to add
     */
    private void addRoot(DTNode<V, E> newRoot) {
        roots.add(newRoot);
    }

    /**
     * Remove {@code root} from the set of root.
     *
     * @param root the root to remove
     */
    private void removeRoot(DTNode<V, E> root) {
        roots.remove(root);
    }

    /**
     * Remove {@code oldRoot} and replace with {@code newRoot}.
     *
     * @param oldRoot the old root to replace with {@code newRoot}
     * @param newRoot the new root to add
     */
    void replaceRoot(DTNode<V, E> oldRoot, DTNode<V, E> newRoot) {
        roots.remove(oldRoot);
        roots.add(newRoot);
    }

    @Override
    public boolean containsVertex(V vertex) {
        return vertexToTreeNode.containsKey(vertex);
    }

    @Override
    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
    }

    @Override
    public V getEdgeSource(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge<V, E> e -> e.nodeU().getVertex();
        };
    }

    @Override
    public V getEdgeTarget(E edge) {
        return switch (edges.get(edge)) {
            case null -> null;
            case Edge<V, E> e -> e.nodeV().getVertex();
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
        return vertexToTreeNode.get(v).getNeighborEdges();
    }

    @Override
    public int getNeighborEdgeCountOf(V v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<V> getVertices() {
        return vertexToTreeNode.keySet();
    }

    @Override
    public List<V> getNeighborVerticesOf(V v) {
        throw new UnsupportedOperationException();
    }

    public int getNbConnectedComponent() {
        return roots.size();
    }

    public Set<V> componentView(V vertex) {
        return getNodeOrThrow(vertex).componentView();
    }

    /**
     * Builds a list of components, sort it by size in reverse order and update index for every root.
     *
     * @return a list of components sorted by size in reverse order.
     */
    public List<Set<V>> allComponents() {
        List<Set<V>> components = new ArrayList<>(roots.size());
        for (DTNode<V, E> root : roots) {
            components.add(root.componentView());
        }

        components.sort(Comparator.<Set<V>>comparingInt(Set::size).reversed());
        for (int i = 0; i < components.size(); i++) {
            ComponentView<V, E> comp = (ComponentView<V, E>) components.get(i);
            comp.setIndex(i);
        }

        return components;
    }

    Set<DTNode<V, E>> getRoots() {
        return roots;
    }
}
