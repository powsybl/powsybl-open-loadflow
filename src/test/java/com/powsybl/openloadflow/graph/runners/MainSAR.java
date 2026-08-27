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

import java.io.IOException;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class MainSAR {

    private MainSAR() { }

    private static RunParameters.Performance performance() {
        RunParameters.Performance perf = new RunParameters.Performance()
                .setWarmup(1)
                .setMeasurement(1);
        perf.output()
                .setOutputFormat("results/sa/${name}/${class}_no_tree_edge_boolean.${ext}")
                .setOverwrite(true); //.setReplacement("results/sa/${name}/${class}_list_of_root.${ext}");
        return perf;
    }

    private static RunParameters.Validator validator() {
        return new RunParameters.Validator();
    }

    private static RunParameters.StatsWriter statsWriter() {
        RunParameters.StatsWriter stats = new RunParameters.StatsWriter();
        stats.output().setOutputFormat("graph_stats/data_sa/${name}/${class}/${operations}");

        return stats;
    }

    public static void main(String[] args) throws IOException {
        SecurityAnalysisRunner sar = new SecurityAnalysisRunner();
        sar.setRunParameters(performance());

        sar.addInput(new SecurityAnalysisRunner.Input("/home/carrezval/networks/20240101T1200Z_20240101T1200Z_pf.xiidm.gz",
                "fr", 0, -1, 1, 0, SingleSecurityAnalysisRunner.Mode.DC, 1));
        // sar.addInput(new SecurityAnalysisRunner.Input("/home/carrezval/networks/20240101T1200Z_20240101T1200Z_pf.xiidm.gz",
        //         "fr", 0, -1, 1, 0, SingleSecurityAnalysisRunner.Mode.DC, 2));
        // sar.addInput(new SecurityAnalysisRunner.Input("/home/carrezval/networks/20240101T1200Z_20240101T1200Z_pf.xiidm.gz",
        //         "fr", 0, -1, 1, 1, SingleSecurityAnalysisRunner.Mode.DC, 1));
        // sar.addInput(new SecurityAnalysisRunner.Input("/home/carrezval/networks/20240101T1200Z_20240101T1200Z_pf.xiidm.gz",
        //         "fr", 0, -1, 1, 1, SingleSecurityAnalysisRunner.Mode.DC, 2));
        // sar.addInput(new SecurityAnalysisRunner.Input("/home/carrezval/networks/case_SyntheticUSA.mat",
        //         "usa", 5000, 10000, 10, 0, SingleSecurityAnalysisRunner.Mode.DC, 8));
        // sar.addInput(new SecurityAnalysisRunner.Input("/home/carrezval/networks/case_SyntheticUSA.mat",
        //         "usa", 5000, 10000, 10, 10, SingleSecurityAnalysisRunner.Mode.DC, 8));

        // sar.addConnectivityFactory(new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i));
        // sar.addConnectivityFactory(new NaiveGraphConnectivityFactory<>((Integer i) -> i));
        // sar.addConnectivityFactory(new MinimumSpanningTreeGraphConnectivityFactory<>());
        // sar.addConnectivityFactory(new EvenShiloachGraphDecrementalConnectivityFactory<>());
        // sar.addConnectivityFactory(new HolmEtAlGraphConnectivityFactory<>());
        // sar.addConnectivityFactory(new HolmEtAlWithoutLevelGraphConnectivityFactory<>());
        // sar.addConnectivityFactory(new NewHolmGraphConnectivityFactory<>());
        // sar.addConnectivityFactory(new HolmStandaloneFactory<>());
        sar.addConnectivityFactory(new DTreeGraphConnectivityFactory<>());
        // sar.addConnectivityFactory(new DTreeStandaloneFactory<>());
        // sar.addConnectivityFactory(new Delta2DTreeStandalone.Factory<>());
        // sar.addConnectivityFactory(new Delta2ReplaceWithBestDTreeStandalone.Factory<>());
        // sar.addConnectivityFactory(new ReplaceWithBestDTreeStandalone.Factory<>());
        // sar.addConnectivityFactory(IDTreeStandalone::new);
        // sar.addConnectivityFactory(new IndexedDTreeStandalone2ndVerFactory<>((Integer i) -> i, (Integer i) -> i));
        // sar.addConnectivityFactory(new DnDTreeStandaloneFactory<>());
        // sar.addConnectivityFactory(new OptDTreeStandaloneFactory<>());

        sar.run();

        System.out.println(DTNode.N.get());
        System.out.println(DTreeStandalone.N.get());
    }
}
