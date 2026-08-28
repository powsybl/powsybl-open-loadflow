/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.workload.ISpyGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.workload.PerformanceGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.workload.StatsWriterGraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.workload.ValidatorGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfElement;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface RunParameters {

    <V, E> ISpyGraphConnectivityFactory<V, E> createFactory(GraphConnectivityFactory<V, E> factory, Output output);

    int warmup();

    int measurement();

    Output output();

    final class Performance implements RunParameters {

        private int warmup = 10;
        private int measurement = 10;
        private final Output output = new Output();

        @Override
        public <V, E> PerformanceGraphConnectivityFactory<V, E> createFactory(GraphConnectivityFactory<V, E> factory, Output output) {
            return new PerformanceGraphConnectivityFactory<>(factory, output);
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

        @Override
        public Output output() {
            return output;
        }

    }

    final class Validator implements RunParameters {

        @Override
        public <V, E> ValidatorGraphConnectivityFactory<V, E> createFactory(GraphConnectivityFactory<V, E> factory, Output output) {
            return new ValidatorGraphConnectivityFactory<>(new NaiveGraphConnectivityFactory<>(i -> {
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

        @Override
        public Output output() {
            return null;
        }
    }

    final class StatsWriter implements RunParameters {

        private final Output output = new Output();

        @Override
        public <V, E> StatsWriterGraphConnectivityFactory<V, E> createFactory(GraphConnectivityFactory<V, E> factory, Output output) {
            return new StatsWriterGraphConnectivityFactory<>(factory, output);
        }

        @Override
        public int warmup() {
            return 0;
        }

        @Override
        public int measurement() {
            return 1;
        }

        @Override
        public Output output() {
            return output;
        }
    }
}
