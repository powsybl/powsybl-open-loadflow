/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtree;

import com.powsybl.openloadflow.graph.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * A set view of the connected component of a graph containing a specific {@link DTNode} in a spanning tree.
 * The nodes in this tree are exactly the vertices of the connected component.
 * This component view is guaranteed to always contain {@link #node} and
 * every {@link DTNode} connected to {@link #node}, according to the graph’s current state.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
class ComponentView<V, E> extends AbstractSetView<V> implements Component<V> {

    private final DTNode<V, E> node;

    ComponentView(DTNode<V, E> node) {
        this.node = node;
    }

    @Override
    public Iterator<V> iterator() {
        return new DFSIterator<>(getRoot());
    }

    @Override
    public boolean contains(Object o) {
        if (o != null) {
            // node might not be the root anymore, so need to use findRoot on node.
            return node.getGraph().rootOf((V) o) == getRoot();
        }

        return false;
    }

    @Override
    public int size() {
        return getRoot().size();
    }

    @Override
    public int getNumber() {
        node.getGraph().buildAndSortComponents();
        return getRoot().getIndex();
    }

    @Override
    public Set<V> toOwnedSet() {
        return new HashSet<>(this);
    }

    DTNode<V, E> getRoot() {
        return node.findRoot();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ComponentView<?, ?> that) {
            return Objects.equals(getRoot(), that.getRoot());
        } else {
            return super.equals(o);
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
