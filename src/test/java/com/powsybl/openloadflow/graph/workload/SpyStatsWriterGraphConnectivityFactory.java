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
import org.apache.commons.text.StringSubstitutor;

import java.nio.file.Path;
import java.util.Map;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyStatsWriterGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;

    private final String output;
    private final Map<String, String> outputPathParameters;

    public SpyStatsWriterGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory,
                                                  String output, Map<String, String> outputPathParameters) {
        this.delegateFactory = delegateFactory;
        this.output = output;
        this.outputPathParameters = outputPathParameters;
    }

    private synchronized ISpyGraphConnectivity<V, E> create(Path source) {
        outputPathParameters.put("operations", source.toString());

        StringSubstitutor substitutor = new StringSubstitutor(outputPathParameters);
        substitutor.setEnableUndefinedVariableException(true);

        String path = substitutor.replace(output);
        Log.get().log("Creating ComputeSdGraphConnectivity to %s", path);

        SpyStatsWriterGraphConnectivity<V, E> conn = new SpyStatsWriterGraphConnectivity<>(Path.of(path));
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        return conn;
    }

    @Override
    public ISpyGraphConnectivity<V, E> create() {
        throw new IllegalStateException();
    }

    @Override
    public ISpyGraphConnectivity<V, E> create(Operations operations) {
        return create(operations.source());
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
