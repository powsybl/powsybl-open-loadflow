/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.alg.connectivity.TreeDynamicConnectivity;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class HolmEtAlGraphConnectivity<V, E> extends AbstractGraphConnectivity<V, E, JGraphTModel<V, E>> {

    private final List<TreeDynamicConnectivity<V>> spanningTreeForests = new ArrayList<>();

    public HolmEtAlGraphConnectivity() {
        super(new JGraphTModel<>());
    }

    @Override
    protected void updateConnectivity(EdgeRemove<V, E> edgeRemove) {

    }

    @Override
    protected void updateConnectivity(EdgeAdd<V, E> edgeAdd) {

    }

    @Override
    protected void updateConnectivity(VertexAdd<V, E> vertexAdd) {
        // nothing to do here
    }

    @Override
    protected void resetConnectivity(Deque<GraphModification<V, E>> m) {

    }

    @Override
    protected void updateComponents() {

    }

    @Override
    protected int getQuickComponentNumber(V vertex) {
        return 0;
    }

    @Override
    public boolean supportTemporaryChangesNesting() {
        return false;
    }
}
