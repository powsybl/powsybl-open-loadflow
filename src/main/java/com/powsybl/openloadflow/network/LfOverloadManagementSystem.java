/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfSwitch;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfOverloadManagementSystem {

    private final LfBranch monitoredBranch;

    private final double threshold;

    private final LfSwitch switchToOperate;

    private final boolean switchOpen;

    public LfOverloadManagementSystem(LfBranch monitoredBranch, double threshold, LfSwitch switchToOperate, boolean switchOpen) {
        this.monitoredBranch = Objects.requireNonNull(monitoredBranch);
        this.threshold = threshold;
        this.switchToOperate = Objects.requireNonNull(switchToOperate);
        this.switchOpen = switchOpen;
    }

    public LfBranch getMonitoredBranch() {
        return monitoredBranch;
    }

    public double getThreshold() {
        return threshold;
    }

    public LfSwitch getSwitchToOperate() {
        return switchToOperate;
    }

    public boolean isSwitchOpen() {
        return switchOpen;
    }
}
