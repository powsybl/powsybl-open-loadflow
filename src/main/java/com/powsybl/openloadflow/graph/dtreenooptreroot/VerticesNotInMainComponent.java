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
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A view over the vertices not connected to a specific node.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
class VerticesNotInMainComponent<V, E> extends AbstractSetView<V> {

    private final DTGraph<V, E> graph;
    private final DTNode<V, E> excludedTree;
    private int size = -1;

    VerticesNotInMainComponent(DTGraph<V, E> graph, DTNode<V, E> excludedTree) {
        this.graph = graph;
        this.excludedTree = excludedTree;
    }

    @Override
    public Iterator<V> iterator() {
        return new Itr(excludedTree.findRoot());
    }

    @Override
    public boolean contains(Object o) {
        if (o != null) {
            return graph.rootOf((V) o) != excludedTree.findRoot();
        }

        return false;
    }

    @Override
    public int size() {
        if (size < 0) {
            size = 0;

            DTNode<V, E> excludedTreeRoot = excludedTree.findRoot();
            size = graph.getRoots().stream()
                    .filter(root -> root != excludedTreeRoot)
                    .mapToInt(DTNode::size)
                    .sum();
        }

        return size;
    }

    private class Itr implements Iterator<V> {

        private final DTNode<V, E> excludedTree;
        private int index = 0;
        private Iterator<V> curIt;

        Itr(DTNode<V, E> excludedTree) {
            this.excludedTree = excludedTree;
        }

        @Override
        public boolean hasNext() {
            if (curIt != null && curIt.hasNext()) {
                return true;
            }

            List<DTNode<V, E>> roots = graph.getRoots();
            while (index < roots.size()) {
                DTNode<V, E> next = roots.get(index);
                index++;

                if (next != excludedTree) {
                    curIt = new DFSIterator<>(next);
                    return true;
                }
            }

            return false;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            return curIt.next();
        }
    }
}
