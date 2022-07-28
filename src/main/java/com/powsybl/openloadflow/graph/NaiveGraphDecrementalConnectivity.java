/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NaiveGraphDecrementalConnectivity<V, E> implements GraphDecrementalConnectivity<V, E> {

    private final Graph<V, E> graph = new Pseudograph<>(null, null, false);

    private final List<Triple<V, E, V>> cutEdges = new ArrayList<>();

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
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.addEdge(vertex1, vertex2, edge);
        invalidateComponents();
    }

    @Override
    public void cut(E edge) {
        if (cutEdges.stream().anyMatch(t -> t.getMiddle().equals(edge))) {
            throw new PowsyblException("Edge already cut: " + edge);
        }
        if (!graph.containsEdge(edge)) {
            throw new PowsyblException("No such edge in graph: " + edge);
        }
        V vertex1 = graph.getEdgeSource(edge);
        V vertex2 = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);
        cutEdges.add(Triple.of(vertex1, edge, vertex2));
        invalidateComponents();
    }

    @Override
    public void reset() {
        for (Triple<V, E, V> cutEdge : cutEdges) {
            graph.addEdge(cutEdge.getLeft(), cutEdge.getRight(), cutEdge.getMiddle());
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
