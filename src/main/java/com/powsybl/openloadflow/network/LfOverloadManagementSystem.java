/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfOverloadManagementSystem implements LfCopyable<LfOverloadManagementSystem, LfNetwork> {

    private final LfBranch monitoredBranch;

    private final TwoSides monitoredSide;

    public record LfBranchTripping(LfBranch branchToOperate, boolean branchOpen, double threshold) { }

    private final List<LfBranchTripping> branchTrippingList = new ArrayList<>();

    public LfOverloadManagementSystem(LfBranch monitoredBranch, TwoSides monitoredSide) {
        this.monitoredBranch = Objects.requireNonNull(monitoredBranch);
        this.monitoredSide = Objects.requireNonNull(monitoredSide);
    }

    @Override
    public LfOverloadManagementSystem copy(LfNetwork copyNetwork) {
        LfOverloadManagementSystem copiedSystem = new LfOverloadManagementSystem(
                copyNetwork.getBranchById(monitoredBranch.getId()), monitoredSide);
        for (LfOverloadManagementSystem.LfBranchTripping tripping : branchTrippingList) {
            copiedSystem.addLfBranchTripping(copyNetwork.getBranchById(tripping.branchToOperate().getId()),
                    tripping.branchOpen(), tripping.threshold());
        }
        return copiedSystem;
    }

    public LfBranch getMonitoredBranch() {
        return monitoredBranch;
    }

    public TwoSides getMonitoredSide() {
        return monitoredSide;
    }

    public void addLfBranchTripping(LfBranch branchToOperate, boolean branchOpen, double threshold) {
        branchTrippingList.add(new LfBranchTripping(Objects.requireNonNull(branchToOperate), branchOpen, threshold));
    }

    public List<LfBranchTripping> getBranchTrippingList() {
        return branchTrippingList;
    }
}
