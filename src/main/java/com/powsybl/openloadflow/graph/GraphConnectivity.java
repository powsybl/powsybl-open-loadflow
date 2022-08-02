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
 * Interface for incremental and decremental connectivity computations, with a save / reset mechanism.
 * The connectivity can be saved thanks to a call to {@link #save}. A call to {@link #reset} will then restore the
 * connectivity state saved by undoing in reverse order all the graph modifications which occurred since last reset
 * or last save.
 * Two (resp. N) consecutive calls to {@link #reset} will restore the connectivity of the second to last (resp. Nth to
 * last) save. Consecutive meaning without any graph modifications in between.
 * Hence, USE CAUTIOUSLY the call to {@link #reset}! You should check beforehand if there are some graph modifications
 * to undo. Indeed, if no graph modifications have occurred since last reset or last save you end up with the
 * connectivity state corresponding to the second to last save.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public interface GraphConnectivity<V, E> {

    void addVertex(V vertex);

    void addEdge(V vertex1, V vertex2, E edge);

    void removeEdge(E edge);

    /**
     * Save the connectivity state
     */
    void save();

    /**
     * Restore the connectivity state of last save, but ONLY IF topological changes have been made.
     * If no topological changes have occurred, restore the connectivity state to the second to last save.
     */
    void reset();

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
}
