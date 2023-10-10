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
    private final boolean phaseControlEnabled;
    private final boolean voltageControlEnabled;
    private Integer tapPosition;

    public BranchState(LfBranch branch) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel instanceof PiModelArray piModelArray) {
            tapPosition = piModel.getTapPosition();
            // also save modified a1 and r1 and not directly a1 and r1 to avoid restoring
            // with same values as current tap position
            a1 = piModelArray.getModifiedA1();
            r1 = piModelArray.getModifiedR1();
        } else {
            a1 = piModel.getA1();
            r1 = piModel.getR1();
        }
        phaseControlEnabled = branch.isPhaseControlEnabled();
        voltageControlEnabled = branch.isVoltageControlEnabled();
    }

    @Override
    public void restore() {
        super.restore();
        PiModel piModel = element.getPiModel();
        if (piModel instanceof PiModelArray) {
            piModel.setTapPosition(tapPosition);
        }
        piModel.setA1(a1);
        piModel.setR1(r1);
        element.setPhaseControlEnabled(phaseControlEnabled);
        element.setVoltageControlEnabled(voltageControlEnabled);
    }

    public static BranchState save(LfBranch branch) {
        return new BranchState(branch);
    }
}
