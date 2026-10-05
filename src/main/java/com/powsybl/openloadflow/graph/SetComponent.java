/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.Iterator;
import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SetComponent<V> extends AbstractComponent<V> {

    final Set<V> set;

    public SetComponent(Set<V> set) {
        this(set, -1);
    }

    public SetComponent(Set<V> set, int num) {
        this.set = set;
        setNum(num);
    }

    @Override
    public Set<V> intoSet() {
        return set;
    }

    @Override
    public Iterator<V> iterator() {
        return set.iterator();
    }

    @Override
    public boolean contains(Object o) {
        return set.contains(o);
    }

    @Override
    public int size() {
        return set.size();
    }
}
