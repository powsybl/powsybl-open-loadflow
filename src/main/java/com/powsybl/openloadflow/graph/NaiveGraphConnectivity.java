/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
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
public class NaiveGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, JGraphTModelWithAdjacencyList<V, E>> {

    private int[] components;

    private final ToIntFunction<V> numGetter;

    public NaiveGraphConnectivity(ToIntFunction<V> vertexNumGetter) {
        super(new JGraphTModelWithAdjacencyList<>(vertexNumGetter));
        this.numGetter = Objects.requireNonNull(vertexNumGetter);
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return true;
    }

    private List<Set<V>> calculateConnectedSets() {
        Map<V, TIntArrayList> adjacencyList = getGraph().getAdjacencyList();
        TIntArrayList[] adjacencyListArray = new TIntArrayList[adjacencyList.size()];
        for (Map.Entry<V, TIntArrayList> entry : adjacencyList.entrySet()) {
            V vertex = entry.getKey();
            TIntArrayList adj = entry.getValue();
            adjacencyListArray[numGetter.applyAsInt(vertex)] = adj;
        }
        GraphUtil.ConnectedComponentsComputationResult result = GraphUtil.computeConnectedComponents(adjacencyListArray);
        List<Set<V>> connectedSets = new ArrayList<>();
        for (int size : result.getComponentSize()) {
            connectedSets.add(HashSet.newHashSet(size));
        }
        int[] componentNum = result.getComponentNumber();
        for (V vertex : adjacencyList.keySet()) {
            int v = numGetter.applyAsInt(vertex);
            connectedSets.get(componentNum[v]).add(vertex);
        }
        return connectedSets;
    }

    protected void updateComponents() {
        if (components == null) {
            components = new int[getGraph().getVertices().size()];
            componentSets = calculateConnectedSets();
            for (int componentIndex = 0; componentIndex < componentSets.size(); componentIndex++) {
                Set<V> vertices = componentSets.get(componentIndex);
                for (V vertex : vertices) {
                    components[numGetter.applyAsInt(vertex)] = componentIndex;
                }
            }
        }
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {
        invalidateComponents();
    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return components[numGetter.applyAsInt(vertex)];
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {
        invalidateComponents();
    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {
        invalidateComponents();
    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        invalidateComponents();
    }

    private void invalidateComponents() {
        components = null;
    }
}
