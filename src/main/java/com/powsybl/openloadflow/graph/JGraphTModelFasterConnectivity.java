/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.math.graph.GraphUtil;
import gnu.trove.list.array.TIntArrayList;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class JGraphTModelFasterConnectivity<V, E> implements GraphModel<V, E> {

    private final JGraphTModel<V, E> delegate = new JGraphTModel<>();

    private final ToIntFunction<V> vertexNumGetter;

    private final Map<V, TIntArrayList> adjacencyList = new LinkedHashMap<>();

    public JGraphTModelFasterConnectivity(ToIntFunction<V> vertexNumGetter) {
        this.vertexNumGetter = Objects.requireNonNull(vertexNumGetter);
    }

    @Override
    public void addEdge(V v1, V v2, E e) {
        delegate.addEdge(v1, v2, e);
        adjacencyList.get(v1).add(vertexNumGetter.applyAsInt(v2));
        adjacencyList.get(v2).add(vertexNumGetter.applyAsInt(v1));
    }

    @Override
    public void removeEdge(E e) {
        V edgeSource = getEdgeSource(e);
        V edgeTarget = getEdgeTarget(e);
        adjacencyList.get(edgeSource).remove(vertexNumGetter.applyAsInt(edgeTarget));
        adjacencyList.get(edgeTarget).remove(vertexNumGetter.applyAsInt(edgeSource));
        delegate.removeEdge(e);
    }

    @Override
    public void addVertex(V v) {
        delegate.addVertex(v);
        adjacencyList.put(v, new TIntArrayList(10));
    }

    @Override
    public void removeVertex(V v) {
        delegate.removeVertex(v);
        adjacencyList.remove(v);
    }

    @Override
    public boolean containsVertex(V vertex) {
        return delegate.containsVertex(vertex);
    }

    @Override
    public boolean containsEdge(E edge) {
        return delegate.containsEdge(edge);
    }

    @Override
    public V getEdgeSource(E edge) {
        return delegate.getEdgeSource(edge);
    }

    @Override
    public V getEdgeTarget(E edge) {
        return delegate.getEdgeTarget(edge);
    }

    @Override
    public Set<E> getEdgesBetween(V vertex1, V vertex2) {
        return delegate.getEdgesBetween(vertex1, vertex2);
    }

    @Override
    public Set<E> getEdges() {
        return delegate.getEdges();
    }

    @Override
    public Set<E> getNeighborEdgesOf(V v) {
        return delegate.getNeighborEdgesOf(v);
    }

    @Override
    public int getNeighborEdgeCountOf(V v) {
        return delegate.getNeighborEdgeCountOf(v);
    }

    @Override
    public Set<V> getVertices() {
        return delegate.getVertices();
    }

    @Override
    public List<V> getNeighborVerticesOf(V v) {
        return delegate.getNeighborVerticesOf(v);
    }

    @Override
    public List<Set<V>> calculateConnectedSets() {
        TIntArrayList[] adjacencyListArray = new TIntArrayList[adjacencyList.size()];
        for (Map.Entry<V, TIntArrayList> entry : this.adjacencyList.entrySet()) {
            V vertex = entry.getKey();
            TIntArrayList adj = entry.getValue();
            adjacencyListArray[vertexNumGetter.applyAsInt(vertex)] = adj;
        }
        GraphUtil.ConnectedComponentsComputationResult result = GraphUtil.computeConnectedComponents(adjacencyListArray);
        List<Set<V>> connectedSets = new ArrayList<>();
        for (int size : result.getComponentSize()) {
            connectedSets.add(HashSet.newHashSet(size));
        }
        int[] componentNum = result.getComponentNumber();
        for (V vertex : this.adjacencyList.keySet()) {
            int v = vertexNumGetter.applyAsInt(vertex);
            connectedSets.get(componentNum[v]).add(vertex);
        }
        return connectedSets;
    }
}
