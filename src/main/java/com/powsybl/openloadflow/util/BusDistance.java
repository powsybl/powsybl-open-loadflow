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
        Set<LfBus> busesToCheck = new HashSet<LfBus>();
        Set<LfBus> checkedBuses = new HashSet<LfBus>();
        busesToCheck.add(controller);
        checkedBuses.add(controller);
        for (int distance = 1; distance <= maxDistanceSearch; distance++) {
            busesToCheck = busesToCheck.stream().flatMap(bus -> bus.findNeighbors().keySet().stream()).collect(Collectors.toSet());
            busesToCheck.removeAll(checkedBuses);
            if (busesToCheck.contains(controlledBus)) {
                return distance;
            } else if (busesToCheck.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            checkedBuses.addAll(busesToCheck);
        }
        return Integer.MAX_VALUE;
    }
}
