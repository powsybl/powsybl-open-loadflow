/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.dtree;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import gnu.trove.set.hash.THashSet;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * An unmodifiable view of a set why can be backed by any structure providing
 * iteration of distinct elements. It is used by {@link DTreeGraphConnectivity} to
 * avoid copies in {@link GraphConnectivity#getLargestConnectedComponent()} and
 * {@link GraphConnectivity#getConnectedComponent(Object)}.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public abstract class AbstractSetView<E> extends AbstractSet<E> {

    /**
     * @param set the set to copy if necessary
     * @return a copy of {@code set} if it is an {@link AbstractSetView}
     *         or the original set otherwise
     */
    public static <V> Set<V> copyIfNeeded(Set<V> set) {
        if (set instanceof AbstractSetView<V>) {
            return new THashSet<>(set);
        }
        return set;
    }

    @Override
    public final boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean remove(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean addAll(Collection<? extends E> newElements) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean removeAll(Collection<?> oldElements) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean removeIf(java.util.function.Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean retainAll(Collection<?> elementsToKeep) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract Iterator<E> iterator();
}
