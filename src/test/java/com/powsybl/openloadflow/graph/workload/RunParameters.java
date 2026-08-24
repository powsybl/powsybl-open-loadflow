/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import org.mockito.Spy;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface RunParameters {

    ISpyGraphConnectivityFactory<Integer, Integer> factoryFor(Workload workload, GraphConnectivityFactory<Integer, Integer> factory);

    int warmup();

    int measurement();

    final class Performance implements RunParameters {

        private int warmup = 10;
        private int measurement = 10;
        private final SpyOutputFolder output = new SpyOutputFolder();

        @Override
        public ISpyGraphConnectivityFactory<Integer, Integer> factoryFor(Workload workload, GraphConnectivityFactory<Integer, Integer> factory) {
            output.setWorkload(workload);
            output.setGraphConnectivityFactory(factory.getClass());

            return new SpyPerformanceGraphConnectivityFactory<>(factory, new SpyOutputFolder(output));
        }

        public Performance setWarmup(int warmup) {
            if (warmup <= 0) {
                throw new IllegalArgumentException("warmup must be >= 0");
            }
            this.warmup = warmup;
            return this;
        }

        @Override
        public int warmup() {
            return warmup;
        }

        public Performance setMeasurement(int measurement) {
            if (measurement <= 0) {
                throw new IllegalArgumentException("measurement must be > 0");
            }
            this.measurement = measurement;
            return this;
        }

        @Override
        public int measurement() {
            return measurement;
        }

        public Performance setOutput(String output) {
            this.output.setOutputFormat(output);
            return this;
        }
    }

    final class Validator implements RunParameters {

        @Override
        public ISpyGraphConnectivityFactory<Integer, Integer> factoryFor(Workload workload, GraphConnectivityFactory<Integer, Integer> factory) {
            return new SpyValidatorGraphConnectivityFactory<>(new NaiveGraphConnectivityFactory<>(i -> i), factory);
        }

        @Override
        public int warmup() {
            return 0;
        }

        @Override
        public int measurement() {
            return 1;
        }
    }

    final class StatsWriter implements RunParameters {

        private final SpyOutputFolder output = new SpyOutputFolder();

        @Override
        public ISpyGraphConnectivityFactory<Integer, Integer> factoryFor(Workload workload, GraphConnectivityFactory<Integer, Integer> factory) {
            output.setWorkload(workload);
            output.setGraphConnectivityFactory(factory.getClass());

            return new SpyStatsWriterGraphConnectivityFactory<>(factory, output);
        }

        @Override
        public int warmup() {
            return 0;
        }

        @Override
        public int measurement() {
            return 1;
        }

        public StatsWriter setOutput(String output) {
            this.output.setOutputFormat(output);
            return this;
        }
    }
}
