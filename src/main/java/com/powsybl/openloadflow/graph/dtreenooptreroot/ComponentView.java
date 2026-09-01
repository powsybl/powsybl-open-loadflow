/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtreenooptreroot;

import com.powsybl.openloadflow.graph.dtree.AbstractSetView;

import java.util.Iterator;

/**
 * A set view of a connected component in a graph. A component view is
 * represented by a single {@link DTNode} in a spanning tree. The nodes in this
 * tree are exactly the vertices of the connected component. A component view
 * is guaranteed to contain {@link #node} and every {@link DTNode} connected to
 * {@link #node}.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ComponentView<V, E> extends AbstractSetView<V> {

    private final DTNode<V, E> node;

    ComponentView(DTNode<V, E> node) {
        this.node = node;
    }

    @Override
    public Iterator<V> iterator() {
        return new DFSIterator<>(node.findRoot());
    }

    @Override
    public boolean contains(Object o) {
        if (o != null) {
            // node might not be the root anymore, so need to use findRoot on node.
            // However, don't use findRootOptReroot on node, it might change the root
            // after we got the root of 'o'.

            return node.getGraph().rootOf((V) o) == node.findRoot();
        }

        return false;
    }

    @Override
    public int size() {
        return node.findRoot().size();
    }
}
