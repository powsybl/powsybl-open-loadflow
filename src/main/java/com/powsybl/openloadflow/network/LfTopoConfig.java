/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.action.*;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.openloadflow.network.impl.LfLegBranch;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfTopoConfig {

    private final Set<Switch> switchesToOpen;

    private final Set<Switch> switchesToClose;

    private final Set<String> busIdsToLose;

    private final Set<String> branchIdsWithPtcToRetain;

    private final Set<String> branchIdsWithRtcToRetain;

    private final Set<String> shuntIdsToOperate;

    private final Set<String> branchIdsOpenableSide1;

    private final Set<String> branchIdsOpenableSide2;

    private final Set<String> branchIdsToClose;

    public LfTopoConfig() {
        switchesToOpen = new HashSet<>();
        switchesToClose = new HashSet<>();
        busIdsToLose = new HashSet<>();
        branchIdsWithPtcToRetain = new HashSet<>();
        branchIdsWithRtcToRetain = new HashSet<>();
        shuntIdsToOperate = new HashSet<>();
        branchIdsOpenableSide1 = new HashSet<>();
        branchIdsOpenableSide2 = new HashSet<>();
        branchIdsToClose = new HashSet<>();
    }

    public LfTopoConfig(LfTopoConfig other) {
        Objects.requireNonNull(other);
        this.switchesToOpen = new HashSet<>(other.switchesToOpen);
        this.switchesToClose = new HashSet<>(other.switchesToClose);
        this.busIdsToLose = new HashSet<>(other.busIdsToLose);
        this.branchIdsWithPtcToRetain = new HashSet<>(other.branchIdsWithPtcToRetain);
        this.branchIdsWithRtcToRetain = new HashSet<>(other.branchIdsWithRtcToRetain);
        this.shuntIdsToOperate = new HashSet<>(other.shuntIdsToOperate);
        this.branchIdsOpenableSide1 = new HashSet<>(other.branchIdsOpenableSide1);
        this.branchIdsOpenableSide2 = new HashSet<>(other.branchIdsOpenableSide2);
        this.branchIdsToClose = new HashSet<>(other.branchIdsToClose);
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

    public void addBranchIdWithPtcToRetain(String branchId) {
        branchIdsWithPtcToRetain.add(branchId);
    }

    public void addBranchIdWithRtcToRetain(String branchId) {
        branchIdsWithRtcToRetain.add(branchId);
    }

    public void addShuntIdToOperate(String shuntId) {
        shuntIdsToOperate.add(shuntId);
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

    public boolean isOperatedShunt(String shuntId) {
        return shuntIdsToOperate.contains(shuntId);
    }

    public Set<String> getBranchIdsOpenableSide1() {
        return branchIdsOpenableSide1;
    }

    public Set<String> getBranchIdsOpenableSide2() {
        return branchIdsOpenableSide2;
    }

    public Set<String> getBranchIdsToClose() {
        return branchIdsToClose;
    }

    public void addAllSwitchesToOperate(Network network, List<Action> actions) {
        actions.stream().filter(action -> action.getType().equals(SwitchAction.NAME))
                .forEach(action -> {
                    String switchId = ((SwitchAction) action).getSwitchId();
                    Switch sw = network.getSwitch(switchId);
                    boolean toOpen = ((SwitchAction) action).isOpen();
                    if (sw.isOpen() && !toOpen) { // the switch is open and the action will close it.
                        LfTopoConfig.this.getSwitchesToClose().add(sw);
                    } else if (!sw.isOpen() && toOpen) { // the switch is closed and the action will open it.
                        LfTopoConfig.this.getSwitchesToOpen().add(sw);
                    }
                });
    }

    public void addAllPtcToOperate(List<Action> actions) {
        for (Action action : actions) {
            if (PhaseTapChangerTapPositionAction.NAME.equals(action.getType())) {
                PhaseTapChangerTapPositionAction ptcAction = (PhaseTapChangerTapPositionAction) action;
                ptcAction.getSide().ifPresentOrElse(
                        side -> LfTopoConfig.this.addBranchIdWithPtcToRetain(LfLegBranch.getId(side, ptcAction.getTransformerId())), // T3WT
                        () -> LfTopoConfig.this.addBranchIdWithPtcToRetain(ptcAction.getTransformerId()) // T2WT
                );
            }
        }
    }

    public void addAllRtcToOperate(List<Action> actions) {
        for (Action action : actions) {
            if (RatioTapChangerTapPositionAction.NAME.equals(action.getType())) {
                RatioTapChangerTapPositionAction rtcAction = (RatioTapChangerTapPositionAction) action;
                rtcAction.getSide().ifPresentOrElse(
                        side -> LfTopoConfig.this.addBranchIdWithRtcToRetain(LfLegBranch.getId(side, rtcAction.getTransformerId())), // T3WT
                        () -> LfTopoConfig.this.addBranchIdWithRtcToRetain(rtcAction.getTransformerId()) // T2WT
                );
            }
        }
    }

    public void addAllShuntsToOperate(List<Action> actions) {
        actions.stream().filter(action -> action.getType().equals(ShuntCompensatorPositionAction.NAME))
                .forEach(action -> LfTopoConfig.this.addShuntIdToOperate(((ShuntCompensatorPositionAction) action).getShuntCompensatorId()));
    }

    public void addAllBranchesToClose(Network network, List<Action> actions) {
        // only branches open at both side or open at one side are visible in the LfNetwork.
        for (Action action : actions) {
            if (TerminalsConnectionAction.NAME.equals(action.getType())) {
                TerminalsConnectionAction terminalsConnectionAction = (TerminalsConnectionAction) action;
                if (terminalsConnectionAction.getSide().isEmpty() && !terminalsConnectionAction.isOpen()) {
                    Branch<?> branch = network.getBranch(terminalsConnectionAction.getElementId());
                    if (branch != null && !(branch instanceof TieLine) &&
                            !branch.getTerminal1().isConnected() && !branch.getTerminal2().isConnected()) {
                        // both terminals must be disconnected. If only one is connected, the branch is present
                        // in the Lf network.
                        LfTopoConfig.this.getBranchIdsToClose().add(terminalsConnectionAction.getElementId());
                    }
                }
            }
        }
    }
}
