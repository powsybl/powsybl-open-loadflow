/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.workload;

import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpyPerformanceGraphConnectivityFactory<V, E> implements ISpyGraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;
    private final List<SpyPerformanceGraphConnectivity<V, E>> spies = new ArrayList<>();

    public SpyPerformanceGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public synchronized SpyPerformanceGraphConnectivity<V, E> create() {
        SpyPerformanceGraphConnectivity<V, E> conn = new SpyPerformanceGraphConnectivity<>();
        conn.setDelegateFactory(delegateFactory);
        conn.newDelegate();
        spies.add(conn);
        return conn;
    }

    @Override
    public ISpyGraphConnectivity<V, E> createUnregistered() {
        SpyPerformanceGraphConnectivity<V, E> conn = new SpyPerformanceGraphConnectivity<>();
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
        if (spies.size() == 1) {
            return spies.getFirst().resultsToString(iterations);
        }

        SpyPerformanceGraphConnectivity<V, E> res = new SpyPerformanceGraphConnectivity<>();
        res.setDelegate(delegateFactory.create());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spies.size(); i++) {
            sb.append("Spy n°%d: %s%n".formatted(i, spies.get(i).resultsToString(iterations)));
            res.merge(spies.get(i));
        }

        sb.append("Total:").append(System.lineSeparator());
        sb.append(res.resultsToString(iterations));
        return sb.toString();
    }

    @Override
    public String toString() {
        return super.toString() + "[" + delegateFactory.toString() + "]";
    }
}
