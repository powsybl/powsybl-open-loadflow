/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.workload.RunParameters;
import com.powsybl.openloadflow.graph.workload.SpyPerformanceGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class SAConnectivityBenchmark {

    private static final RunParameters PERFORMANCE = new RunParameters.Performance()
            .setWarmup(20)
            .setMeasurement(10)
            .setOutput("results/sa/${workload}/${class}_no_tree_edge_boolean.${ext}")
            .setOverwrite(true); //.setReplacement("results/sa/${workload}/${class}_list_of_root.${ext}");
    private static final RunParameters VALIDATOR = new RunParameters.Validator();
    private static final RunParameters STATS_WRITER = new RunParameters.StatsWriter()
            .setOutput("graph_stats/data/${workload}/${class}/${operations}");

    private static final RunParameters WORKLOAD_PARAMS = PERFORMANCE;

    private SAConnectivityBenchmark() { }

    public static void main(String[] args) throws IOException {
        List<GraphConnectivityFactory<LfBus, LfBranch>> factories = List.of(
                // new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i)
                // new NaiveGraphConnectivityFactory<>(LfBus::getNum),
                // new MinimumSpanningTreeGraphConnectivityFactory<>(),
                // new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                // new HolmEtAlGraphConnectivityFactory<>(),
                // new HolmEtAlWithoutLevelGraphConnectivityFactory<>(),
                // new NewHolmGraphConnectivityFactory<>(),
                // new HolmStandaloneFactory<>(),
                new DTreeGraphConnectivityFactory<>()
                // new DTreeStandaloneFactory<>()
        );

        Map<String, List<Parameters>> parameters = linkedMap(
                "/home/carrezval/networks/20240101T1200Z_20240101T1200Z_pf.xiidm.gz", List.of(
                        new Parameters(0, Integer.MAX_VALUE, 1, 0, 1, SecurityAnalysisRunner.Mode.DC),
                        new Parameters(0, Integer.MAX_VALUE, 1, 1, 1, SecurityAnalysisRunner.Mode.DC)),
                "/home/carrezval/networks/case_SyntheticUSA.mat", List.of(
                        new Parameters(5000, 10000, 10, 0, 8, SecurityAnalysisRunner.Mode.DC))
        );

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of("sa_results.txt"))) {
            for (Map.Entry<String, List<Parameters>> perNetwork : parameters.entrySet()) {
                for (Parameters params : perNetwork.getValue()) {
                    Network network = Network.read(Path.of(perNetwork.getKey()));
                    SecurityAnalysisRunner sar = new SecurityAnalysisRunner(network);

                    if (params.lineToDisconnect > 0) {
                        sar.disconnectLinesPreserveConnectivity(params.lineToDisconnect);
                    }
                    sar.generateContingenciesAndActions(params.contingencyCount, params.linePerContingency, params.actionPerOp);
                    sar.threadCount = params.threadCount;
                    sar.mode = params.mode;

                    for (GraphConnectivityFactory<LfBus, LfBranch> factory : factories) {
                        SpyPerformanceGraphConnectivityFactory<LfBus, LfBranch> spy = new SpyPerformanceGraphConnectivityFactory<>(factory);
                        AverageStopWatch asw = sar.run(spy);

                        bw.write(spy.resultsToString(1));
                        bw.write("Security analysis done in: %s%n%n%n".formatted(asw.toString()));
                        bw.flush();
                    }
                }
            }
        }

        /*Network network = Network.read(Path.of("/home/carrezval/networks/case_SyntheticUSA.mat")); // 20240101T1200Z_20240101T1200Z_pf.xiidm.gz"));
        SecurityAnalysisRunner sar = new SecurityAnalysisRunner(network);
        sar.disconnectLinesPreserveConnectivity(5000);
        sar.generateContingenciesAndActions(10000, 10, 0);
        // sar.setContingenciesAllLines();
        // sar.setDefaultActions();
        sar.threadCount = Runtime.getRuntime().availableProcessors();
        sar.mode = SecurityAnalysisRunner.Mode.DC;

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of("sa_results.txt"))) {
            for (GraphConnectivityFactory<LfBus, LfBranch> factory : factories) {
                SpyPerformanceGraphConnectivityFactory<LfBus, LfBranch> spy = new SpyPerformanceGraphConnectivityFactory<>(factory);
                AverageStopWatch asw = sar.run(spy);

                bw.write(spy.resultsToString(1));
                bw.write("Security analysis done in: %s%n%n%n".formatted(asw.toString()));
                bw.flush();
            }
        }*/
    }

    public static List<Action> getCloseSwitchActions(Network network) {
        return network.getSwitchStream()
                .filter(Switch::isOpen) // seulement les switchs ouverts dont l'action sera de les fermer
                .filter(s -> !s.getId().contains("."))
                .map(s -> {
                    s.setRetained(true);
                    return (Action) new SwitchAction(s.getId(), s.getId(), false);
                })
                .collect(Collectors.toList());
    }

    public static List<OperatorStrategy> createOperatorStrategies(List<Contingency> contingencies, List<Action> actions, Random random, int minAction, int maxAction) {
        List<OperatorStrategy> operatorStrategies = new ArrayList<>();

        for (int i = 0; i < contingencies.size(); i++) {
            // take between 'minAction' and 'maxAction' actions for this contingency
            List<String> opActions = RandomUtils.sample(random, actions, minAction, maxAction).map(Action::getId).toList();

            operatorStrategies.add(new OperatorStrategy("OP" + i,
                    ContingencyContext.specificContingency(contingencies.get(i).getId()),
                    new TrueCondition(),
                    opActions));
        }

        return operatorStrategies;
    }

    private record Parameters(int lineToDisconnect, int contingencyCount, int linePerContingency, int actionPerOp, int threadCount, SecurityAnalysisRunner.Mode mode) { }

    static <K, V> Map<K, V> linkedMap(K k1, V v1) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);

        return map;
    }

    static <K, V> Map<K, V> linkedMap(K k1, V v1, K k2, V v2) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);

        return map;
    }

    static <K, V> Map<K, V> linkedMap(K k1, V v1, K k2, V v2, K k3, V v3) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);

        return map;
    }
}
