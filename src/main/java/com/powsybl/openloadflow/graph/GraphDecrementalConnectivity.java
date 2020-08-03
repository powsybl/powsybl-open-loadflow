/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface GraphDecrementalConnectivity<V> {

    void addVertex(V vertex);

    void addEdge(V vertex1, V vertex2);

    void cut(V vertex1, V vertex2);

    void reset();

    boolean isConnected(V vertex1, V vertex2);

    int getComponentCount();
}
