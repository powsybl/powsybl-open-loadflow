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
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.workload.BenchmarkParameters;
import com.powsybl.openloadflow.graph.workload.ISpyGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.workload.IterationType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public final class SAConnectivityBenchmark {

    private static final BenchmarkParameters PERFORMANCE = new BenchmarkParameters.Performance()
            .setWarmup(1)
            .setMeasurement(1)
            .setOutput("results/sa/${name}/${class}_no_tree_edge_boolean.${ext}")
            .setOverwrite(true); //.setReplacement("results/sa/${name}/${class}_list_of_root.${ext}");
    private static final BenchmarkParameters VALIDATOR = new BenchmarkParameters.Validator();
    private static final BenchmarkParameters STATS_WRITER = new BenchmarkParameters.StatsWriter()
            .setOutput("graph_stats/data_sa/${name}/${class}/${operations}");

    private static final BenchmarkParameters WORKLOAD_PARAMS = PERFORMANCE;

    private static final Log LOG = Log.init("sa_results.txt");

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
                        new Parameters("fr", 0, Integer.MAX_VALUE, 1, 0, 1, SecurityAnalysisRunner.Mode.DC),
                        new Parameters("fr", 0, Integer.MAX_VALUE, 1, 0, 2, SecurityAnalysisRunner.Mode.DC),
                        new Parameters("fr", 0, Integer.MAX_VALUE, 1, 1, 1, SecurityAnalysisRunner.Mode.DC),
                        new Parameters("fr", 0, Integer.MAX_VALUE, 1, 1, 2, SecurityAnalysisRunner.Mode.DC)),
                "/home/carrezval/networks/case_SyntheticUSA.mat", List.of(
                        new Parameters("usa", 5000, 10000, 10, 0, 8, SecurityAnalysisRunner.Mode.DC),
                        new Parameters("usa", 5000, 10000, 10, 10, 8, SecurityAnalysisRunner.Mode.DC))
        );

        for (Map.Entry<String, List<Parameters>> perNetwork : parameters.entrySet()) {
            for (Parameters params : perNetwork.getValue()) {
                LOG.log("Security Analysis parameters: %s", params);

                Network network = Network.read(Path.of(perNetwork.getKey()));
                SecurityAnalysisRunner sar = new SecurityAnalysisRunner(network);

                if (params.lineToDisconnect > 0) {
                    sar.disconnectLinesPreserveConnectivity(params.lineToDisconnect);
                }
                sar.generateContingenciesAndActions(params.contingencyCount, params.linePerContingency, params.actionPerOp);
                sar.threadCount = params.threadCount;
                sar.mode = params.mode;

                for (GraphConnectivityFactory<LfBus, LfBranch> factory : factories) {
                    LOG.log("Using %s", factory);

                    if (factory instanceof EvenShiloachGraphDecrementalConnectivityFactory<LfBus, LfBranch> && !sar.operatorStrategies.isEmpty()) {
                        LOG.log("skipping EvenShiloachGraphDecrementalConnectivity, because of security analysis with operator strategies");
                        continue;
                    }

                    ISpyGraphConnectivityFactory<LfBus, LfBranch> spy = WORKLOAD_PARAMS.wrapIntoSpyFactory(params.toDirectoryName(), factory);
                    String partialResults = run(sar, spy, WORKLOAD_PARAMS.warmup(), WORKLOAD_PARAMS.measurement());
                    LOG.log(partialResults);
                }
            }
        }
    }

    private static String run(SecurityAnalysisRunner sar, ISpyGraphConnectivityFactory<LfBus, LfBranch> spy, int warmup, int measurement) {
        if (warmup > 0) {
            spy.beginIterations(warmup, IterationType.WARMUP);
            for (int i = 0; i < warmup; i++) {
                sar.run(spy);
            }
            spy.endIterations(warmup, IterationType.WARMUP);
        }

        AverageStopWatch asw = new AverageStopWatch();
        if (measurement > 0) {
            spy.beginIterations(measurement, IterationType.MEASURE);
            for (int i = 0; i < warmup; i++) {
                asw.merge(sar.run(spy));
            }
            spy.endIterations(measurement, IterationType.MEASURE);
        }

        return spy.resultsToString(measurement) + "Security analysis done in: %s%n%n%n".formatted(asw.toString());
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

    public record Parameters(String name, int lineToDisconnect, int contingencyCount, int linePerContingency, int actionPerOp, int threadCount, SecurityAnalysisRunner.Mode mode) {
        public String toDirectoryName() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("_").append(contingencyCount).append("_").append(linePerContingency);

            if (actionPerOp > 0) {
                sb.append("_1_").append(actionPerOp);
            }

            sb.append("/").append(mode.name().toLowerCase(Locale.ROOT)).append("_").append(threadCount);

            return sb.toString();
        }
    }

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
