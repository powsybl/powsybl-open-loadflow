/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

import java.util.Objects;
import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ValidatorGraphConnectivity<V, E> extends AbstractSpyGraphConnectivity<V, E> {

    private final GraphConnectivityFactory<V, E> checkerFactory;
    private GraphConnectivity<V, E> checker;
    private int step;

    public ValidatorGraphConnectivity(GraphConnectivityFactory<V, E> checkerFactory) {
        this.checkerFactory = checkerFactory;
    }

    @Override
    public void notifyOperation(int operation) {
        this.step = operation;
    }

    @Override
    public void setDelegate(GraphConnectivity<V, E> delegate) {
        super.setDelegate(delegate);
        this.checker = checkerFactory.create();
    }

    @Override
    public void addVertex(V vertex) {
        checker.addVertex(vertex);
        delegate.addVertex(vertex);
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        checker.addEdge(vertex1, vertex2, edge);
        delegate.addEdge(vertex1, vertex2, edge);
    }

    @Override
    public void removeEdge(E edge) {
        checker.removeEdge(edge);
        delegate.removeEdge(edge);
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        boolean expected = checker.supportTemporaryChangesNesting();
        boolean current = delegate.supportTemporaryChangesNesting();
        assertEquals(expected, current);
        return current;
    }

    @Override
    public void startTemporaryChanges(boolean computeComparisons) {
        checker.startTemporaryChanges(computeComparisons);
        delegate.startTemporaryChanges(computeComparisons);
    }

    @Override
    public void undoTemporaryChanges() {
        checker.undoTemporaryChanges();
        delegate.undoTemporaryChanges();
    }

    @Override
    public int getComponentNumber(V vertex) {
        int expected = checker.getComponentNumber(vertex);
        int current = delegate.getComponentNumber(vertex);
        assertEquals(expected, current);
        return current;
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        checker.setMainComponentVertex(mainComponentVertex);
        delegate.setMainComponentVertex(mainComponentVertex);
    }

    @Override
    public int getNbConnectedComponents() {
        int expected = checker.getNbConnectedComponents();
        int current = delegate.getNbConnectedComponents();
        assertEquals(expected, current);
        return current;
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        Set<V> expected = checker.getConnectedComponent(vertex);
        Set<V> current = delegate.getConnectedComponent(vertex);
        assertEquals(expected, current);
        return current;
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        Set<V> expected = checker.getLargestConnectedComponent();
        Set<V> current = delegate.getLargestConnectedComponent();
        assertEquals(expected, current);
        return current;
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        Set<V> expected = checker.getVerticesRemovedFromMainComponent();
        Set<V> current = delegate.getVerticesRemovedFromMainComponent();
        assertEquals(expected, current);
        return current;
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        Set<E> expected = checker.getEdgesRemovedFromMainComponent();
        Set<E> current = delegate.getEdgesRemovedFromMainComponent();
        assertEquals(expected, current);
        return current;
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        Set<V> expected = checker.getVerticesAddedToMainComponent();
        Set<V> current = delegate.getVerticesAddedToMainComponent();
        assertEquals(expected, current);
        return current;
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        Set<E> expected = checker.getEdgesAddedToMainComponent();
        Set<E> current = delegate.getEdgesAddedToMainComponent();
        assertEquals(expected, current);
        return current;
    }

    private void assertEquals(Object obj1, Object obj2) {
        if (!Objects.equals(obj1, obj2)) {
            throw new AssertionError("%s != %s: %s and %s have inconsistent results at step %d".formatted(obj1, obj2, checker.getClass(), delegate.getClass(), step));
        }
    }
}
