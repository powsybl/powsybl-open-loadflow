/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.MaskSubgraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfZeroImpedanceNetwork {

    private final boolean dc;

    private final Graph<LfBus, LfBranch> graph;

    private SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree;

    public LfZeroImpedanceNetwork(boolean dc, Graph<LfBus, LfBranch> graph) {
        this.dc = dc;
        this.graph = Objects.requireNonNull(graph);
    }

    public static List<LfZeroImpedanceNetwork> create(LfNetwork network, boolean dc) {
        Objects.requireNonNull(network);
        List<LfZeroImpedanceNetwork> zeroImpedanceNetworks = new ArrayList<>();
        var graph = createZeroImpedanceSubGraph(network, dc);
        List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(graph).connectedSets();
        for (Set<LfBus> connectedSet : connectedSets) {
            var subGraph = new AsSubgraph<>(graph, connectedSet);
            var zeroImpedanceNetwork = new LfZeroImpedanceNetwork(dc, subGraph);
            for (LfBranch branch : subGraph.edgeSet()) {
                branch.setZeroImpedanceNetwork(dc, zeroImpedanceNetwork);
            }
            zeroImpedanceNetwork.updateSpanningTree();
            zeroImpedanceNetworks.add(zeroImpedanceNetwork);
        }
        return zeroImpedanceNetworks;
    }

    private static Graph<LfBus, LfBranch> createZeroImpedanceSubGraph(LfNetwork network, boolean dc) {
        return network.createSubGraph(branch -> branch.isZeroImpedance(dc)
                && branch.getBus1() != null && branch.getBus2() != null);
    }

    public void updateSpanningTree() {
        // computer spanning tree on enabled subgraph
        var enabledSubGraph = new MaskSubgraph<>(graph, LfElement::isDisabled, LfElement::isDisabled);
        spanningTree = new KruskalMinimumSpanningTree<>(enabledSubGraph).getSpanningTree();
        Set<LfBranch> spanningTreeEdges = spanningTree.getEdges();
        for (LfBranch branch : graph.edgeSet()) {
            branch.setSpanningTreeEdge(dc, spanningTreeEdges.contains(branch));
        }
    }

    public Graph<LfBus, LfBranch> getGraph() {
        return graph;
    }

    public SpanningTreeAlgorithm.SpanningTree<LfBranch> getSpanningTree() {
        return spanningTree;
    }
}
