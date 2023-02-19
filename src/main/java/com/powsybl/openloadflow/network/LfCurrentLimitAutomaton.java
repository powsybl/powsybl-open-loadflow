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
public class LfCurrentLimitAutomaton {

    private final LfSwitch switchToOperate;

    private final boolean switchOpen;

    public LfCurrentLimitAutomaton(LfSwitch switchToOperate, boolean switchOpen) {
        this.switchToOperate = Objects.requireNonNull(switchToOperate);
        this.switchOpen = switchOpen;
    }

    public LfSwitch getSwitchToOperate() {
        return switchToOperate;
    }

    public boolean isSwitchOpen() {
        return switchOpen;
    }
}
