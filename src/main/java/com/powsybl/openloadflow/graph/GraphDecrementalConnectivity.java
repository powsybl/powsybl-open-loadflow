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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface GraphDecrementalConnectivity<V> {

    void addVertex(V vertex);

    void addEdge(V vertex1, V vertex2);

    /**
     * Cut one edge between given vertices
     * @param vertex1 first vertex, from or towards which the edge has been constructed
     * @param vertex2 second vertex, towards or from which the edge has been constructed
     */
    void cut(V vertex1, V vertex2);

    /**
     * Reset all the cut done previously in the graph
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

    Set<V> getConnectedComponent(V vertex);

    Set<V> getNonConnectedVertices(V vertex);
}
