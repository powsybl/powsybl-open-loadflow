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
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public abstract class AbstractGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private final Graph<V, E> graph = new Pseudograph<>(null, null, false);

    private final Deque<ModificationsContext<V, E>> modificationsContexts = new ArrayDeque<>();

    protected List<Set<V>> componentSets;

    private V mainComponentVertex;

    protected abstract void updateConnectivity(EdgeRemove<V, E> edgeRemove);

    protected abstract void updateConnectivity(EdgeAdd<V, E> edgeAdd);

    protected abstract void updateConnectivity(VertexAdd<V, E> vertexAdd);

    protected abstract void resetConnectivity(Deque<GraphModification<V, E>> m);

    protected abstract void updateComponents();

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        if (graph.containsVertex(vertex)) {
            return;
        }
        VertexAdd<V, E> vertexAdd = new VertexAdd<>(vertex);
        vertexAdd.apply(graph);
        if (!modificationsContexts.isEmpty()) {
            ModificationsContext<V, E> modificationsContext = modificationsContexts.peekLast();
            modificationsContext.add(vertexAdd);
            updateConnectivity(vertexAdd);
        }
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        Objects.requireNonNull(edge);
        if (graph.containsEdge(edge)) {
            return;
        }
        EdgeAdd<V, E> edgeAdd = new EdgeAdd<>(vertex1, vertex2, edge);
        edgeAdd.apply(graph);
        if (!modificationsContexts.isEmpty()) {
            ModificationsContext<V, E> modificationsContext = modificationsContexts.peekLast();
            modificationsContext.add(edgeAdd);
            updateConnectivity(edgeAdd);
        }
    }

    @Override
    public void removeEdge(E edge) {
        Objects.requireNonNull(edge);
        if (!graph.containsEdge(edge)) {
            return;
        }
        V vertex1 = graph.getEdgeSource(edge);
        V vertex2 = graph.getEdgeTarget(edge);
        EdgeRemove<V, E> edgeRemove = new EdgeRemove<>(vertex1, vertex2, edge);
        edgeRemove.apply(graph);
        if (!modificationsContexts.isEmpty()) {
            ModificationsContext<V, E> modificationsContext = modificationsContexts.peekLast();
            modificationsContext.add(edgeRemove);
            updateConnectivity(edgeRemove);
        }
    }

    @Override
    public void startTemporaryChanges() {
        ModificationsContext<V, E> modificationsContext = new ModificationsContext<>();
        modificationsContexts.add(modificationsContext);
        modificationsContext.setVerticesInitiallyNotInMainComponent(getVerticesNotInMainComponent());
    }

    @Override
    public void undoTemporaryChanges() {
        if (modificationsContexts.isEmpty()) {
            throw new PowsyblException("Cannot reset, no remaining saved connectivity");
        }
        ModificationsContext<V, E> m = modificationsContexts.pollLast();
        Deque<GraphModification<V, E>> modifications = m.getModifications();
        resetConnectivity(modifications);
        modifications.descendingIterator().forEachRemaining(gm -> gm.undo(graph));
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

    protected Collection<Set<V>> getSmallComponents() {
        checkSaved();
        updateComponents();
        return componentSets.subList(1, componentSets.size());
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        int componentNumber = getComponentNumber(vertex);
        return componentSets.get(componentNumber);
    }

    protected Set<V> getNonConnectedVertices(V vertex) {
        Set<V> connectedComponent = getConnectedComponent(vertex);
        return componentSets.stream()
                .filter(component -> component != connectedComponent)
                .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public Graph<V, E> getGraph() {
        return graph;
    }

    protected Deque<ModificationsContext<V, E>> getModificationsContexts() {
        return modificationsContexts;
    }

    protected void checkSaved() {
        if (modificationsContexts.isEmpty()) {
            throw new PowsyblException("Cannot compute connectivity without a saved state, please call GraphConnectivity::startTemporaryChanges at least once beforehand");
        }
    }

    protected void checkVertex(V vertex) {
        if (!graph.containsVertex(vertex)) {
            throw new IllegalArgumentException("given vertex " + vertex + " is not in the graph");
        }
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        Set<V> verticesNotInMainComponent = getVerticesNotInMainComponent(); // exception thrown if modificationsContexts empty
        return modificationsContexts.peekLast().getVerticesAddedToMainComponent(verticesNotInMainComponent);
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        Set<V> verticesNotInMainComponent = getVerticesNotInMainComponent(); // exception thrown if modificationsContexts empty
        return modificationsContexts.peekLast().getEdgesAddedToMainComponent(verticesNotInMainComponent, graph);
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        Set<V> verticesNotInMainComponent = getVerticesNotInMainComponent(); // exception thrown if modificationsContexts empty
        return modificationsContexts.peekLast().getVerticesRemovedFromMainComponent(verticesNotInMainComponent);
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        Set<V> verticesNotInMainComponent = getVerticesNotInMainComponent(); // exception thrown if modificationsContexts empty
        return modificationsContexts.peekLast().getEdgesRemovedFromMainComponent(verticesNotInMainComponent, graph);
    }

    private Set<V> getVerticesNotInMainComponent() {
        if (mainComponentVertex != null) {
            return getNonConnectedVertices(mainComponentVertex);
        } else {
            return getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
        }
    }

    public void setMainComponentVertex(V mainComponentVertex) {
        if (!modificationsContexts.isEmpty() && mainComponentVertex != this.mainComponentVertex) {
            throw new PowsyblException("Cannot change main component vertex after starting temporary changes");
        }
        this.mainComponentVertex = mainComponentVertex;
    }
}
