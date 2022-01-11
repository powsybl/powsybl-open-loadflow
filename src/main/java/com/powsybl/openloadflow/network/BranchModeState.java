/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class BranchModeState extends ElementState<LfBranch> {

    private final DiscretePhaseControl.Mode discretePhaseControlMode;

    public BranchModeState(LfBranch branch) {
        super(branch);
        discretePhaseControlMode = branch.getDiscretePhaseControl().map(DiscretePhaseControl::getMode).orElse(null);
    }

    @Override
    public void restore() {
        if (discretePhaseControlMode != null) {
            element.getDiscretePhaseControl().ifPresent(control -> control.setMode(discretePhaseControlMode));
        }
    }

    public static BranchModeState save(LfBranch branch) {
        return new BranchModeState(branch);
    }
}
