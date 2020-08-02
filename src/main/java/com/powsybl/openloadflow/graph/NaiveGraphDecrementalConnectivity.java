/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NaiveGraphDecrementalConnectivity<V> implements GraphDecrementalConnectivity<V> {

    private final UndirectedGraph<V, Object> graph = new Pseudograph<>(Object.class);

    private final List<Pair<V, V>> cutEdges = new ArrayList<>();

    private int[] components;

    private final ToIntFunction<V> numGetter;

    public NaiveGraphDecrementalConnectivity(ToIntFunction<V> numGetter) {
        this.numGetter = Objects.requireNonNull(numGetter);
    }

    private void updateComponents() {
        if (components == null) {
            components = new int[graph.vertexSet().size()];
            List<Set<V>> componentSets = new ConnectivityInspector<>(graph).connectedSets();
            for (int componentIndex = 0; componentIndex < componentSets.size(); componentIndex++) {
                Set<V> vertices = componentSets.get(componentIndex);
                for (V vertex : vertices) {
                    components[numGetter.applyAsInt(vertex)] = componentIndex;
                }
            }
        }
    }

    private void invalidateComponents() {
        components = null;
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
        invalidateComponents();
    }

    @Override
    public void addEdge(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.addEdge(vertex1, vertex2, new Object());
        invalidateComponents();
    }

    @Override
    public void cut(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.removeEdge(vertex1, vertex2);
        cutEdges.add(Pair.of(vertex1, vertex2));
        invalidateComponents();
    }

    @Override
    public void reset() {
        for (Pair<V, V> cutEdge : cutEdges) {
            graph.addEdge(cutEdge.getLeft(), cutEdge.getRight(), new Object());
        }
        cutEdges.clear();
        invalidateComponents();
    }

    @Override
    public boolean isConnected(V vertex1, V vertex2) {
        updateComponents();
        int component1 = components[numGetter.applyAsInt(vertex1)];
        int component2 = components[numGetter.applyAsInt(vertex2)];
        return component1 == component2;
    }
}
