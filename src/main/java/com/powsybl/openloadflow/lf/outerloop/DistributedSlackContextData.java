/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

/**
 * Keeps track of distributed active power
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DistributedSlackContextData {
    private double distributedActivePower = 0.0;

    public double getDistributedActivePower() {
        return distributedActivePower;
    }

    public void addDistributedActivePower(double addedDistributedActivePower) {
        distributedActivePower += addedDistributedActivePower;
    }
}
