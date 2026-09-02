/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.benchmark.AverageStopWatch;
import com.powsybl.openloadflow.graph.benchmark.generators.WorkloadUtils;
import com.powsybl.openloadflow.graph.benchmark.runners.Output;
import com.powsybl.openloadflow.graph.benchmark.runners.RunnerContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class PerformanceGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;
    private final Output output;

    // Each spy has a name corresponding to its thread id (aka contingencies and actions) and network or the operations file
    // Here thread id is more the partitionNum and less the real thread id that can change between two iterations.
    // but the partitionNum will always be the same
    private final Map<String, PerformanceGraphConnectivity<V, E>> spyMap = new HashMap<>();

    public PerformanceGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory) {
        this(delegateFactory, null);
    }

    public PerformanceGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory, Output output) {
        this.delegateFactory = delegateFactory;

        if (output != null) {
            this.output = new Output(output);
            this.output.setSpyGraphConnectivityFactory(getClass());
        } else {
            this.output = null;
        }
    }

    @Override
    public void beginIterations(RunnerContext context) {
        spyMap.clear();
    }

    @Override
    public void endIterations(RunnerContext context, AverageStopWatch timePerIteration) {
        if (context.iterationType() == IterationType.MEASURE) {
            try {
                output.set("ext", "json");
                Path outputPath = this.output.getOutputPath();

                if (outputPath != null) {
                    ObjectMapper mapper = new ObjectMapper();

                    try (JsonGenerator g = mapper.createGenerator(WorkloadUtils.newBufferedWriter(outputPath))) {
                        g.setPrettyPrinter(new DefaultPrettyPrinter());
                        g.writeStartObject();
                        serialize(g);

                        g.writeObjectFieldStart("timePerIteration");
                        timePerIteration.serialize(g);
                        g.writeEndObject();

                        g.writeEndObject();
                    }
                }

                this.output.set("ext", "txt");
                outputPath = this.output.getOutputPath();

                if (outputPath != null) {
                    String content = resultsToString(context.iterations()) + "Time/iteration: " + timePerIteration + System.lineSeparator();
                    Files.writeString(outputPath, content);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public synchronized PerformanceGraphConnectivity<V, E> create() {
        PerformanceGraphConnectivity<V, E> conn = new PerformanceGraphConnectivity<>();
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        return conn;
    }

    @Override
    public synchronized ISpyGraphConnectivity<V, E> create(Operations operations) {
        return create(operations.source().toString());
    }

    @Override
    public synchronized ISpyGraphConnectivity<V, E> create(int threadId, int networkId) {
        return create("{T" + threadId + "-N" + networkId + "}");
    }

    private synchronized ISpyGraphConnectivity<V, E> create(String name) {
        var perf = spyMap.computeIfAbsent(name, k -> {
            PerformanceGraphConnectivity<V, E> conn = new PerformanceGraphConnectivity<>(name);
            conn.setDelegateFactory(delegateFactory);
            return conn;
        });
        perf.newDelegate();
        return perf;
    }

    @Override
    public GraphConnectivityFactory<V, E> getDelegateFactory() {
        return delegateFactory;
    }

    private void serialize(JsonGenerator g) throws IOException {
        PerformanceGraphConnectivity<V, E> merged = new PerformanceGraphConnectivity<>();
        merged.setDelegateFactory(delegateFactory);
        merged.setDelegate(delegateFactory.create());

        g.writeArrayFieldStart("operations");
        for (PerformanceGraphConnectivity<V, E> spy : spyMap.values()) {
            g.writeStartObject();
            spy.serialize(g);
            g.writeEndObject();
            merged.merge(spy);
        }
        g.writeEndArray();

        g.writeObjectFieldStart("merged");
        merged.serialize(g);
        g.writeEndObject();
    }

    @Override
    public String resultsToString(int iterations) {
        if (spyMap.size() == 1) {
            // ...
            return spyMap.values().iterator().next().resultsToString(iterations);
        }

        PerformanceGraphConnectivity<V, E> res = new PerformanceGraphConnectivity<>();
        res.setDelegateFactory(delegateFactory);
        res.setDelegate(delegateFactory.create());

        StringBuilder sb = new StringBuilder();
        for (PerformanceGraphConnectivity<V, E> perfSpy : spyMap.values()) {
            sb.append(perfSpy.resultsToString(iterations));
            res.merge(perfSpy);
        }

        sb.append("Total:").append(System.lineSeparator());
        sb.append(res.resultsToString(iterations));
        return sb.toString();
    }

    @Override
    public String toString() {
        return super.toString() + "[" + delegateFactory.toString() + "]";
    }
}
