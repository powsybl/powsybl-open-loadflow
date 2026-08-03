/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.function.ToIntFunction;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class IndexedDTreeStandaloneFactory<V, E> implements GraphConnectivityFactory<V, E> {

    private final ToIntFunction<V> vertexAsInt;
    private final ToIntFunction<E> edgeAsInt;

    public IndexedDTreeStandaloneFactory(ToIntFunction<V> vertexAsInt, ToIntFunction<E> edgeAsInt) {
        this.vertexAsInt = vertexAsInt;
        this.edgeAsInt = edgeAsInt;
    }

    @Override
    public GraphConnectivity<V, E> create() {
        return new IndexedDTreeStandalone<>(vertexAsInt, edgeAsInt);
    }
}
