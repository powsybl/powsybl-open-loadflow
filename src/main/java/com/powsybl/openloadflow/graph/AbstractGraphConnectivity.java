/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.jgrapht.Graph;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public abstract class AbstractGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private final Graph<V, E> graph = new Pseudograph<>(null, null, false);

    private final Deque<Deque<GraphModification<V, E>>> graphModifications = new ArrayDeque<>();

    protected List<Set<V>> componentSets;

    protected abstract void updateConnectivity(EdgeRemove<V, E> edgeRemove);

    protected abstract void updateConnectivity(EdgeAdd<V, E> edgeAdd);

    protected abstract void updateConnectivity(VertexAdd<V, E> vertexAdd);

    protected abstract void resetConnectivity(Deque<GraphModification<V, E>> m);

    protected abstract void updateComponents();

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        VertexAdd<V, E> vertexAdd = new VertexAdd<>(vertex);
        vertexAdd.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(vertexAdd);
            updateConnectivity(vertexAdd);
        }
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        EdgeAdd<V, E> edgeAdd = new EdgeAdd<>(vertex1, vertex2, edge);
        edgeAdd.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(edgeAdd);
            updateConnectivity(edgeAdd);
        }
    }

    @Override
    public void removeEdge(E edge) {
        if (!graph.containsEdge(edge)) {
            throw new PowsyblException("No such edge in graph: " + edge);
        }
        V vertex1 = graph.getEdgeSource(edge);
        V vertex2 = graph.getEdgeTarget(edge);
        EdgeRemove<V, E> edgeRemove = new EdgeRemove<>(vertex1, vertex2, edge);
        edgeRemove.apply(graph);
        if (!graphModifications.isEmpty()) {
            graphModifications.peekLast().add(edgeRemove);
            updateConnectivity(edgeRemove);
        }
    }

    @Override
    public void startTemporaryChanges() {
        graphModifications.add(new ArrayDeque<>());
    }

    @Override
    public void undoTemporaryChanges() {
        if (graphModifications.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }
        Deque<GraphModification<V, E>> m = graphModifications.pollLast();
        resetConnectivity(m);
        m.descendingIterator().forEachRemaining(gm -> gm.undo(graph));
    }

    @Override
    public int getComponentNumber(V vertex) {
        checkSaved();
        checkVertex(vertex);
        updateComponents();
        return getQuickComponentNumber(vertex);
    }

    protected abstract int getQuickComponentNumber(V vertex);

    @Override
    public int getNbConnectedComponents() {
        checkSaved();
        updateComponents();
        return componentSets.size();
    }

    @Override
    public Collection<Set<V>> getSmallComponents() {
        checkSaved();
        updateComponents();
        return componentSets.subList(1, componentSets.size());
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        int componentNumber = getComponentNumber(vertex);
        return componentSets.get(componentNumber);
    }

    @Override
    public Set<V> getNonConnectedVertices(V vertex) {
        Set<V> connectedComponent = getConnectedComponent(vertex);
        return componentSets.stream()
                .filter(component -> component != connectedComponent)
                .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public Graph<V, E> getGraph() {
        return graph;
    }

    protected Deque<Deque<GraphModification<V, E>>> getGraphModifications() {
        return graphModifications;
    }

    protected void checkSaved() {
        if (graphModifications.isEmpty()) {
            throw new PowsyblException("Cannot compute connectivity without a saved state, please call GraphConnectivity::save at least once beforehand");
        }
    }

    protected void checkVertex(V vertex) {
        if (!graph.containsVertex(vertex)) {
            throw new AssertionError("given vertex " + vertex + " is not in the graph");
        }
    }
}
