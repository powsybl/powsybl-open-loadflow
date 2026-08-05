/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.graph.SpanningForestGraphConnectivity;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface ISpyGraphConnectivity<V, E> extends SpanningForestGraphConnectivity<V, E> {

    void beginOperations(Operations operations);

    void endOperations(Operations operations);

    void setDelegate(GraphConnectivity<V, E> delegate);

    GraphConnectivity<V, E> getDelegate();

    void setDelegateFactory(GraphConnectivityFactory<V, E> delegateFactory);

    GraphConnectivityFactory<V, E> getDelegateFactory();

    default void newDelegate() {
        setDelegate(getDelegateFactory().create());
    }
}
