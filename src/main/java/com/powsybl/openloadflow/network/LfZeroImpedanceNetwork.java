/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.Pseudograph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfZeroImpedanceNetwork {

    private final LfNetwork network;

    private final boolean dc;

    private final Graph<LfBus, LfBranch> graph;

    private SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree;

    public LfZeroImpedanceNetwork(LfNetwork network, boolean dc, Graph<LfBus, LfBranch> graph) {
        this.network = Objects.requireNonNull(network);
        this.dc = dc;
        this.graph = Objects.requireNonNull(graph);
        for (LfBus bus : graph.vertexSet()) {
            bus.setZeroImpedanceNetwork(dc, this);
        }
        updateSpanningTree();
    }

    public static Set<LfZeroImpedanceNetwork> create(LfNetwork network, boolean dc) {
        Objects.requireNonNull(network);
        Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = new LinkedHashSet<>();
        var graph = createZeroImpedanceSubGraph(network, dc);
        List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(graph).connectedSets();
        for (Set<LfBus> connectedSet : connectedSets) {
            var subGraph = new AsSubgraph<>(graph, connectedSet);
            zeroImpedanceNetworks.add(new LfZeroImpedanceNetwork(network, dc, subGraph));
        }
        return zeroImpedanceNetworks;
    }

    private static Graph<LfBus, LfBranch> createZeroImpedanceSubGraph(LfNetwork network, boolean dc) {
        Graph<LfBus, LfBranch> subGraph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null && branch.isZeroImpedance(dc)) {
                // add to zero impedance graph all buses that could be connected to a zero impedance branch
                if (!subGraph.containsVertex(bus1)) {
                    subGraph.addVertex(bus1);
                }
                if (!subGraph.containsVertex(bus2)) {
                    subGraph.addVertex(bus2);
                }
                if (!branch.isDisabled()) {
                    subGraph.addEdge(bus1, bus2, branch);
                }
            }
        }
        return subGraph;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public boolean isDc() {
        return dc;
    }

    public Graph<LfBus, LfBranch> getGraph() {
        return graph;
    }

    public SpanningTreeAlgorithm.SpanningTree<LfBranch> getSpanningTree() {
        return spanningTree;
    }

    public void updateSpanningTree() {
        spanningTree = new KruskalMinimumSpanningTree<>(graph).getSpanningTree();
        Set<LfBranch> spanningTreeEdges = spanningTree.getEdges();
        for (LfBranch branch : graph.edgeSet()) {
            branch.setSpanningTreeEdge(dc, spanningTreeEdges.contains(branch));
        }
    }

    public void tryToSplit() {
        List<LfBranch> disabledBranches = graph.edgeSet().stream()
                .filter(LfElement::isDisabled)
                .collect(Collectors.toList());
        if (!disabledBranches.isEmpty()) {
            for (LfBranch branch : disabledBranches) {
                graph.removeEdge(branch);
            }
            List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(graph).connectedSets();
            if (connectedSets.size() > 1) { // real split
                for (LfBranch branch : disabledBranches) {
                    branch.setSpanningTreeEdge(dc, false);
                }
                Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = network.getZeroImpedanceNetworks(dc);
                zeroImpedanceNetworks.remove(this);
                List<LfZeroImpedanceNetwork> splitZns = new ArrayList<>(2);
                for (Set<LfBus> connectedSet : connectedSets) {
                    var subGraph = new AsSubgraph<>(graph, connectedSet);
                    splitZns.add(new LfZeroImpedanceNetwork(network, dc, subGraph));
                }
                zeroImpedanceNetworks.addAll(splitZns);
                for (LfNetworkListener listener : network.getListeners()) {
                    listener.onZeroImpedanceNetworkSplit(this, splitZns);
                }
            } else {
                boolean atLeastOneOfDisablingBranchIsPartOfSpanningTree = disabledBranches.stream()
                        .anyMatch(branch -> branch.isSpanningTreeEdge(dc));
                if (atLeastOneOfDisablingBranchIsPartOfSpanningTree) {
                    // just update the spanning
                    updateSpanningTree();
                }
            }
        }
    }

    public static void merge(LfZeroImpedanceNetwork zn1, LfZeroImpedanceNetwork zn2, LfBranch enabledBranch) {
        Objects.requireNonNull(zn1);
        Objects.requireNonNull(zn2);
        Objects.requireNonNull(enabledBranch);
        LfNetwork network = zn1.getNetwork();
        boolean dc = zn1.isDc();
        Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = network.getZeroImpedanceNetworks(dc);
        Graph<LfBus, LfBranch> mergedGraph = new Pseudograph<>(LfBranch.class);
        Graphs.addGraph(mergedGraph, zn1.getGraph());
        Graphs.addGraph(mergedGraph, zn2.getGraph());
        mergedGraph.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
        zeroImpedanceNetworks.remove(zn1);
        zeroImpedanceNetworks.remove(zn2);
        LfZeroImpedanceNetwork mergedZn = new LfZeroImpedanceNetwork(network, dc, mergedGraph);
        zeroImpedanceNetworks.add(mergedZn);
        for (LfNetworkListener listener : network.getListeners()) {
            listener.onZeroImpedanceNetworkMerge(zn1, zn2, mergedZn);
        }
    }
}
