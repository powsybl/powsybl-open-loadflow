/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class ModificationsContext<V, E> {

    private final Deque<GraphModification<V, E>> modifications = new ArrayDeque<>();
    private final Function<V, Set<V>> verticesNotInMainComponentGetter;
    private Set<V> verticesNotInMainComponentBefore;
    private Set<V> verticesAddedToMainComponent;
    private Set<V> verticesRemovedFromMainComponent;
    private Set<E> edgesAddedToMainComponent;
    private Set<E> edgesRemovedFromMainComponent;
    private Map<E, AbstractEdgeModification<V, E>> edgeFirstModificationMap;
    private V mainComponentVertex;

    public ModificationsContext(Function<V, Set<V>> verticesNotInMainComponentGetter, V mainComponentVertex) {
        this.mainComponentVertex = mainComponentVertex;
        this.verticesNotInMainComponentGetter = verticesNotInMainComponentGetter;
    }

    public void computeVerticesNotInMainComponentBefore() {
        this.verticesNotInMainComponentBefore = verticesNotInMainComponentGetter.apply(mainComponentVertex);
    }

    public void add(GraphModification<V, E> graphModification) {
        invalidateComparisons();
        modifications.add(graphModification);
    }

    private void invalidateComparisons() {
        verticesAddedToMainComponent = null;
        edgesAddedToMainComponent = null;
        verticesRemovedFromMainComponent = null;
        edgesRemovedFromMainComponent = null;
        edgeFirstModificationMap = null;
    }

    public Deque<GraphModification<V, E>> getModifications() {
        return modifications;
    }

    public Set<E> getEdgesRemovedFromMainComponent(Graph<V, E> graph) {
        if (edgesRemovedFromMainComponent == null) {
            edgesRemovedFromMainComponent = computeEdgesRemovedFromMainComponent(graph);
        }
        return edgesRemovedFromMainComponent;
    }

    public Set<V> getVerticesRemovedFromMainComponent() {
        if (verticesRemovedFromMainComponent == null) {
            Set<V> result = new HashSet<>(getVerticesNotInMainComponentAfter());
            result.removeAll(verticesNotInMainComponentBefore);
            if (!result.isEmpty()) {
                // remove vertices added in between
                // note that there is no VertexRemove modification, thus we do not need to check if vertex is in the graph in the end
                getAddedVertexStream().forEach(result::remove);
            }
            verticesRemovedFromMainComponent = result;
        }
        return verticesRemovedFromMainComponent;
    }

    public Set<E> getEdgesAddedToMainComponent(Graph<V, E> graph) {
        if (edgesAddedToMainComponent == null) {
            edgesAddedToMainComponent = computeEdgesAddedToMainComponent(graph);
        }
        return edgesAddedToMainComponent;
    }

    public Set<V> getVerticesAddedToMainComponent() {
        if (verticesAddedToMainComponent == null) {
            Set<V> result = new HashSet<>(verticesNotInMainComponentBefore);
            Set<V> verticesNotInMainComponentAfter = getVerticesNotInMainComponentAfter();
            result.removeAll(verticesNotInMainComponentAfter);
            // add vertices added to main component in between
            // note that there is no VertexRemove modification, thus we do not need to check if vertex is in the graph before / in the end
            getAddedVertexStream().filter(addedVertex -> !verticesNotInMainComponentAfter.contains(addedVertex)).forEach(result::add);
            verticesAddedToMainComponent = result;
        }
        return verticesAddedToMainComponent;
    }

    private Set<V> getVerticesNotInMainComponentAfter() {
        return verticesNotInMainComponentGetter.apply(mainComponentVertex);
    }

    private Set<E> computeEdgesRemovedFromMainComponent(Graph<V, E> graph) {
        Set<V> verticesRemoved = getVerticesRemovedFromMainComponent();
        Set<E> result = verticesRemoved.stream().map(graph::edgesOf).flatMap(Set::stream).collect(Collectors.toSet());

        // We need to look in modifications to adjust the computation of the edges above, indeed:
        //  - result contains the edges which were added in the small components
        //  - result is missing the edges removed in main component with an EdgeRemove modification

        computeEdgeFirstModificationMap();

        // Remove the new edges
        modifications.stream().filter(EdgeAdd.class::isInstance).map(m -> ((EdgeAdd<V, E>) m).e)
                .filter(graph::containsEdge) // the edge is in the graph: it was not removed afterwards
                .filter(edgeAdded -> !graphContainedEdgeBefore(edgeAdded)) // the edge did not exist in the graph before the modifications
                .forEach(result::remove);

        // Add edges explicitly removed (with an EdgeRemove modification)
        modifications.stream().filter(EdgeRemove.class::isInstance).map(m -> ((EdgeRemove<V, E>) m).e)
                .filter(edgeRemoved -> !graph.containsEdge(edgeRemoved)) // the edge was not added afterwards
                .filter(this::graphContainedEdgeBefore) // the edge was in the graph before the modifications
                .filter(edgeRemoved -> !verticesNotInMainComponentBefore.contains((edgeFirstModificationMap.get(edgeRemoved)).v1)) // one of the original vertices of the edge was in the main component
                .forEach(result::add);

        return result;
    }

    private Set<E> computeEdgesAddedToMainComponent(Graph<V, E> graph) {
        Set<E> result = getVerticesAddedToMainComponent().stream().map(graph::edgesOf).flatMap(Set::stream).collect(Collectors.toSet());

        // We need to look in modifications to adjust the computation of the edges above
        // Indeed result is missing the edges added in main component with an EdgeAdd modification

        computeEdgeFirstModificationMap();

        // Add edges added to main component in between
        Set<V> verticesNotInMainComponentAfter = getVerticesNotInMainComponentAfter();
        modifications.stream().filter(EdgeAdd.class::isInstance).map(m -> ((EdgeAdd<V, E>) m).e)
                .filter(graph::containsEdge) // the edge is in the graph: it was not removed afterwards
                .filter(edgeAdded -> !graphContainedEdgeBefore(edgeAdded)) // the edge did not exist in the graph before the modifications
                .filter(edgeAdded -> !verticesNotInMainComponentAfter.contains(graph.getEdgeSource(edgeAdded))) // one of the final vertices of the edge is in the main component
                .forEach(result::add);

        return result;
    }

    private void computeEdgeFirstModificationMap() {
        if (edgeFirstModificationMap == null) {
            edgeFirstModificationMap = modifications.stream()
                    .filter(AbstractEdgeModification.class::isInstance)
                    .map(m -> (AbstractEdgeModification<V, E>) m)
                    .collect(Collectors.toMap(m -> m.e, m -> m, (m1, m2) -> m1));
        }
    }

    private boolean graphContainedEdgeBefore(E edge) {
        // If first modification is a EdgeRemove, knowing that the non-effective modifications are not added in the queue,
        // we can conclude that the graph contained the edge before the modifications were applied
        return edgeFirstModificationMap.get(edge) instanceof EdgeRemove;
    }

    private Stream<V> getAddedVertexStream() {
        return modifications.stream().filter(VertexAdd.class::isInstance).map(m -> ((VertexAdd<V, E>) m).v);
    }

    public void setMainComponentVertex(V mainComponentVertex) {
        invalidateComparisons();
        this.mainComponentVertex = mainComponentVertex;
    }

    public boolean isInMainComponentBefore(V vertex) {
        return !verticesNotInMainComponentBefore.contains(vertex);
    }
}
