/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.utils.AverageStopWatch;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface ISpyGraphConnectivityFactory<V, E> extends GraphConnectivityFactory<V, E> {

    default void beginIterations(int iterations, IterationType type) { }

    default void endIterations(int iterations, IterationType type, AverageStopWatch timePerIteration) { }

    @Override
    ISpyGraphConnectivity<V, E> create();

    default ISpyGraphConnectivity<V, E> create(Operations operations) {
        return create();
    }

    ISpyGraphConnectivity<V, E> createUnregistered();

    default ISpyGraphConnectivity<V, E> createUnregistered(Operations operations) {
        return createUnregistered();
    }

    GraphConnectivityFactory<V, E> getDelegateFactory();

    String resultsToString(int iterations);
}
