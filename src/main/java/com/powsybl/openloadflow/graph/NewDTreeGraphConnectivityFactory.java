/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class NewDTreeGraphConnectivityFactory<V, E> implements GraphConnectivityFactory<V, E> {

    private final ToIntFunction<V> vertexToInt;
    private final ToIntFunction<E> edgeToInt;

    public NewDTreeGraphConnectivityFactory(ToIntFunction<V> vertexToInt, ToIntFunction<E> edgeToInt) {
        this.vertexToInt = Objects.requireNonNull(vertexToInt);
        this.edgeToInt = Objects.requireNonNull(edgeToInt);
    }

    @Override
    public GraphConnectivity<V, E> create() {
        return new NewDTreeGraphConnectivity<>(vertexToInt, edgeToInt);
    }
}
