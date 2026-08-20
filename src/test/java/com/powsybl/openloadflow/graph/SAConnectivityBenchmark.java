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
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.workload.SpyPerformanceGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SAConnectivityBenchmark {

    public static void main(String[] args) throws IOException {
        Network network = Network.read(Path.of("/home/carrezval/networks/case_SyntheticUSA.mat")); // 20240101T1200Z_20240101T1200Z_pf.xiidm.gz"));
        SecurityAnalysisRunner sar = new SecurityAnalysisRunner(network);
        sar.disconnectLinesPreserveConnectivity(5000);
        sar.generateContingenciesAndActions(10000, 10, 0);
        // sar.setContingenciesAllLines();
        // sar.setDefaultActions();
        sar.threadCount = Runtime.getRuntime().availableProcessors();
        sar.mode = SecurityAnalysisRunner.Mode.DC;

        List<GraphConnectivityFactory<LfBus, LfBranch>> factories = List.of(
                // new OldNaiveGraphConnectivity.Factory<>((Integer i) -> i)
                // new NaiveGraphConnectivityFactory<>(LfBus::getNum),
                // new MinimumSpanningTreeGraphConnectivityFactory<>(),
                // new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                // new HolmEtAlGraphConnectivityFactory<>(),
                // new HolmEtAlWithoutLevelGraphConnectivityFactory<>(),
                // new NewHolmGraphConnectivityFactory<>(),
                // new HolmStandaloneFactory<>(),
                // new DTreeGraphConnectivityFactory<>(),
                new DTreeStandaloneFactory<>()
        );

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of("sa_results.txt"))) {
            for (GraphConnectivityFactory<LfBus, LfBranch> factory : factories) {
                SpyPerformanceGraphConnectivityFactory<LfBus, LfBranch> spy = new SpyPerformanceGraphConnectivityFactory<>(factory);
                AverageStopWatch asw = sar.run(spy);

                bw.write(spy.resultsToString(1));
                bw.write("Security analysis done in: %s%n%n%n".formatted(asw.toString()));
                bw.flush();
            }
        }
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
}
