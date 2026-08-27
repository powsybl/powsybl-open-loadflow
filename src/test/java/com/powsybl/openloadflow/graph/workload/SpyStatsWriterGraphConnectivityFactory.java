/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.log.Log;
import com.powsybl.openloadflow.graph.runners.Output;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyStatsWriterGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;
    private final Output output;

    public SpyStatsWriterGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory, Output output) {
        this.delegateFactory = delegateFactory;
        this.output = new Output(Objects.requireNonNull(output));
        this.output.setSpyGraphConnectivityFactory(getClass());
    }

    @Override
    public ISpyGraphConnectivity<V, E> create() {
        throw new IllegalStateException();
    }

    @Override
    public synchronized ISpyGraphConnectivity<V, E> create(Operations operations) {
        output.setOperations(operations);

        Path path;
        try {
            path = this.output.getOutputPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Log.get().log("Creating ComputeSdGraphConnectivity to %s", path);

        SpyStatsWriterGraphConnectivity<V, E> conn = new SpyStatsWriterGraphConnectivity<>(path);
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        return conn;
    }

    @Override
    public ISpyGraphConnectivity<V, E> createUnregistered() {
        throw new IllegalStateException();
    }

    @Override
    public ISpyGraphConnectivity<V, E> createUnregistered(Operations operations) {
        throw new IllegalStateException();
    }

    @Override
    public GraphConnectivityFactory<V, E> getDelegateFactory() {
        return delegateFactory;
    }

    @Override
    public String resultsToString(int iterations) {
        return "OK";
    }

    @Override
    public String toString() {
        return super.toString() + "[" + delegateFactory.toString() + "]";
    }
}
