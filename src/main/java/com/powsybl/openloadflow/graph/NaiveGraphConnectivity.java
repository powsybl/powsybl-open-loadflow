/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NaiveGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private final Graph<V, E> graph = new Pseudograph<>(null, null, false);

    private final Deque<Deque<GraphModification<V, E>>> graphModifications = new ArrayDeque<>();

    private int[] components;

    private final ToIntFunction<V> numGetter;

    private List<Set<V>> componentSets;

    public NaiveGraphConnectivity(ToIntFunction<V> numGetter) {
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
        GraphModification<V, E> modif = new EdgeAdd<>(vertex1, vertex2, edge);
        modif.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(modif);
            invalidateComponents();
        }
    }

    @Override
    public void removeEdge(E edge) {
        if (!graph.containsEdge(edge)) {
            throw new PowsyblException("No such edge in graph: " + edge);
        }
        V vertex1 = graph.getEdgeSource(edge);
        V vertex2 = graph.getEdgeTarget(edge);
        GraphModification<V, E> modif = new EdgeRemove<>(vertex1, vertex2, edge);
        modif.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(modif);
            invalidateComponents();
        }
    }

    @Override
    public void save() {
        graphModifications.add(new ArrayDeque<>());
    }

    @Override
    public void reset() {
        if (graphModifications.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }
        Deque<GraphModification<V, E>> m = graphModifications.pollLast();
        if (m.isEmpty()) {
            // there are no modifications left at this level: going to lower level.
            if (graphModifications.isEmpty()) {
                throw new PowsyblException("Cannot reset, no remaining saved connectivity");
            }
            m = graphModifications.pollLast();
        }
        graphModifications.add(new ArrayDeque<>());
        m.descendingIterator().forEachRemaining(gm -> gm.undo(graph));
        invalidateComponents();
    }

    @Override
    public int getComponentNumber(V vertex) {
        checkSaved();
        checkVertex(vertex);
        updateComponents();
        return components[numGetter.applyAsInt(vertex)];
    }

    @Override
    public Collection<Set<V>> getSmallComponents() {
        checkSaved();
        updateComponents();
        return componentSets.subList(1, componentSets.size());
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        checkSaved();
        checkVertex(vertex);
        updateComponents();
        return componentSets.get(components[numGetter.applyAsInt(vertex)]);
    }

    @Override
    public Set<V> getNonConnectedVertices(V vertex) {
        checkSaved();
        checkVertex(vertex);
        updateComponents();
        return componentSets.stream().filter(component -> !component.contains(vertex))
            .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private void checkSaved() {
        if (graphModifications.isEmpty()) {
            throw new PowsyblException("Cannot call connectivity computation, no remaining saved connectivity");
        }
    }

    private void checkVertex(V vertex) {
        if (!graph.containsVertex(vertex)) {
            throw new AssertionError("given vertex " + vertex + " is not in the graph");
        }
    }
}
