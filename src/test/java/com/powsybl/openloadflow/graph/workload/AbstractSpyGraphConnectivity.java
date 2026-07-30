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
public abstract class AbstractSpyGraphConnectivity<V, E> implements ISpyGraphConnectivity<V, E> {

    protected GraphConnectivityFactory<V, E> delegateFactory;
    protected GraphConnectivity<V, E> delegate;

    @Override
    public void setDelegate(GraphConnectivity<V, E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public GraphConnectivity<V, E> getDelegate() {
        return delegate;
    }

    @Override
    public void setDelegateFactory(GraphConnectivityFactory<V, E> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public GraphConnectivityFactory<V, E> getDelegateFactory() {
        return delegateFactory;
    }

    @Override
    public long computeSd() {
        if (delegate instanceof SpanningForestGraphConnectivity<V, E> sfgc) {
            return sfgc.computeSd();
        } else {
            return -1;
        }
    }

    @Override
    public int vertexCount() {
        if (delegate instanceof SpanningForestGraphConnectivity<V, E> sfgc) {
            return sfgc.vertexCount();
        } else {
            return -1;
        }
    }

    @Override
    public double averageDepth() {
        if (delegate instanceof SpanningForestGraphConnectivity<V, E> sfgc) {
            return sfgc.averageDepth();
        } else {
            return -1;
        }
    }
}
