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
            LfZeroImpedanceNetwork zn = new LfZeroImpedanceNetwork(network, dc, subGraph);
            if (!dc) {
                zn.updateVoltageControlMergeStatus();
            }
            zeroImpedanceNetworks.add(zn);
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

    @SuppressWarnings("unchecked")
    private static void linkVoltageControls(VoltageControl<?> mainVc, VoltageControl<?> vc) {
        mainVc.mergedDependentVoltageControls.add((VoltageControl) vc);
        vc.mainMergedVoltageControl = (VoltageControl) mainVc;
    }

    public void updateVoltageControlMergeStatus() {
        Map<VoltageControl.Type, List<VoltageControl<?>>> voltageControlsByType = new EnumMap<>(VoltageControl.Type.class);
        for (LfBus zb : graph.vertexSet()) { // all enabled by design
            for (VoltageControl<?> vc : zb.getVoltageControls()) {
                voltageControlsByType.computeIfAbsent(vc.getType(), k -> new ArrayList<>())
                        .add(vc);
                vc.getMergedDependentVoltageControls().clear();
                vc.mainMergedVoltageControl = null;
                vc.mergeStatus = VoltageControl.MergeStatus.MAIN;
            }
        }
        for (List<VoltageControl<?>> voltageControls : voltageControlsByType.values()) {
            if (voltageControls.size() > 1) {
                // we take the highest target voltage (why not...) and in case of equality the voltage control
                // with the first controlled bus ID by alpha sort
                voltageControls.sort(Comparator.<VoltageControl<?>>comparingDouble(VoltageControl::getTargetValue)
                        .reversed()
                        .thenComparing(o -> o.getControlledBus().getId()));
                VoltageControl<?> mainVc = voltageControls.get(0);
                // first one is main, the other have are dependents
                for (int i = 1; i < voltageControls.size(); i++) {
                    VoltageControl<?> vc = voltageControls.get(i);
                    vc.mergeStatus = VoltageControl.MergeStatus.DEPENDENT;
                    linkVoltageControls(mainVc, vc);
                }
            }
        }
    }

    public void removeBranchAndTryToSplit(LfBranch disabledBranch) {
        graph.removeEdge(disabledBranch);

        List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(graph).connectedSets();
        if (connectedSets.size() > 1) { // real split
            disabledBranch.setSpanningTreeEdge(dc, false);

            Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = network.getZeroImpedanceNetworks(dc);
            zeroImpedanceNetworks.remove(this);
            List<LfZeroImpedanceNetwork> splitZns = new ArrayList<>(2);
            for (Set<LfBus> connectedSet : connectedSets) {
                var subGraph = new AsSubgraph<>(graph, connectedSet);
                splitZns.add(new LfZeroImpedanceNetwork(network, dc, subGraph));
            }
            zeroImpedanceNetworks.addAll(splitZns);

            // update voltage control merge status
            if (!dc) {
                for (LfZeroImpedanceNetwork splitZn : splitZns) {
                    splitZn.updateVoltageControlMergeStatus();
                }
            }

            for (LfNetworkListener listener : network.getListeners()) {
                listener.onZeroImpedanceNetworkSplit(this, splitZns, dc);
            }
        } else {
            if (disabledBranch.isSpanningTreeEdge(dc)) {
                disabledBranch.setSpanningTreeEdge(dc, false);

                // just update the spanning
                updateSpanningTree();
            }
        }
    }

    public static void addBranchAndMerge(LfZeroImpedanceNetwork zn1, LfZeroImpedanceNetwork zn2, LfBranch enabledBranch) {
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

        // update voltage control merge status
        if (!dc) {
            mergedZn.updateVoltageControlMergeStatus();
        }

        for (LfNetworkListener listener : network.getListeners()) {
            listener.onZeroImpedanceNetworkMerge(zn1, zn2, mergedZn, dc);
        }
    }

    public void addBranch(LfBranch branch) {
        graph.addEdge(branch.getBus1(), branch.getBus2(), branch);
        updateSpanningTree();
    }

    public void tryToRemoveBranch(LfBranch disabledBranch) {
        if (graph.removeEdge(disabledBranch) && disabledBranch.isSpanningTreeEdge(dc)) {
            disabledBranch.setSpanningTreeEdge(dc, false);

            // just update the spanning
            updateSpanningTree();
        }
    }

    @Override
    public String toString() {
        return "LfZeroImpedanceNetwork(dc=" + dc
                + ", buses=" + graph.vertexSet()
                + ", branches=" + graph.edgeSet()
                + ")";
    }
}
