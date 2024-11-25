/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.LfBus;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public final class BusDistance {

    private BusDistance() {
    }

    /**
     * Breadth first search algorithm to compute distance between two LfBus in a network
     * @param bus1              first LfBus (from which the breadth first search starts)
     * @param bus2              second LfBus
     * @param maxDistanceSearch the algorithm searches until this range and stops after this limit (or if every bus have been checked)
     * @return                  measured distance (number of branches) or Integer.MAX_VALUE if bus2 is not found
     */
    public static int distanceBetweenBuses(LfBus bus1, LfBus bus2, int maxDistanceSearch) {
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        if (bus1.equals(bus2)) {
            return 0;
        }
        Set<LfBus> busesToCheck = new HashSet<>();
        Set<LfBus> checkedBuses = new HashSet<>();
        busesToCheck.add(bus2);
        checkedBuses.add(bus2);
        for (int distance = 1; distance <= maxDistanceSearch; distance++) {
            busesToCheck = busesToCheck.stream()
                    .flatMap(bus -> bus.findNeighbors().keySet().stream())
                    .collect(Collectors.toSet());
            busesToCheck.removeAll(checkedBuses);
            if (busesToCheck.contains(bus1)) {
                return distance;
            } else if (busesToCheck.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            checkedBuses.addAll(busesToCheck);
        }
        return Integer.MAX_VALUE;
    }
}
