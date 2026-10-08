/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import java.util.Set;

/**
import com.powsybl.openloadflow.graph.dtree.ComponentView;

import java.util.Set;

/**
 * A component is a set with a number. Components are owned
 * by their {@link GraphConnectivity}. They aren't persistent data
 * and can change when a {@link GraphConnectivity} is updated.
 * To own a component, you can convert it using {@link #toOwnedSet()}.
 * Implementation may avoid doing a copy whereas using {@link java.util.HashSet}'s
 * constructor will always copy the data.
 *
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public interface Component<V> extends Set<V> {

    /**
     * @return the number of this connected component. Components are sorted by size in reverse order
     * @see GraphConnectivity#getComponentNumber(Object)
     */
    int getNumber();

    /**
     * Component's data is owned by a {@link GraphConnectivity}, meaning they
     * aren't persistent and can change when {@link GraphConnectivity}. This
     * method return a persistent set that is owned by the caller.
     *
     * @return a set whose data is owned by the caller.
     * @implNote Implementations aren't required to copy data when unnecessary
     * @see ComponentView#toOwnedSet()
     * @see HashSetComponent#toOwnedSet()
     */
    Set<V> toOwnedSet();
}
