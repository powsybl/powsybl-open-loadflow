/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import java.util.Map;
import java.util.TreeMap;

/**
 * Keeps track of distributed active power per synchronous component.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DistributedSlackContextData {
    private final Map<Integer, Double> distributedActivePowerByNumSC = new TreeMap<>();

    /**
     * Get the distributed active power in a synchronous component.
     * @param numSC number of the synchronous component
     */
    public double getDistributedActivePower(int numSC) {
        return distributedActivePowerByNumSC.getOrDefault(numSC, 0.0);
    }

    /**
     * Get the overall distributed active power.
     */
    public double getDistributedActivePower() {
        return distributedActivePowerByNumSC.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Update the distributed active power in a synchronous component.
     * @param numSC number of the synchronous component
     * @param addedDistributedActivePower new distributed active power to be added (can be negative).
     */
    public void addDistributedActivePower(int numSC, double addedDistributedActivePower) {
        distributedActivePowerByNumSC.merge(numSC, addedDistributedActivePower, Double::sum);
    }
}
