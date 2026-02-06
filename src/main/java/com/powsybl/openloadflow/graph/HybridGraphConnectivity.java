/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * A hybrid <code>GraphConnectivity</code> hybrid implementation that use the naive connectivity implementation
 * for incremental changes (slow) and the Even-Shiloach decremental connectivity implementation for decremental changes
 * (fast).
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class HybridGraphConnectivity<V, E> implements GraphConnectivity<V, E> {

    private final NaiveGraphConnectivity<V, E> fullConnectivity;

    private final EvenShiloachGraphDecrementalConnectivity<V, E> decrementalConnectivity = new EvenShiloachGraphDecrementalConnectivity<>();

    public enum Mode {
        FULL, DECREMENTAL
    }

    private Mode mode;

    static class TemporaryChange<V, E> {

        private final Map<E, Pair<V, V>> addedEdges = new LinkedHashMap<>();

        private final Set<E> removedEdges = new LinkedHashSet<>();

        public void addVertex(V vertex) {
            throw new PowsyblException("HybridGraphConnectivity does not support adding vertices after the initial graph construction");
        }

        public Map<E, Pair<V, V>> getAddedEdges() {
            return addedEdges;
        }

        public Set<E> getRemovedEdges() {
            return removedEdges;
        }

        public void addEdge(V vertex1, V vertex2, E edge) {
            removedEdges.remove(edge);
            addedEdges.put(edge, Pair.of(vertex1, vertex2));
        }

        public void removeEdge(E edge) {
            addedEdges.remove(edge);
            removedEdges.add(edge);
        }

        @Override
        public String toString() {
            return "TemporaryChange(addedEdges=" + addedEdges + ", removedEdges=" + removedEdges + ')';
        }
    }

    private final Deque<TemporaryChange<V, E>> temporaryChangesStack = new ArrayDeque<>();

    public HybridGraphConnectivity(ToIntFunction<V> numGetter) {
        fullConnectivity = new NaiveGraphConnectivity<>(numGetter);
    }

    public Mode getMode() {
        return mode;
    }

    private TemporaryChange<V, E> getCurrentTemporaryChange() {
        return temporaryChangesStack.peekLast();
    }

    @Override
    public void addVertex(V vertex) {
        if (temporaryChangesStack.isEmpty()) {
            fullConnectivity.addVertex(vertex);
            decrementalConnectivity.addVertex(vertex);
        } else {
            getCurrentTemporaryChange().addVertex(vertex);
            invalidateConnectivity();
        }
    }

    @Override
    public void addEdge(V vertex1, V vertex2, E edge) {
        if (temporaryChangesStack.isEmpty()) {
            fullConnectivity.addEdge(vertex1, vertex2, edge);
            decrementalConnectivity.addEdge(vertex1, vertex2, edge);
        } else {
            getCurrentTemporaryChange().addEdge(vertex1, vertex2, edge);
            invalidateConnectivity();
        }
    }

    @Override
    public void removeEdge(E edge) {
        if (temporaryChangesStack.isEmpty()) {
            fullConnectivity.removeEdge(edge);
            decrementalConnectivity.removeEdge(edge);
        } else {
            getCurrentTemporaryChange().removeEdge(edge);
            invalidateConnectivity();
        }
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    @Override
    public void startTemporaryChanges() {
        temporaryChangesStack.add(new TemporaryChange<>());
        invalidateConnectivity();
    }

    @Override
    public void undoTemporaryChanges() {
        temporaryChangesStack.removeLast();
        invalidateConnectivity();
    }

    private void invalidateConnectivity() {
        if (mode != null) {
            if (mode == Mode.FULL) {
                fullConnectivity.undoTemporaryChanges();
            } else if (mode == Mode.DECREMENTAL) {
                decrementalConnectivity.undoTemporaryChanges();
            }
            mode = null;
        }
    }

    private boolean mustDoIncrementalConnectivity() {
        for (TemporaryChange<V, E> temporaryChange : temporaryChangesStack) {
            if (!temporaryChange.getAddedEdges().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private GraphConnectivity<V, E> getActiveConnectivity() {
        if (mode == null) {
            if (temporaryChangesStack.isEmpty()) {
                mode = Mode.DECREMENTAL; // whatever
                decrementalConnectivity.startTemporaryChanges();
            } else {
                if (mustDoIncrementalConnectivity()) {
                    // even shiloach does not support adding edges after the initial graph construction
                    // we fallback to naive connectivity
                    mode = Mode.FULL;
                    fullConnectivity.startTemporaryChanges();
                    for (TemporaryChange<V, E> temporaryChange : temporaryChangesStack) {
                        for (E edge : temporaryChange.getRemovedEdges()) {
                            fullConnectivity.removeEdge(edge);
                        }
                        for (var entry : temporaryChange.getAddedEdges().entrySet()) {
                            E edge = entry.getKey();
                            V vertex1 = entry.getValue().getLeft();
                            V vertex2 = entry.getValue().getRight();
                            fullConnectivity.addEdge(vertex1, vertex2, edge);
                        }
                    }

                } else {
                    mode = Mode.DECREMENTAL;
                    decrementalConnectivity.startTemporaryChanges();
                    for (TemporaryChange<V, E> temporaryChange : temporaryChangesStack) {
                        for (E edge : temporaryChange.getRemovedEdges()) {
                            decrementalConnectivity.removeEdge(edge);
                        }
                    }
                }
            }
        }
        return switch (mode) {
            case FULL -> fullConnectivity;
            case DECREMENTAL -> decrementalConnectivity;
        };
    }

    @Override
    public int getComponentNumber(V vertex) {
        return getActiveConnectivity().getComponentNumber(vertex);
    }

    @Override
    public void setMainComponentVertex(V mainComponentVertex) {
        getActiveConnectivity().setMainComponentVertex(mainComponentVertex);
    }

    @Override
    public int getNbConnectedComponents() {
        return getActiveConnectivity().getNbConnectedComponents();
    }

    @Override
    public Set<V> getConnectedComponent(V vertex) {
        return getActiveConnectivity().getConnectedComponent(vertex);
    }

    @Override
    public Set<V> getLargestConnectedComponent() {
        return getActiveConnectivity().getLargestConnectedComponent();
    }

    @Override
    public Set<V> getVerticesRemovedFromMainComponent() {
        return getActiveConnectivity().getVerticesRemovedFromMainComponent();
    }

    @Override
    public Set<E> getEdgesRemovedFromMainComponent() {
        return getActiveConnectivity().getEdgesRemovedFromMainComponent();
    }

    @Override
    public Set<V> getVerticesAddedToMainComponent() {
        return getActiveConnectivity().getVerticesAddedToMainComponent();
    }

    @Override
    public Set<E> getEdgesAddedToMainComponent() {
        return getActiveConnectivity().getEdgesAddedToMainComponent();
    }
}
