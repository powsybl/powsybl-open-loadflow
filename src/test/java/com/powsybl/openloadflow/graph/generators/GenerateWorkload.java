/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.generators;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.graph.SecurityAnalysisRunner;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import org.jgrapht.Graph;
import org.jgrapht.graph.Pseudograph;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@SuppressWarnings({"checkstyle:HideUtilityClassConstructor"})
public class GenerateWorkload {

    @SuppressWarnings("checkstyle:RegexpSingleline")
    public static void main(String[] args) throws IOException {
        NetworkGraph graph = new NetworkGraph(args[0]);

        Random random = new Random(0);
        /*PowSyBlWorkload workload = new PowSyBlWorkload(random, graph.graph, graph.enabledBranches);
        workload.forceOneInitialComponent();
        workload.contingencyCount = 1000;
        workload.minEdgePerContingency = 1; //50;
        workload.maxEdgePerContingency = 5;

        workload.actionPerContingency = 0;
        workload.minEdgePerAction = 0;
        workload.maxEdgePerAction = 0;*/

        /*ChenEtAlWorkload workload = new ChenEtAlWorkload(random, graph.graph, List.of());
        workload.testingPoints = 10;
        workload.numberOfUpdate = 1_000_000;
        workload.ur = 1.04;*/

        /*RandomWorkload workload = new RandomWorkload(random, graph.graph, graph.enabledBranches);
        workload.query = 10000;
        workload.insert = 10000;
        workload.remove = 10000;
        workload.computeSd = 10;*/

        SpyWorkloadGenerator workload = new SpyWorkloadGenerator(graph.network);
        workload.sar.disconnectLinesPreserveConnectivity(5000);
        workload.sar.generateContingenciesAndActions(10000, 10, 10);
        workload.sar.threadCount = Runtime.getRuntime().availableProcessors();
        workload.sar.mode = SecurityAnalysisRunner.Mode.DC;

        System.out.println(Instant.now());
        long start = System.currentTimeMillis();
        workload.generate(Path.of("workload/temp/"));
        System.out.println("Workload generation time: " + (System.currentTimeMillis() - start) + " ms");
    }

    private static class NetworkGraph {

        private final Network network;
        private final Graph<Integer, Integer> graph;
        private final List<Integer> disabledBranches = new ArrayList<>();
        private final List<Integer> enabledBranches = new ArrayList<>();

        NetworkGraph(String path) {
            // load network
            network = Network.read(Path.of(path));

            LfTopoConfig topo = new LfTopoConfig();
            for (Switch s : network.getSwitches()) {
                if (s.isOpen() && !s.getId().contains(".")) {
                    topo.getSwitchesToClose().add(s);
                }
            }

            try (LfNetworkList lfNetworkList = Networks.loadWithReconnectableElements(network, topo, new LfNetworkParameters().setBreakers(true), ReportNode.NO_OP)) {
                LfNetwork lfNetwork = lfNetworkList.getList().getFirst();

                // create graph
                graph = new Pseudograph<>(null, null, false);
                lfNetwork.getBuses().forEach(b -> graph.addVertex(b.getNum()));

                for (LfBranch branch : lfNetwork.getBranches()) {
                    if (branch.getBus1() != null && branch.getBus2() != null) {
                        graph.addEdge(branch.getBus1().getNum(), branch.getBus2().getNum(), branch.getNum());

                        if (branch.isDisabled()) {
                            disabledBranches.add(branch.getNum());
                        } else {
                            enabledBranches.add(branch.getNum());
                        }
                    }
                }
            }
        }
    }
}
