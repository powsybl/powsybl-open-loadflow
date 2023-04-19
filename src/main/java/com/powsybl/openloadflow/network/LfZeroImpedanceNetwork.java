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

    private final LoadFlowType loadFlowType;

    private final Graph<LfBus, LfBranch> graph;

    private SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree;

    public LfZeroImpedanceNetwork(LfNetwork network, LoadFlowType loadFlowType, Graph<LfBus, LfBranch> graph) {
        this.network = Objects.requireNonNull(network);
        this.loadFlowType = Objects.requireNonNull(loadFlowType);
        this.graph = Objects.requireNonNull(graph);
        for (LfBus bus : graph.vertexSet()) {
            bus.setZeroImpedanceNetwork(loadFlowType, this);
        }
        updateSpanningTree();
        if (loadFlowType == LoadFlowType.AC) {
            updateVoltageControlMergeStatus();
        }
    }

    private static Graph<LfBus, LfBranch> createSubgraph(Graph<LfBus, LfBranch> graph, Set<LfBus> vertexSubset) {
        Graph<LfBus, LfBranch> subGraph = new Pseudograph<>(LfBranch.class);
        Graphs.addGraph(subGraph, new AsSubgraph<>(graph, vertexSubset));
        return subGraph;
    }

    public static Set<LfZeroImpedanceNetwork> create(LfNetwork network, LoadFlowType type) {
        Objects.requireNonNull(network);
        Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = new LinkedHashSet<>();
        var graph = createZeroImpedanceSubGraph(network, type);
        List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(graph).connectedSets();
        for (Set<LfBus> connectedSet : connectedSets) {
            var subGraph = createSubgraph(graph, connectedSet);
            zeroImpedanceNetworks.add(new LfZeroImpedanceNetwork(network, type, subGraph));
        }
        return zeroImpedanceNetworks;
    }

    private static Graph<LfBus, LfBranch> createZeroImpedanceSubGraph(LfNetwork network, LoadFlowType type) {
        Graph<LfBus, LfBranch> subGraph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null && branch.isZeroImpedance(type)) {
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

    public LoadFlowType getLoadFlowType() {
        return loadFlowType;
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
            branch.setSpanningTreeEdge(loadFlowType, spanningTreeEdges.contains(branch));
        }
    }

    @SuppressWarnings("unchecked")
    private static void linkVoltageControls(VoltageControl<?> mainVc, VoltageControl<?> vc) {
        mainVc.mergedDependentVoltageControls.add((VoltageControl) vc);
        vc.mainMergedVoltageControl = (VoltageControl) mainVc;
    }

    private void updateVoltageControlMergeStatus() {
        Map<VoltageControl.Type, List<VoltageControl<?>>> voltageControlsByType = new EnumMap<>(VoltageControl.Type.class);
        for (LfBus zb : graph.vertexSet()) { // all enabled by design
            for (VoltageControl<?> vc : zb.getVoltageControls()) {
                voltageControlsByType.computeIfAbsent(vc.getType(), k -> new ArrayList<>())
                        .add(vc);
                vc.getMergedDependentVoltageControls().clear();
                vc.mainMergedVoltageControl = null;
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
                mainVc.mergeStatus = VoltageControl.MergeStatus.MAIN;
                // first one is main, the other ones are dependents
                for (int i = 1; i < voltageControls.size(); i++) {
                    VoltageControl<?> vc = voltageControls.get(i);
                    vc.mergeStatus = VoltageControl.MergeStatus.DEPENDENT;
                    linkVoltageControls(mainVc, vc);
                }
            } else {
                voltageControls.get(0).mergeStatus = VoltageControl.MergeStatus.MAIN;
            }
        }
    }

    public void removeBranchAndTryToSplit(LfBranch disabledBranch) {
        graph.removeEdge(disabledBranch);

        List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(graph).connectedSets();
        if (connectedSets.size() > 1) { // real split
            disabledBranch.setSpanningTreeEdge(loadFlowType, false);

            Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = network.getZeroImpedanceNetworks(loadFlowType);
            zeroImpedanceNetworks.remove(this);
            List<LfZeroImpedanceNetwork> splitZns = new ArrayList<>(2);
            for (Set<LfBus> connectedSet : connectedSets) {
                var subGraph = createSubgraph(graph, connectedSet);
                splitZns.add(new LfZeroImpedanceNetwork(network, loadFlowType, subGraph));
            }
            zeroImpedanceNetworks.addAll(splitZns);

            for (LfNetworkListener listener : network.getListeners()) {
                listener.onZeroImpedanceNetworkSplit(this, splitZns, loadFlowType);
            }
        } else {
            if (disabledBranch.isSpanningTreeEdge(loadFlowType)) {
                disabledBranch.setSpanningTreeEdge(loadFlowType, false);

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
        LoadFlowType loadFlowType = zn1.getLoadFlowType();
        Set<LfZeroImpedanceNetwork> zeroImpedanceNetworks = network.getZeroImpedanceNetworks(loadFlowType);
        Graph<LfBus, LfBranch> mergedGraph = new Pseudograph<>(LfBranch.class);
        Graphs.addGraph(mergedGraph, zn1.getGraph());
        Graphs.addGraph(mergedGraph, zn2.getGraph());
        mergedGraph.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
        zeroImpedanceNetworks.remove(zn1);
        zeroImpedanceNetworks.remove(zn2);
        LfZeroImpedanceNetwork mergedZn = new LfZeroImpedanceNetwork(network, loadFlowType, mergedGraph);
        zeroImpedanceNetworks.add(mergedZn);

        for (LfNetworkListener listener : network.getListeners()) {
            listener.onZeroImpedanceNetworkMerge(zn1, zn2, mergedZn, loadFlowType);
        }
    }

    public void addBranch(LfBranch branch) {
        graph.addEdge(branch.getBus1(), branch.getBus2(), branch);
    }

    @Override
    public String toString() {
        return "LfZeroImpedanceNetwork(loadFlowType=" + loadFlowType
                + ", buses=" + graph.vertexSet()
                + ", branches=" + graph.edgeSet()
                + ")";
    }
}
