/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NaiveGraphDecrementalConnectivity<V> implements GraphDecrementalConnectivity<V> {

    private final Graph<V, Object> graph = new Pseudograph<>(Object.class);

    private final List<Pair<V, V>> cutEdges = new ArrayList<>();

    private int[] components;

    private final ToIntFunction<V> numGetter;

    private List<Set<V>> componentSets;

    public NaiveGraphDecrementalConnectivity(ToIntFunction<V> numGetter) {
        this.numGetter = Objects.requireNonNull(numGetter);
    }

    private void updateComponents() {
        if (components == null) {
            components = new int[graph.vertexSet().size()];
            componentSets = new ConnectivityInspector<>(graph)
                    .connectedSets()
                    .stream()
                    .sorted(Comparator.comparing(Set<V>::size).reversed())
                    .collect(Collectors.toList());
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
        if (vertex1 == null || vertex2 == null) {
            return;
        }
        graph.addEdge(vertex1, vertex2, new Object());
        invalidateComponents();
    }

    @Override
    public void cut(V vertex1, V vertex2) {
        if (vertex1 == null || vertex2 == null) {
            return;
        }
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
    public int getComponentNumber(V vertex) {
        checkVertex(vertex);
        updateComponents();
        return components[numGetter.applyAsInt(vertex)];
    }

    @Override
    public Collection<Set<V>> getSmallComponents() {
        updateComponents();
        return componentSets.subList(1, componentSets.size());
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkVertex(vertex);
        updateComponents();
        return componentSets.get(components[numGetter.applyAsInt(vertex)]);
    }

    @Override
    public Set<V> getNonConnectedVertices(V vertex) {
        checkVertex(vertex);
        updateComponents();
        return componentSets.stream().filter(component -> !component.contains(vertex))
            .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private void checkVertex(V vertex) {
        if (!graph.containsVertex(vertex)) {
            throw new AssertionError("given vertex " + vertex + " is not in the graph");
        }
    }
}
