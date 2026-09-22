/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtree;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphModification;
import com.powsybl.openloadflow.graph.dtree.StateMap.State;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

/**
 * Contains modifications performed on the graph between
 * the last call to {@link GraphConnectivity#startTemporaryChanges}
 * and the current instant. It stores a stack of {@link GraphModification}
 * and optionally the set of vertices and edges added to the
 * main component or removed from it.
 */
public class Modifications<V, E> implements Iterable<GraphModification<V, E>> {

    private final DTGraph<V, E> graph;

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

    Modifications(DTGraph<V, E> graph, DTNode<V, E> mainComponentVertex, boolean fictitiousMCV, boolean computeComparisons) {
        this.graph = graph;
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
        modifications.push(modification);
    }

    public void beforeInsertingEdgeInComponent(DTNode<V, E> rootNodeU, Edge<V, E> edge) {
        if (isInMainComponent(rootNodeU)) {
            markEdgeAdded(edge.edgeData());
        }
    }

    public void beforeInsertingTreeEdge(DTNode<V, E> rootU, DTNode<V, E> rootV, Edge<V, E> edge) {
        if (isInMainComponent(rootV)) {
            markEdgeAdded(edge.edgeData());
            markAllAdded(rootU);
        } else if (isInMainComponent(rootU)) {
            markEdgeAdded(edge.edgeData());
            markAllAdded(rootV);
        }
    }

    public void afterInsertingTreeEdge(DTNode<V, E> mergedTree) {
        if (isMainComponentVertexFictitious) {
            maybeBiggestTreeChanged(mergedTree);
        }
    }

    public void afterRemovingNonTreeEdge(Edge<V, E> edge) {
        if (isInMainComponent(edge.nodeU())) {
            markEdgeRemoved(edge.edgeData());
        }
    }

    public void afterRemovingTreeEdge(boolean replacementEdgeFound, DTNode<V, E> smallRoot, DTNode<V, E> largeRoot, Edge<V, E> edge) {
        if (replacementEdgeFound) {
            if (isInMainComponent(smallRoot)) {
                markEdgeRemoved(edge.edgeData());
            }
        } else {
            // /!\ small and large can be both in the main component (when a replacement edge was found)
            // However, in this case, we only need one of the two variables to be true to have
            // the correct behavior (i.e. only the removedEdge is marked as removed).
            // When there is no replacement edge, small and large cannot be simultaneously in the main
            // component as there are in two distinct components.
            boolean smallInMain = isInMainComponent(smallRoot);
            boolean largeInMain = !smallInMain && isInMainComponent(largeRoot); // avoid computing isInMainComponent if we know that small is in the main component

            if (smallInMain || largeInMain) {
                markEdgeRemoved(edge.edgeData());
            }

            if (isMainComponentVertexFictitious) {
                maybeBiggestTreeChanged(graph.getBiggestRoot());
            }

            if (largeInMain) {
                markAllRemoved(smallRoot);
            } else if (smallInMain) {
                markAllRemoved(largeRoot);
            }
        }
    }

    public void afterTreesDisconnected() {
        if (isMainComponentVertexFictitious) {
            maybeBiggestTreeChanged(graph.getBiggestRoot());
        }
    }

    /**
     * Change the main component vertex to the specified one.
     *
     * @param mainComponentVertex new vertex identifying the main component.
     */
    public void setMainComponentVertex(V mainComponentVertex) {
        if (verticesState == null || edgesState == null) {
            return;
        }

        if (this.mainComponentNode.getVertex() != mainComponentVertex) {
            // two things to do:
            // 1. check if the new main component vertex was in the main component before temporary changes.
            // 2. if the main component vertex isn't in the current main component vertex, we need to
            //    update state of edges and vertices

            DTNode<V, E> oldComponentRoot = this.mainComponentNode.findRoot();
            DTNode<V, E> mainComponentNode = graph.getNodeOrThrow(mainComponentVertex);
            DTNode<V, E> newComponentRoot = mainComponentNode.findRoot();

            if (oldComponentRoot != newComponentRoot) {
                // the new main component vertex isn't in the current main component.
                // But that doesn't mean it wasn't in the main component before starting temporary changes,
                // it may have been removed.
                if (verticesState.get(mainComponentVertex) != State.REMOVED) {
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
        if (edgesState != null) {
            edgesState.markAdded(edge);
        }
    }

    public void markEdgeRemoved(E edge) {
        if (edgesState != null) {
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
        if (verticesState == null || edgesState == null) {
            return;
        }

        for (DFSIterator<V, E> it = new DFSIterator<>(root); it.hasNext();) {
            V vertex = it.next();
            verticesState.mark(vertex, newState);

            DTNode<V, E> node = it.node();
            if (node.getParentEdge() != null) {
                edgesState.mark(node.getParentEdge().edgeData(), newState);
            }

            for (Edge<V, E> nte : node.getNonTreeEdges()) {
                if (nte.nodeU() == it.node()) { // only if current node is edge source
                    edgesState.mark(nte.edgeData(), newState);
                }
            }

            // we don't mark child tree edges as removed
            // because for each child tree edge, there is a parentEdge
            // so if we mark a parent edge as removed, we also mark
            // the corresponding child tree edge as removed
        }
    }

    private void maybeBiggestTreeChanged(DTNode<V, E> currentBiggestRoot) {
        DTNode<V, E> mainComponentVertexTree = mainComponentNode.findRoot();
        if (currentBiggestRoot.size() > mainComponentVertexTree.size()) {
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

    @Override
    public Iterator<GraphModification<V, E>> iterator() {
        return modifications.iterator();
    }
}
