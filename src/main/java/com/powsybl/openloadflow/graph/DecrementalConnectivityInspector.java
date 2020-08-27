/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class DecrementalConnectivityInspector<V> implements GraphDecrementalConnectivity<V> {

    private Graph<V, Object> graph;

    private final List<Pair<V, V>> cutEdges;
    private final ConnectivityInspector<V, Object> connectivityInspector;
    private Map<V, Integer> vertexToSetNumber;

    public DecrementalConnectivityInspector() {
        DefaultListenableGraph<V, Object> dlg = new DefaultListenableGraph<>(new Pseudograph<>(Object.class));
        this.connectivityInspector = new ConnectivityInspector<>(dlg);
        dlg.addGraphListener(this.connectivityInspector);
        this.connectivityInspector.connectedSets();
        this.graph = dlg;
        this.cutEdges = new ArrayList<>();
    }

    @Override
    public void addVertex(V vertex) {
        Objects.requireNonNull(vertex);
        graph.addVertex(vertex);
        vertexToSetNumber = null;
    }

    @Override
    public void addEdge(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.addEdge(vertex1, vertex2, new Object());
        vertexToSetNumber = null;
    }

    @Override
    public void cut(V vertex1, V vertex2) {
        Objects.requireNonNull(vertex1);
        Objects.requireNonNull(vertex2);
        graph.removeEdge(vertex1, vertex2);
        cutEdges.add(Pair.of(vertex1, vertex2));
        vertexToSetNumber = null;
    }

    @Override
    public void reset() {
        for (Pair<V, V> cutEdge : cutEdges) {
            graph.addEdge(cutEdge.getLeft(), cutEdge.getRight(), new Object());
        }
        cutEdges.clear();
        vertexToSetNumber = null;
    }

    @Override
    public int getComponentNumber(V vertex) {
        if (vertexToSetNumber == null) {
            vertexToSetNumber = new HashMap<>();
            int iSet = 0;
            for (Set<V> set : connectivityInspector.connectedSets()) {
                iSet++;
                for (V v : set) {
                    vertexToSetNumber.put(v, iSet);
                }
            }
        }
        return vertexToSetNumber.get(vertex);
    }

    @Override
    public Collection<Set<V>> getSmallComponents() {
        int nbCc = connectivityInspector.connectedSets().size();
        return connectivityInspector.connectedSets().stream()
            .sorted(Comparator.comparingInt(Set::size))
            .collect(Collectors.toList()).subList(1, nbCc);
    }

}
