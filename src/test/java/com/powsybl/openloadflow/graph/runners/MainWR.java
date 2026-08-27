/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.runners;

import com.powsybl.openloadflow.graph.DTreeStandalone;
import com.powsybl.openloadflow.graph.dtree.DTNode;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.workload.Workload;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class MainWR {

    private MainWR() { }

    private static RunParameters.Performance performance() {
        RunParameters.Performance perf = new RunParameters.Performance()
                .setWarmup(10)
                .setMeasurement(10);
        perf.output()
                .setOutputFormat("results/workload/${workload}/${class}_no_tree_edge_boolean.${ext}")
                .setOverwrite(true); //.setReplacement("results/workload/${workload}/${class}_list_of_root.${ext}");
        return perf;
    }

    private static RunParameters.Validator validator() {
        return new RunParameters.Validator();
    }

    private static RunParameters.StatsWriter statsWriter() {
        RunParameters.StatsWriter stats = new RunParameters.StatsWriter();
        stats.output().setOutputFormat("graph_stats/data/${workload}/${class}/${operations}");

        return stats;
    }

    public static void main(String[] args) throws IOException {
        WorkloadRunner wr = new WorkloadRunner();
        wr.setRunParameters(performance());

        //List<Workload> workloads = getAllWorkloads(Path.of("workload/"), Set.of()); //, Set.of("spy_10000_10_10_10000_10_10_2026-07-09T08:47:18.906235251Z.zip"));
        wr.addInput(Workload.inMemory(Path.of("workload/spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt")));
        wr.addInput(Workload.inMemory(Path.of("workload/spy_5541_1_1_5541_1_1_2026-07-03T11:50:06.510031405Z.txt")));
        wr.addInput(Workload.inMemory(Path.of("workload/spy_10000_10_10_10000_10_10_2026-08-07T07:59:16.649371906Z.zip")));

        // wr.addConnectivityFactory(new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i));
        // wr.addConnectivityFactory(new NaiveGraphConnectivityFactory<>((Integer i) -> i));
        // wr.addConnectivityFactory(new MinimumSpanningTreeGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new EvenShiloachGraphDecrementalConnectivityFactory<>());
        // wr.addConnectivityFactory(new HolmEtAlGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new HolmEtAlWithoutLevelGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new NewHolmGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new HolmStandaloneFactory<>());
        wr.addConnectivityFactory(new DTreeGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new DTreeStandaloneFactory<>());
        // wr.addConnectivityFactory(new Delta2DTreeStandalone.Factory<>());
        // wr.addConnectivityFactory(new Delta2ReplaceWithBestDTreeStandalone.Factory<>());
        // wr.addConnectivityFactory(new ReplaceWithBestDTreeStandalone.Factory<>());
        // wr.addConnectivityFactory(IDTreeStandalone::new);
        // wr.addConnectivityFactory(new IndexedDTreeStandalone2ndVerFactory<>((Integer i) -> i, (Integer i) -> i));
        // wr.addConnectivityFactory(new DnDTreeStandaloneFactory<>());
        // wr.addConnectivityFactory(new OptDTreeStandaloneFactory<>());

        wr.run();

        System.out.println(DTNode.N.get());
        System.out.println(DTreeStandalone.N.get());
    }
}
