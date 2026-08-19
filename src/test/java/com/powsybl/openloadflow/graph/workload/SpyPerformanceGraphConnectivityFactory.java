/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.sa.extensions.ContingencyLoadFlowParametersJsonSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyPerformanceGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;
    private final SpyOutputFolder output;
    private final List<SpyPerformanceGraphConnectivity<V, E>> spies = new ArrayList<>();

    public SpyPerformanceGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory) {
        this(delegateFactory, null);
    }

    public SpyPerformanceGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory, SpyOutputFolder output) {
        this.delegateFactory = delegateFactory;
        this.output = output;
    }

    @Override
    public void endIterations(int iterations, IterationType type) {
        if (type == IterationType.MEASURE) {
            Path output = this.output.getOutputPath();

            ObjectMapper mapper = new ObjectMapper();

            try (JsonGenerator g = mapper.createGenerator(output.toFile(), JsonEncoding.UTF8)) {
                g.writeStartObject();
                serialize(g);
                g.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void serialize(JsonGenerator g) throws IOException {
        g.writeObjectFieldStart("operations");
        g.writeEndObject();

        g.writeObjectFieldStart("merged");
        g.writeEndObject();
    }

    @Override
    public synchronized SpyPerformanceGraphConnectivity<V, E> create() {
        SpyPerformanceGraphConnectivity<V, E> conn = new SpyPerformanceGraphConnectivity<>();
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        spies.add(conn);
        return conn;
    }

    @Override
    public synchronized ISpyGraphConnectivity<V, E> create(Operations operations) {
        output.setOperations(operations);
        SpyPerformanceGraphConnectivity<V, E> conn = new SpyPerformanceGraphConnectivity<>();
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        spies.add(conn);
        return conn;
    }

    @Override
    public ISpyGraphConnectivity<V, E> createUnregistered() {
        SpyPerformanceGraphConnectivity<V, E> conn = new SpyPerformanceGraphConnectivity<>();
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        return conn;
    }

    @Override
    public ISpyGraphConnectivity<V, E> createUnregistered(Operations operations) {
        return ISpyGraphConnectivityFactory.super.createUnregistered(operations);
    }

    @Override
    public GraphConnectivityFactory<V, E> getDelegateFactory() {
        return delegateFactory;
    }

    @Override
    public String resultsToString(int iterations) {
        if (spies.size() == 1) {
            return spies.getFirst().resultsToString(iterations);
        }

        SpyPerformanceGraphConnectivity<V, E> res = new SpyPerformanceGraphConnectivity<>();
        res.setDelegate(delegateFactory.create());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spies.size(); i++) {
            sb.append("Spy n°%d: %s%n".formatted(i, spies.get(i).resultsToString(iterations)));
            res.merge(spies.get(i));
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
