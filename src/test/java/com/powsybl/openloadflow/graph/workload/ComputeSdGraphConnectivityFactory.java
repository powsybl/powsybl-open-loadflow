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
public class ComputeSdGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;

    private final String output;
    private final Map<String, String> outputPathParameters;
    private int num;

    public ComputeSdGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory,
                                             String output, Map<String, String> outputPathParameters) {
        this.delegateFactory = delegateFactory;
        this.output = output;
        this.outputPathParameters = outputPathParameters;
    }

    @Override
    public synchronized ISpyGraphConnectivity<V, E> create() {
        num++;
        outputPathParameters.put("index", String.valueOf(num));

        StringSubstitutor substitutor = new StringSubstitutor(outputPathParameters);
        substitutor.setEnableUndefinedVariableException(true);

        String path = substitutor.replace(output);
        Log.get().log("Creating ComputeSdGraphConnectivity to %s", path);

        ComputeSdGraphConnectivity<V, E> conn = new ComputeSdGraphConnectivity<>(Path.of(path));
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        return conn;
    }

    @Override
    public ISpyGraphConnectivity<V, E> createUnregistered() {
        return create();
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
