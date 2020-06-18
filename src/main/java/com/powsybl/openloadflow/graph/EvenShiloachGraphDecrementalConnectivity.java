/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.Pseudograph;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * https://dl.acm.org/doi/10.1145/322234.322235
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EvenShiloachGraphDecrementalConnectivity<V> implements GraphDecrementalConnectivity<V>  {

    private final UndirectedGraph<V, Object> graph = new Pseudograph<>(Object.class);

    private final ToIntFunction<V> numGetter;

    public EvenShiloachGraphDecrementalConnectivity(ToIntFunction<V> numGetter) {
        this.numGetter = Objects.requireNonNull(numGetter);
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
    }

    @Override
    public void addEdge(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.addEdge(vertex1, vertex2, new Object());
    }

    @Override
    public void cut(V vertex1, V vertex2) {

    }

    @Override
    public void reset() {

    }

    @Override
    public boolean isConnected(V vertex1, V vertex2) {
        return false;
    }
}
