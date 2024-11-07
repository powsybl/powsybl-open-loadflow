/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.LfBus;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public final class BusDistance {

    private BusDistance() {
    }

    public static int distanceBetweenBuses(LfBus controlledBus, LfBus controller, int maxDistanceSearch) {
        if (controlledBus.equals(controller)) {
            return 0;
        }
        Set<LfBus> neighbors = controlledBus.findNeighbors().keySet();
        if (neighbors.contains(controller)) {
            return 1;
        }
        for (int distance = 2; distance < maxDistanceSearch; distance++) {
            neighbors = neighbors.stream().flatMap(bus -> bus.findNeighbors().keySet().stream()).collect(Collectors.toSet());
            if (neighbors.contains(controller)) {
                return distance;
            }
        }
        return Integer.MAX_VALUE;
    }
}
