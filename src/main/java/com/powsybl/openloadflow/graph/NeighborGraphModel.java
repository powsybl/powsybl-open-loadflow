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
public class NeighborGraphModel<V, E> implements IAdjacencyListGraphModel<V, E> {

    private final ToIntFunction<V> numGetter;

    private final Map<V, Neighbourhood<E>> neighbourhoods = new LinkedHashMap<>();
    private final Map<E, Edge<V>> edges = new LinkedHashMap<>();

    public NeighborGraphModel(ToIntFunction<V> numGetter) {
        this.numGetter = Objects.requireNonNull(numGetter);
    }

    @Override
    public void addEdge(V v1, V v2, E e) {
        Edge<V> edge = new Edge<>(v1, v2);
        Edge<V> currentEdge = edges.putIfAbsent(e, edge);

        if (currentEdge == null) {
            Neighbourhood<E> src = neighbourhoods.get(edge.source);
            Neighbourhood<E> target = neighbourhoods.get(edge.target);

            src.neighborEdges.add(e);
            target.neighborEdges.add(e);

            src.adjacentVertices.add(numGetter.applyAsInt(edge.target));
            target.adjacentVertices.add(numGetter.applyAsInt(edge.source));
        }
    }

    @Override
    public void removeEdge(E e) {
        Edge<V> edge = edges.remove(e);

        if (edge != null) {
            Neighbourhood<E> src = neighbourhoods.get(edge.source);
            Neighbourhood<E> target = neighbourhoods.get(edge.target);

            src.neighborEdges.remove(e);
            target.neighborEdges.remove(e);

            src.adjacentVertices.remove(numGetter.applyAsInt(edge.target));
            target.adjacentVertices.remove(numGetter.applyAsInt(edge.source));
        }
    }

    @Override
    public void addVertex(V v) {
        neighbourhoods.computeIfAbsent(v, k -> new Neighbourhood<>());
    }

    @Override
    public void removeVertex(V v) {
        Neighbourhood<E> neighbourhood = neighbourhoods.get(v);

        if (neighbourhood != null) {
            for (E edges : new HashSet<>(neighbourhood.neighborEdges())) {
                removeEdge(edges);
            }
            neighbourhoods.remove(v);
        }
    }

    @Override
    public boolean containsVertex(V vertex) {
        return neighbourhoods.containsKey(vertex);
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
        return neighbourhoods.get(v).neighborEdges();
    }

    @Override
    public Set<V> getVertices() {
        return neighbourhoods.keySet();
    }

    @Override
    public TIntArrayList[] getAdjacencyList() {
        TIntArrayList[] adjacencyListArray = new TIntArrayList[neighbourhoods.size()];
        for (Map.Entry<V, Neighbourhood<E>> entry : neighbourhoods.entrySet()) {
            V vertex = entry.getKey();
            TIntArrayList adj = entry.getValue().adjacentVertices;
            adjacencyListArray[numGetter.applyAsInt(vertex)] = adj;
        }
        return adjacencyListArray;
    }

    private record Neighbourhood<E>(Set<E> neighborEdges, TIntArrayList adjacentVertices) {

        Neighbourhood() {
            this(new HashSet<>(), new TIntArrayList(10));
        }
    }

    private record Edge<V>(V source, V target) { }
}
