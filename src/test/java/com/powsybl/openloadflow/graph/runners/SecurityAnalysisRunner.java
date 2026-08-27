/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.runners;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;
import com.powsybl.openloadflow.graph.workload.ISpyGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.workload.IterationType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Locale;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SecurityAnalysisRunner extends AbstractRunner<SecurityAnalysisRunner.Input, LfBus, LfBranch> {

    private static final Log LOG = Log.init("sa_results.txt");

    @Override
    public void run() {
        for (Input input : inputs) {
            LOG.log("Security Analysis parameters: %s", input);

            Network network = Network.read(input.network);
            SingleSecurityAnalysisRunner ssar = new SingleSecurityAnalysisRunner(network);

            if (input.lineToDisconnect > 0) {
                ssar.disconnectLinesPreserveConnectivity(input.lineToDisconnect);
            }
            ssar.generateContingenciesAndActions(input.contingencyCount, input.linePerContingency, input.actionPerOp);
            ssar.threadCount = input.threadCount;
            ssar.mode = input.mode;

            for (GraphConnectivityFactory<LfBus, LfBranch> factory : factories) {
                LOG.log("Using %s", factory);

                if (factory instanceof EvenShiloachGraphDecrementalConnectivityFactory<LfBus, LfBranch> && !ssar.operatorStrategies.isEmpty()) {
                    LOG.log("skipping EvenShiloachGraphDecrementalConnectivity, because of security analysis with operator strategies");
                    continue;
                }

                String partialResults = run(input, ssar, factory);
                LOG.log(partialResults);
            }
        }
    }

    private String run(Input input, SingleSecurityAnalysisRunner ssar, GraphConnectivityFactory<LfBus, LfBranch> factory) {
        int warmup = runParameters.warmup();
        int measurement = runParameters.measurement();

        Output output = runParameters.output();
        output.setGraphConnectivityFactory(factory.getClass());
        output.set("name", input.toDirectoryName(ssar));

        ISpyGraphConnectivityFactory<LfBus, LfBranch> spy = runParameters.createFactory(factory, output);

        if (warmup > 0) {
            spy.beginIterations(warmup, IterationType.WARMUP);
            for (int i = 0; i < warmup; i++) {
                ssar.run(spy);
            }
            spy.endIterations(warmup, IterationType.WARMUP, null);
        }

        AverageStopWatch asw = new AverageStopWatch();
        if (measurement > 0) {
            spy.beginIterations(measurement, IterationType.MEASURE);
            for (int i = 0; i < warmup; i++) {
                asw.merge(ssar.run(spy));
            }
            spy.endIterations(measurement, IterationType.MEASURE, asw);
        }

        return spy.resultsToString(measurement) + "Security analysis done in: %s%n%n%n".formatted(asw.toString());
    }

    public record Input(String network,
                        String name,
                        int lineToDisconnect,
                        int contingencyCount,
                        int linePerContingency,
                        int actionPerOp,
                        SingleSecurityAnalysisRunner.Mode mode,
                        int threadCount) {
        public String toDirectoryName(SingleSecurityAnalysisRunner ssar) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("_").append(contingencyCount < 0 ? ssar.contingencies.size() : contingencyCount).append("_").append(linePerContingency);

            if (actionPerOp > 0) {
                sb.append("_1_").append(actionPerOp);
            }

            sb.append("/").append(mode.name().toLowerCase(Locale.ROOT)).append("_").append(threadCount);

            return sb.toString();
        }
    }
}
