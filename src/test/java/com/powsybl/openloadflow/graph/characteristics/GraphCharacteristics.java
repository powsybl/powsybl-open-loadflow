/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.characteristics;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import gnu.trove.map.hash.TIntIntHashMap;
import org.jgrapht.Graph;
import org.jgrapht.graph.Pseudograph;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class GraphCharacteristics {

    private static final Log LOG = Log.init();

    private GraphCharacteristics() { }

    public static void main(String[] args) {
        Network network = Network.read(Path.of(args[0]));
        LfNetwork lfNetwork = Networks.load(network, new FirstSlackBusSelector()).getFirst();

        Pseudograph<LfBus, LfBranch> graph = new Pseudograph<>(null, null, false);
        for (LfBus lfBus : lfNetwork.getBuses()) {
            graph.addVertex(lfBus);
        }
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            if (lfBranch != null && lfBranch.getBus1() != null && lfBranch.getBus2() != null) {
                graph.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
            }
        }

        List<LfBus> vertices = new ArrayList<>(graph.vertexSet());

        Diameter.diameterMultithreaded(graph, vertices, Runtime.getRuntime().availableProcessors() - 1);
        degree(graph);
        multiEdges(graph, vertices);
    }

    public static <V, E> void degree(Graph<V, E> graph) {
        int minDegree = Integer.MAX_VALUE;
        int maxDegree = Integer.MIN_VALUE;
        int averageDegree = 0;

        TIntIntHashMap degreeToCount = new TIntIntHashMap();

        for (V v : graph.vertexSet()) {
            int degree = graph.degreeOf(v);
            minDegree = Math.min(degree, minDegree);
            maxDegree = Math.max(degree, maxDegree);

            averageDegree += degree;
            degreeToCount.adjustOrPutValue(degree, 1, 1);
        }

        LOG.log("Min - Average - Max: %d, %f, %d%n",
                minDegree, averageDegree / (double) graph.vertexSet().size(), maxDegree);

        for (int i = 0; i <= maxDegree; i++) {
            LOG.log("Number of vertex of degree " + i + ": " + degreeToCount.get(i));
        }
    }

    public static <V, E> void multiEdges(Graph<V, E> graph, List<V> vertices) {
        int multiEdgeCount = 0;
        int loop = 0;
        for (int i = 0; i < vertices.size(); i++) {
            V vi = vertices.get(i);

            for (int j = 0; j < i; j++) {
                V vj = vertices.get(j);

                if (graph.getAllEdges(vi, vj).size() >= 2) {
                    multiEdgeCount++;
                }
            }

            if (!graph.getAllEdges(vi, vi).isEmpty()) {
                loop++;
            }
        }

        LOG.log("Number of vertices having loop: %d", loop);
        LOG.log("Number of multi-edges: %d", multiEdgeCount);
    }
}
