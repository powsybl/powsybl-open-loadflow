/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class ModificationsContext<V, E> {

    private final Deque<GraphModification<V, E>> modifications = new ArrayDeque<>();
    private final List<V> verticesNotInMainComponentBefore = new ArrayList<>();
    private Set<V> verticesAddedToMainComponent;
    private Set<V> verticesRemovedFromMainComponent;
    private Set<E> edgesAddedToMainComponent;
    private Set<E> edgesRemovedFromMainComponent;

    public void setVerticesInitiallyNotInMainComponent(Collection<Set<V>> smallComponents) {
        smallComponents.forEach(this.verticesNotInMainComponentBefore::addAll);
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
    }

    public Deque<GraphModification<V, E>> getModifications() {
        return modifications;
    }

    public Set<E> getEdgesRemovedFromMainComponent(List<V> verticesNotInMainComponent, Graph<V, E> graph) {
        if (edgesRemovedFromMainComponent == null) {
            edgesRemovedFromMainComponent = computeEdgesRemovedFromMainComponent(verticesNotInMainComponent, graph);
        }
        return edgesRemovedFromMainComponent;
    }

    private Set<E> computeEdgesRemovedFromMainComponent(List<V> verticesNotInMainComponent, Graph<V, E> graph) {
        Set<V> verticesRemoved = getVerticesRemovedFromMainComponent(verticesNotInMainComponent);
        Set<E> result = verticesRemoved.stream().map(graph::edgesOf).flatMap(Set::stream).collect(Collectors.toSet());
        // We need to look in modifications to adjust the computation of the edges above, indeed:
        //  - result is missing the edges removed in main component with an EdgeRemove modification
        //  - result contains the edges which were added in the small components
        for (GraphModification<V, E> m : modifications) {
            if (m instanceof EdgeAdd) {
                E edgeAdded = ((EdgeAdd<V, E>) m).e;
                if (graph.containsEdge(edgeAdded)) {
                    // edge was added and not removed afterwards as it is now in the graph
                    // if that edge is in result, it means that it is not now in the main component
                    // BUT it was not in the main component before either
                    result.remove(edgeAdded);
                }
            } else if (m instanceof EdgeRemove) {
                EdgeRemove<V, E> removeEdge = (EdgeRemove<V, E>) m;
                if (!graph.containsEdge(removeEdge.e) && !verticesNotInMainComponentBefore.contains(removeEdge.v1)) {
                    // edge was removed and not added afterwards: this edge should be in result if it was before in the main component
                    // one end of the edge was not in the main component before <=> the edge was not in the main component before
                    result.add(removeEdge.e);
                }
            }
        }
        return result;
    }

    public Set<V> getVerticesRemovedFromMainComponent(List<V> verticesNotInMainComponentAfter) {
        if (verticesRemovedFromMainComponent == null) {
            Set<V> result = new HashSet<>(verticesNotInMainComponentAfter);
            result.removeAll(new HashSet<>(verticesNotInMainComponentBefore));
            if (!result.isEmpty()) {
                // remove vertices added in between
                // note that there is no VertexRemove modification, thus we do not need to check if vertex is in the graph in the end
                getAddedVertexStream().forEach(result::remove);
            }
            verticesRemovedFromMainComponent = result;
        }
        return verticesRemovedFromMainComponent;
    }

    private Stream<V> getAddedVertexStream() {
        return modifications.stream().filter(VertexAdd.class::isInstance).map(m -> ((VertexAdd<V, E>) m).v);
    }

    public Set<E> getEdgesAddedToMainComponent(List<V> verticesNotInMainComponentAfter, Graph<V, E> graph) {
        if (edgesAddedToMainComponent == null) {
            Set<V> verticesAdded = getVerticesAddedToMainComponent(verticesNotInMainComponentAfter);
            Set<E> result = verticesAdded.stream().map(graph::edgesOf).flatMap(Set::stream).collect(Collectors.toSet());
            // Add edges added to main component in between
            modifications.stream().filter(EdgeAdd.class::isInstance).map(m -> (EdgeAdd<V, E>) m)
                    .filter(edgeAdd -> graph.containsEdge(edgeAdd.e)) // edge was not removed afterwards
                    .filter(edgeAdd -> !verticesNotInMainComponentAfter.contains(edgeAdd.v1)) // edge is in the main component at the end
                    .forEach(edgeAdd -> result.add(edgeAdd.e));
            edgesAddedToMainComponent = result;
        }
        return edgesAddedToMainComponent;
    }

    public Set<V> getVerticesAddedToMainComponent(List<V> verticesNotInMainComponentAfter) {
        if (verticesAddedToMainComponent == null) {
            Set<V> result = new HashSet<>(verticesNotInMainComponentBefore);
            result.removeAll(new HashSet<>(verticesNotInMainComponentAfter));
            // add vertices added to main component in between
            // note that there is no VertexRemove modification, thus we do not need to check if vertex is in the graph in the end
            getAddedVertexStream().filter(addedVertex -> !verticesNotInMainComponentAfter.contains(addedVertex)).forEach(result::add);
            verticesAddedToMainComponent = result;
        }
        return verticesAddedToMainComponent;
    }
}
