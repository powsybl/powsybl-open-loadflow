/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.workload;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ValidatorGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> checkFactory;
    private final GraphConnectivityFactory<V, E> delegateFactory;

    public ValidatorGraphConnectivityFactory(GraphConnectivityFactory<V, E> checkFactory, GraphConnectivityFactory<V, E> delegateFactory) {
        this.checkFactory = checkFactory;
        this.delegateFactory = delegateFactory;
    }

    @Override
    public ValidatorGraphConnectivity<V, E> create() {
        ValidatorGraphConnectivity<V, E> conn = new ValidatorGraphConnectivity<>(checkFactory);
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        return conn;
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
