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
            Set<V> verticesAdded = getVerticesRemovedFromMainComponent(verticesNotInMainComponent);
            edgesRemovedFromMainComponent = verticesAdded.stream().map(graph::edgesOf).flatMap(Set::stream).collect(Collectors.toSet());
        }
        return edgesRemovedFromMainComponent;
    }

    public Set<V> getVerticesRemovedFromMainComponent(List<V> verticesNotInMainComponentAfter) {
        if (verticesRemovedFromMainComponent == null) {
            Set<V> result = new HashSet<>(verticesNotInMainComponentAfter);
            result.removeAll(new HashSet<>(verticesNotInMainComponentBefore));
            verticesRemovedFromMainComponent = result;
        }
        return verticesRemovedFromMainComponent;
    }

    public Set<E> getEdgesAddedToMainComponent(List<V> verticesNotInMainComponentAfter, Graph<V, E> graph) {
        if (edgesAddedToMainComponent == null) {
            Set<V> verticesAdded = getVerticesAddedToMainComponent(verticesNotInMainComponentAfter);
            edgesAddedToMainComponent = verticesAdded.stream().map(graph::edgesOf).flatMap(Set::stream).collect(Collectors.toSet());
        }
        return edgesAddedToMainComponent;
    }

    public Set<V> getVerticesAddedToMainComponent(List<V> verticesNotInMainComponentAfter) {
        if (verticesAddedToMainComponent == null) {
            Set<V> result = new HashSet<>(verticesNotInMainComponentBefore);
            result.removeAll(new HashSet<>(verticesNotInMainComponentAfter));
            verticesAddedToMainComponent = result;
        }
        return verticesAddedToMainComponent;
    }
}
