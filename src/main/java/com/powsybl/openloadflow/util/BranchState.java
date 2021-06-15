/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.*;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class BranchState {
    private final double a1;
    private final double r1;
    private final boolean isVoltageControllerEnabled;

    public BranchState(LfBranch b) {
        PiModel piModel = b.getPiModel();
        a1 = piModel.getA1();
        r1 = piModel.getR1();
        isVoltageControllerEnabled = b.isDiscreteVoltageControllerEnabled();
    }

    public void restoreBranchState(LfBranch branch) {
        PiModel piModel = branch.getPiModel();
        piModel.setA1(a1);
        piModel.setR1(r1);
        branch.setDiscreteVoltageControlEnabled(isVoltageControllerEnabled);
    }

    /**
     * Get the map of the states of given branches, indexed by the branch itself
     * @param branches the bus for which the state is returned
     * @return the map of the states of given branches, indexed by the branch itself
     */
    public static Map<LfBranch, BranchState> createBranchStates(Collection<LfBranch> branches) {
        return branches.stream().collect(Collectors.toMap(Function.identity(), BranchState::new));
    }

    /**
     * Set the branch states based on the given map of states
     * @param branchStates the map containing the branches states, indexed by branches
     */
    public static void restoreBranchStates(Map<LfBranch, BranchState> branchStates) {
        branchStates.forEach((b, state) -> state.restoreBranchState(b));
    }
}
