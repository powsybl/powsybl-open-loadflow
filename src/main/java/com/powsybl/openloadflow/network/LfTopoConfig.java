/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Switch;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfTopoConfig {

    private final Set<Switch> switchesToOpen = new HashSet<>();

    private final Set<Switch> switchesToClose = new HashSet<>();

    private final Set<String> busIdsToLose = new HashSet<>();

    private final Set<String> branchIdsWithPtcToRetain = new HashSet<>();

    private final Set<String> branchIdsWithRtcToRetain = new HashSet<>();

    private final Set<String> branchIdsOpenableSide1 = new HashSet<>();

    private final Set<String> branchIdsOpenableSide2 = new HashSet<>();

    public Set<Switch> getSwitchesToOpen() {
        return switchesToOpen;
    }

    public Set<Switch> getSwitchesToClose() {
        return switchesToClose;
    }

    public Set<String> getBusIdsToLose() {
        return busIdsToLose;
    }

    public void addBranchIdsWithPtcToRetain(String branchId) {
        branchIdsWithPtcToRetain.add(branchId);
    }

    public void addBranchIdsWithRtcToRetain(String branchId) {
        branchIdsWithRtcToRetain.add(branchId);
    }

    public boolean isBreaker() {
        return !(switchesToOpen.isEmpty() && switchesToClose.isEmpty() && busIdsToLose.isEmpty());
    }

    public boolean isRetainedPtc(String branchId) {
        return branchIdsWithPtcToRetain.contains(branchId);
    }

    public boolean isRetainedRtc(String branchId) {
        return branchIdsWithRtcToRetain.contains(branchId);
    }

    public Set<String> getBranchIdsOpenableSide1() {
        return branchIdsOpenableSide1;
    }

    public Set<String> getBranchIdsOpenableSide2() {
        return branchIdsOpenableSide2;
    }
}
