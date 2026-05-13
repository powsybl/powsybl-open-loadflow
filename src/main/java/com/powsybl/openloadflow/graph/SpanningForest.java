/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.util.AVLTree;

import java.util.Iterator;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpanningForest<V> {

    private AVLTree<V> find(V vertex) {
        return null;
    }

    public boolean connected(V u, V w) {
        return find(u) == find(w);
    }

    public int componentSize(V vertex) {
        return 0;
    }

    public boolean addEdge(V u, V v) {
        return false;
    }

    public void addAll(AVLTree<V> tree) {

    }

    public boolean removeEdge(V u, V v) {
        return false;
    }

    public Iterator<Pair<V, V>> edgesInComponent(V vertex) {
        return null;
    }

    public Iterator<V> verticesInComponent(V vertex) {
        return null;
    }
}
