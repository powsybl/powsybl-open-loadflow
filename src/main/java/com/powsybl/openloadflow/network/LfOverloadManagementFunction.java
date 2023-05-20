/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfSwitch;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfOverloadManagementFunction {

    private final LfBranch branchToMonitor;

    private final LfSwitch switchToOperate;

    private final boolean switchOpen;

    public LfOverloadManagementFunction(LfBranch branchToMonitor, LfSwitch switchToOperate, boolean switchOpen) {
        this.branchToMonitor = Objects.requireNonNull(branchToMonitor);
        this.switchToOperate = Objects.requireNonNull(switchToOperate);
        this.switchOpen = switchOpen;
    }

    public LfBranch getBranchToMonitor() {
        return branchToMonitor;
    }

    public LfSwitch getSwitchToOperate() {
        return switchToOperate;
    }

    public boolean isSwitchOpen() {
        return switchOpen;
    }
}
