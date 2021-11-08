/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class BranchState {

    private final LfBranch branch;

    private final double a1;
    private final double r1;
    private final boolean disabled;

    public BranchState(LfBranch branch) {
        this.branch = Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        a1 = piModel.getA1();
        r1 = piModel.getR1();
        disabled = branch.isDisabled();
    }

    public void restore() {
        PiModel piModel = branch.getPiModel();
        piModel.setA1(a1);
        piModel.setR1(r1);
        branch.setDisabled(disabled);
    }

    public static BranchState save(LfBranch branch) {
        return new BranchState(branch);
    }

    public static List<BranchState> save(Collection<LfBranch> branches) {
        return branches.stream().map(BranchState::save).collect(Collectors.toList());
    }

    public static void restore(Collection<BranchState> branchStates) {
        branchStates.forEach(BranchState::restore);
    }
}
