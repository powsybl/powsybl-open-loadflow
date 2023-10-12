/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Switch;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfTopoConfig {

    private final Set<Switch> switchesToOpen = new HashSet<>();

    private final Set<Switch> switchesToClose = new HashSet<>();

    private final Set<String> busIdsToLose = new HashSet<>();

    private final Set<String> branchIdsWithPtcToRetain = new HashSet<>();

    private final Set<String> branchIdsWithRtcToRetain = new HashSet<>();

    public Set<Switch> getSwitchesToOpen() {
        return switchesToOpen;
    }

    public Set<Switch> getSwitchesToClose() {
        return switchesToClose;
    }

    public Set<String> getBusIdsToLose() {
        return busIdsToLose;
    }

    public Set<String> getBranchIdsWithPtcToRetain() {
        return branchIdsWithPtcToRetain;
    }

    public Set<String> getBranchIdsWithRtcToRetain() {
        return branchIdsWithRtcToRetain;
    }

    public boolean isBreaker() {
        return !(switchesToOpen.isEmpty() && switchesToClose.isEmpty() && busIdsToLose.isEmpty());
    }

    public boolean retainPtc(String branchId) {
        return branchIdsWithPtcToRetain.contains(branchId);
    }

    public boolean retainRtc(String branchId) {
        return branchIdsWithRtcToRetain.contains(branchId);
    }
}
