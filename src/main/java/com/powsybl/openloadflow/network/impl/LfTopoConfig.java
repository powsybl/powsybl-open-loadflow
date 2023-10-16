/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Switch;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfTopoConfig {

    private final Set<Switch> switchesToOpen;

    private final Set<Switch> switchesToClose;

    private final Set<String> busIdsToLose;

    public LfTopoConfig() {
        switchesToOpen = new HashSet<>();
        switchesToClose = new HashSet<>();
        busIdsToLose = new HashSet<>();
    }

    public LfTopoConfig(LfTopoConfig other) {
        Objects.requireNonNull(other);
        this.switchesToOpen = new HashSet<>(other.switchesToOpen);
        this.switchesToClose = new HashSet<>(other.switchesToClose);
        this.busIdsToLose = new HashSet<>(other.busIdsToLose);
    }

    public Set<Switch> getSwitchesToOpen() {
        return switchesToOpen;
    }

    public Set<Switch> getSwitchesToClose() {
        return switchesToClose;
    }

    public Set<String> getBusIdsToLose() {
        return busIdsToLose;
    }

    public boolean isBreaker() {
        return !(switchesToOpen.isEmpty() && switchesToClose.isEmpty() && busIdsToLose.isEmpty());
    }
}
