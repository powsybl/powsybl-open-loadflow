/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.Set;

/**
 * Interface for incremental and decremental connectivity computations, through a mechanism which records the
 * topological changes which later need to be undone.
 * To start recording topological changes, call {@link #startTemporaryChanges}.
 * A call to {@link #undoTemporaryChanges} will then undo in reverse order all the graph modifications which occurred since last
 * call to {@link #startTemporaryChanges}.
 * This allows several levels of temporary changes - even if some implementations might not support it.
 * <pre>
 *     connectivity.addVertex(v1);
 *     connectivity.addVertex(v2);
 *     connectivity.addEdge(v1, v2, e12);
 *
 *     connectivity.startTemporaryChanges();
 *     connectivity.removeEdge(e12);
 *
 *        connectivity.startTemporaryChanges();
 *        connectivity.addVertex(v3);
 *        connectivity.addEdge(v1, v3, e13);
 *        connectivity.undoTemporaryChanges();
 *
 *     connectivity.undoTemporaryChanges();
 * </pre>
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public interface GraphConnectivity<V, E> {

    void addVertex(V vertex);

    void addEdge(V vertex1, V vertex2, E edge);

    void removeEdge(E edge);

    boolean supportTemporaryChangesNesting();

    /**
     * Start recording topological changes to undo them later by a {@link #undoTemporaryChanges} call.
     */
    void startTemporaryChanges();

    /**
     * Undo all the connectivity changes (possibly none) since last call to {@link #startTemporaryChanges}.
     */
    void undoTemporaryChanges();

    /**
     * Return the number of the connected component containing the given vertex, knowing that the number represents
     * the size ranking of the related connected component in the graph, 0 being the main connected component. Hence, the
     * greater the component number is, the smaller the number of vertices in that component.
     * @param vertex the vertex whose connected component number is looked for
     * @return the number of the related connected component
     */
    int getComponentNumber(V vertex);

    /**
     * Set the main component with given vertex.
     * The connected component relative to this vertex is considered as being the main component.
     * If not set, the main component is considered to be the biggest component.
     * This main component cannot be changed if any temporary changes are ongoing.
     * @param mainComponentVertex vertex defining main component
     */
    void setMainComponentVertex(V mainComponentVertex);

    /**
     * Return the number of connected components
     */
    int getNbConnectedComponents();

    /**
     * Return the connected component set of given vertex
     */
    Set<V> getConnectedComponent(V vertex);

    /**
     * Return the vertices which were removed from main component by last temporary changes.
     * The main component is set by calling setMainComponentVertex, or if not set it is the biggest connected component.
     */
    Set<V> getVerticesRemovedFromMainComponent();

    /**
     * Return the edges which were removed from main component by last temporary changes.
     * The main component is set by calling setMainComponentVertex, or if not set it is the biggest connected component.
     */
    Set<E> getEdgesRemovedFromMainComponent();

    /**
     * Return the vertices which were added to main component by last temporary changes.
     * The main component is set by calling setMainComponentVertex, or if not set it is the biggest connected component.
     */
    Set<V> getVerticesAddedToMainComponent();

    /**
     * Return the edges which were added to main component by last temporary changes.
     * The main component is set by calling setMainComponentVertex, or if not set it is the biggest connected component.
     */
    Set<E> getEdgesAddedToMainComponent();
}
