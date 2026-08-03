/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class IndexMap<K, V> implements Iterable<V> {

    private final ToIntFunction<K> keyAsInt;
    private final List<V> elements = new ArrayList<>();

    public IndexMap(ToIntFunction<K> keyAsInt) {
        this.keyAsInt = keyAsInt;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    private int idOf(K key) {
        if (key == null) {
            return -1;
        } else {
            return keyAsInt.applyAsInt(key);
        }
    }

    public boolean containsKey(K key) {
        int id = idOf(key);
        return id >= 0 && id < elements.size() && elements.get(id) != null;
    }

    public V get(K key) {
        int id = idOf(key);
        if (id >= 0 && id < elements.size()) {
            return elements.get(id);
        }
        return null;
    }

    public V put(K key, V value) {
        int id = idOf(key);
        if (id < 0) {
            throw new IllegalStateException("Cant put entry with negative key");
        }
        while (elements.size() <= id) {
            elements.add(null);
        }
        return elements.set(id, value);
    }

    public V remove(K key) {
        int id = idOf(key);
        if (id >= 0 && id < elements.size()) {
            return elements.set(id, null);
        }
        return null;
    }

    @Override
    public Iterator<V> iterator() {
        return new Itr();
    }

    private final class Itr implements Iterator<V> {

        private int index = 0;
        private V next = null;

        @Override
        public boolean hasNext() {
            if (next == null) {
                while (index < elements.size() && next == null) {
                    next = elements.get(index);
                    index++;
                }
            }
            return next != null;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            V next = this.next;
            this.next = null;
            return next;
        }
    }
}
