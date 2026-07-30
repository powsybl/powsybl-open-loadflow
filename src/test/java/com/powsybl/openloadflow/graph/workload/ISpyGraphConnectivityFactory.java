/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface ISpyGraphConnectivityFactory<V, E> extends GraphConnectivityFactory<V, E> {

    @Override
    ISpyGraphConnectivity<V, E> create();

    ISpyGraphConnectivity<V, E> createUnregistered();

    GraphConnectivityFactory<V, E> getDelegateFactory();

    String resultsToString(int iterations);
}
