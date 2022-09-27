/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import java.util.Collection;
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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public interface GraphConnectivity<V, E> {

    void addVertex(V vertex);

    void addEdge(V vertex1, V vertex2, E edge);

    void removeEdge(E edge);

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
     * Return the collection of "small" connected components, meaning all the connected components except the biggest one (size-wise).
     * @return the collection of small connected components
     */
    Collection<Set<V>> getSmallComponents();

    int getNbConnectedComponents();

    Set<V> getConnectedComponent(V vertex);

    Set<V> getNonConnectedVertices(V vertex);

    Set<V> getVerticesRemovedFromMainComponent();

    Set<E> getEdgesRemovedFromMainComponent();

    Set<V> getVerticesAddedToMainComponent();

    Set<E> getEdgesAddedToMainComponent();
}
