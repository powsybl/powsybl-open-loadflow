/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import gnu.trove.list.array.TIntArrayList;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AdjacencyListGraphModel<V, E> implements IAdjacencyListGraphModel<V, E> {

    private final ToIntFunction<V> numGetter;

    private final Map<V, TIntArrayList> adjacencyList = new LinkedHashMap<>();
    private final Map<V, Set<E>> neighbourEdges = new LinkedHashMap<>();
    private final Map<E, Edge<V>> edges = new LinkedHashMap<>();

    public AdjacencyListGraphModel(ToIntFunction<V> numGetter) {
        this.numGetter = Objects.requireNonNull(numGetter);
    }

    @Override
    public void addEdge(V v1, V v2, E e) {
        Edge<V> newEdge = new Edge<>(v1, v2);
        Edge<V> currentEdge = edges.putIfAbsent(e, newEdge);

        if (currentEdge == null) {
            neighbourEdges.get(v1).add(e);
            neighbourEdges.get(v2).add(e);
            adjacencyList.get(v1).add(numGetter.applyAsInt(v2));
            adjacencyList.get(v2).add(numGetter.applyAsInt(v1));
        }
    }

    @Override
    public void removeEdge(E e) {
        Edge<V> edge = edges.remove(e);

        if (edge != null) {
            neighbourEdges.get(edge.source).remove(e);
            neighbourEdges.get(edge.target).remove(e);
            adjacencyList.get(edge.source).remove(numGetter.applyAsInt(edge.target));
            adjacencyList.get(edge.target).remove(numGetter.applyAsInt(edge.source));
        }
    }

    @Override
    public void addVertex(V v) {
        var current = adjacencyList.putIfAbsent(v, new TIntArrayList(10));

        if (current == null) {
            neighbourEdges.put(v, new HashSet<>());
        }
    }

    @Override
    public void removeVertex(V v) {
        adjacencyList.remove(v);
        neighbourEdges.remove(v);
    }

    @Override
    public boolean containsVertex(V vertex) {
        return adjacencyList.containsKey(vertex);
    }

    @Override
    public boolean containsEdge(E edge) {
        return edges.containsKey(edge);
    }

    @Override
    public V getEdgeSource(E edge) {
        return edges.get(edge).source;
    }

    @Override
    public V getEdgeTarget(E edge) {
        return edges.get(edge).target;
    }

    @Override
    public Set<E> getNeighborEdgesOf(V v) {
        return neighbourEdges.get(v);
    }

    @Override
    public Set<V> getVertices() {
        return adjacencyList.keySet();
    }

    @Override
    public Map<V, TIntArrayList> getAdjacencyList() {
        return adjacencyList;
    }

    private record Edge<V>(V source, V target) { }
}
