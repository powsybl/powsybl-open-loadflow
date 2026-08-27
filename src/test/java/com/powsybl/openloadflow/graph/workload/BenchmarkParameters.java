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
import com.powsybl.openloadflow.graph.SAConnectivityBenchmark;
import com.powsybl.openloadflow.network.LfElement;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface BenchmarkParameters {

    default <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(Workload workload, GraphConnectivityFactory<V, E> factory) {
        return wrapIntoSpyFactory(factory);
    }

    default <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(String name, GraphConnectivityFactory<V, E> factory) {
        return wrapIntoSpyFactory(factory);
    }

    <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(GraphConnectivityFactory<V, E> factory);

    int warmup();

    int measurement();

    final class Performance implements BenchmarkParameters {

        private int warmup = 10;
        private int measurement = 10;
        private final SpyOutputFolder output = new SpyOutputFolder();

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(Workload workload, GraphConnectivityFactory<V, E> factory) {
            output.setWorkload(workload);
            return wrapIntoSpyFactory(factory);
        }

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(String name, GraphConnectivityFactory<V, E> factory) {
            output.set("name", name);
            return wrapIntoSpyFactory(factory);
        }

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(GraphConnectivityFactory<V, E> factory) {
            output.setGraphConnectivityFactory(factory.getClass());
            return new SpyPerformanceGraphConnectivityFactory<>(factory, new SpyOutputFolder(output));
        }

        public Performance setWarmup(int warmup) {
            if (warmup < 0) {
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

        public Performance setReplacement(String output) {
            this.output.setReplacementFormat(output);
            return this;
        }

        public Performance setOverwrite(boolean overwrite) {
            this.output.setOverwrite(overwrite);
            return this;
        }
    }

    final class Validator implements BenchmarkParameters {

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(GraphConnectivityFactory<V, E> factory) {
            return new SpyValidatorGraphConnectivityFactory<>(new NaiveGraphConnectivityFactory<>(i -> {
                if (i instanceof LfElement l) {
                    return l.getNum();
                } else {
                    return (Integer) i;
                }
            }), factory);
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

    final class StatsWriter implements BenchmarkParameters {

        private final SpyOutputFolder output = new SpyOutputFolder();

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(Workload workload, GraphConnectivityFactory<V, E> factory) {
            output.setWorkload(workload);
            return wrapIntoSpyFactory(factory);
        }

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(String name, GraphConnectivityFactory<V, E> factory) {
            output.set("name", name);
            return wrapIntoSpyFactory(factory);
        }

        @Override
        public <V, E> ISpyGraphConnectivityFactory<V, E> wrapIntoSpyFactory(GraphConnectivityFactory<V, E> factory) {
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

        public StatsWriter setReplacement(String output) {
            this.output.setReplacementFormat(output);
            return this;
        }

        public StatsWriter setOverwrite(boolean overwrite) {
            this.output.setOverwrite(overwrite);
            return this;
        }
    }
}
