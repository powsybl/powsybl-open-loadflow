/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.openloadflow.graph.DTreeStandalone;
import com.powsybl.openloadflow.graph.DTreeStandaloneFactory;
import com.powsybl.openloadflow.graph.benchmark.workload.Workload;
import com.powsybl.openloadflow.graph.dtree.DTNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class MainWR {

    private MainWR() { }

    private static RunParameters.Performance performance() {
        RunParameters.Performance perf = new RunParameters.Performance()
                .setWarmup(0)
                .setMeasurement(10);
        perf.output()
                .setOutputFormat(null) // "results/workload_roots/${workload}/${class}.${ext}")
                .setOverwrite(false); //.setReplacement("results/workload/${workload}/${class}_list_of_root.${ext}");
        return perf;
    }

    private static RunParameters.Validator validator() {
        return new RunParameters.Validator();
    }

    private static RunParameters.StatsWriter statsWriter() {
        RunParameters.StatsWriter stats = new RunParameters.StatsWriter();
        stats.output().setOutputFormat("graph_stats/data_roots/${workload}/${class}/${operations}");

        return stats;
    }

    public static void main(String[] args) throws IOException {
        WorkloadRunner wr = new WorkloadRunner();
        wr.setRunParameters(validator());

        if (args.length >= 1) {
            switch (args[0]) {
                case "perf" -> wr.setRunParameters(performance());
                case "stats" -> wr.setRunParameters(statsWriter());
                case "validator" -> wr.setRunParameters(validator());
            }
        }

        /*if (args.length >= 2) {
            for (int i = 1; i < args.length; i++) {
                wr.addInput(Workload.inMemory(Path.of(args[i])));
            }
        } else {
            addAllWorkloadInFolder(wr, Path.of("workload/temp"));
        }*/

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
        //wr.addConnectivityFactory(new HolmStandaloneFactory<>());
        // wr.addConnectivityFactory(new DTreeSetRootGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new DTreeNoOptRerootGraphConnectivityFactory<>());
        // wr.addConnectivityFactory(new DTreeGraphConnectivityFactory<>());
        wr.addConnectivityFactory(new DTreeStandaloneFactory<>());
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

    private static void addAllWorkloadInFolder(WorkloadRunner wr, Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            Iterator<Path> it = stream.iterator();

            while (it.hasNext()) {
                Path next = it.next();
                wr.addInput(Workload.inMemory(next));
            }
        }
    }
}
