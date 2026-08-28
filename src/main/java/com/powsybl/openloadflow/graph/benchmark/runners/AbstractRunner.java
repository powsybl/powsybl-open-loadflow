/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.runners;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

import java.util.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public abstract class AbstractRunner<I, V, E> implements Runner<I, V, E> {

    protected RunParameters runParameters;
    protected final List<I> inputs = new ArrayList<>();
    protected final Set<GraphConnectivityFactory<V, E>> factories = new LinkedHashSet<>();

    @Override
    public void setRunParameters(RunParameters runParameters) {
        this.runParameters = runParameters;
    }

    @Override
    public void addInput(I input) {
        this.inputs.add(Objects.requireNonNull(input));
    }

    @Override
    public void addConnectivityFactory(GraphConnectivityFactory<V, E> factory) {
        this.factories.add(Objects.requireNonNull(factory));
    }
}
