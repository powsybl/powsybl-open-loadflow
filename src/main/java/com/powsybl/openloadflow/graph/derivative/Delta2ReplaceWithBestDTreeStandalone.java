/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.derivative;

import com.powsybl.openloadflow.graph.DTreeStandalone;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class Delta2ReplaceWithBestDTreeStandalone<V, E> extends DTreeStandalone<V, E> {

    @Override
    protected final int ancestorBound(int delta) {
        return delta - 2;
    }

    @Override
    protected final boolean replaceWithBest() {
        return true;
    }

    public static class Factory<V, E> implements GraphConnectivityFactory<V, E> {

        @Override
        public GraphConnectivity<V, E> create() {
            return new Delta2ReplaceWithBestDTreeStandalone<>();
        }
    }
}
