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

    private double a1 = Double.NaN;
    private double r1 = Double.NaN;
    private final boolean phaseControlEnabled;
    private final boolean voltageControlEnabled;
    private Integer tapPosition;

    public BranchState(LfBranch branch) {
        super(branch);
        PiModel piModel = branch.getPiModel();
        if (piModel instanceof PiModelArray) {
            tapPosition = piModel.getTapPosition();
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
        if (tapPosition != null) {
            piModel.setTapPosition(tapPosition);
        }
        if (!Double.isNaN(a1)) {
            piModel.setA1(a1);
        }
        if (!Double.isNaN(r1)) {
            piModel.setR1(r1);
        }
        element.setPhaseControlEnabled(phaseControlEnabled);
        element.setVoltageControlEnabled(voltageControlEnabled);
    }

    public static BranchState save(LfBranch branch) {
        return new BranchState(branch);
    }
}
