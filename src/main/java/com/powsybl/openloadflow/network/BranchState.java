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
public class BranchState extends ElementState<LfBranch> {

    private final double a1;
    private final double r1;
    private final DiscretePhaseControl.Mode discretePhaseControlMode;
    private final boolean disabled;

    public BranchState(LfBranch branch) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        a1 = piModel.getA1();
        r1 = piModel.getR1();
        discretePhaseControlMode = branch.getDiscretePhaseControl().map(DiscretePhaseControl::getMode).orElse(null);
        disabled = branch.isDisabled();
    }

    public void restore() {
        PiModel piModel = element.getPiModel();
        piModel.setA1(a1);
        piModel.setR1(r1);
        if (discretePhaseControlMode != null) {
            element.getDiscretePhaseControl().ifPresent(control -> control.setMode(discretePhaseControlMode));
        }
        element.setDisabled(disabled);
    }

    public static BranchState save(LfBranch branch) {
        return new BranchState(branch);
    }
}
