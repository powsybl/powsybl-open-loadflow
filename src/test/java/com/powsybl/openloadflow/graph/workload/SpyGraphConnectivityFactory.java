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
public class SpyGraphConnectivityFactory<V, E> implements GraphConnectivityFactory<V, E> {

    private final GraphConnectivityFactory<V, E> delegateFactory;
    private final List<SpyGraphConnectivity<V, E>> spies = new ArrayList<>();

    public SpyGraphConnectivityFactory(GraphConnectivityFactory<V, E> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public SpyGraphConnectivity<V, E> create() {
        SpyGraphConnectivity<V, E> conn = new SpyGraphConnectivity<>();
        conn.setDelegate(delegateFactory.create());
        spies.add(conn);
        return conn;
    }

    public GraphConnectivityFactory<V, E> getDelegateFactory() {
        return delegateFactory;
    }

    public String resultsToString(int iterations) {
        if (spies.size() == 1) {
            return spies.getFirst().resultsToString(iterations);
        }

        SpyGraphConnectivity<V, E> res = new SpyGraphConnectivity<>();
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
