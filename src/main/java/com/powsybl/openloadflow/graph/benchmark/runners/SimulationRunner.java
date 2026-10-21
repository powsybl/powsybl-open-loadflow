/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.AverageStopWatch;
import com.powsybl.openloadflow.graph.benchmark.log.Log;
import com.powsybl.openloadflow.graph.benchmark.workload.ISpyGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.workload.IterationType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SimulationRunner extends AbstractRunner<SimulationInput, LfBus, LfBranch> {

    private static final Log LOG = Log.init("sa_results.txt");

    @Override
    public void run() {
        for (SimulationInput input : inputs) {
            LOG.log("Security Analysis parameters: %s", input);

            Simulation simulation = input.createSimulation();

            for (GraphConnectivityFactory<LfBus, LfBranch> factory : factories) {
                LOG.log("Using %s", factory);

                if (factory instanceof EvenShiloachGraphDecrementalConnectivityFactory<LfBus, LfBranch> && simulation.hasOperatorStrategies()) {
                    LOG.log("skipping EvenShiloachGraphDecrementalConnectivity, because of security analysis with operator strategies");
                    continue;
                }

                String partialResults = run(simulation, factory);
                LOG.log(partialResults);
            }
        }
    }

    private String run(Simulation simulation, GraphConnectivityFactory<LfBus, LfBranch> factory) {
        int warmup = runParameters.warmup();
        int measurement = runParameters.measurement();

        Output output = runParameters.output();
        if (output != null) {
            output.setGraphConnectivityFactory(factory.getClass());
            output.set("name", simulation.directoryName());
        }

        ISpyGraphConnectivityFactory<LfBus, LfBranch> spy = runParameters.createFactory(factory, output);

        if (warmup > 0) {
            RunnerContext warmupContext = new RunnerContext(warmup, simulation.threadCount(), IterationType.WARMUP);

            spy.beginIterations(warmupContext);
            for (int i = 0; i < warmup; i++) {
                spy.beginIteration(warmupContext, i);
                simulation.run(spy);
                spy.endIteration(warmupContext, i);
            }
            spy.endIterations(warmupContext, null);
        }

        AverageStopWatch asw = new AverageStopWatch();
        if (measurement > 0) {
            RunnerContext measurementContext = new RunnerContext(measurement, simulation.threadCount(), IterationType.MEASURE);

            spy.beginIterations(measurementContext);
            for (int i = 0; i < measurement; i++) {
                spy.beginIteration(measurementContext, i);
                asw.merge(simulation.run(spy).time());
                spy.endIteration(measurementContext, i);
            }
            spy.endIterations(measurementContext, asw);
        }

        return spy.resultsToString(measurement) + "%nSecurity analysis done in: %s%n%n%n".formatted(asw.toString());
    }
}
