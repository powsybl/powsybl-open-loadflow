/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public class ReactivePowerControl {

    public enum ControlledSide {
        ONE,
        TWO
    }

    private final LfBranch controlledBranch;
    private final ControlledSide controlledSide;
    private final LfBus controller;
    private final double targetValue;

    public ReactivePowerControl(LfBranch controlledBranch, ControlledSide controlledSide, LfBus controller, double targetValue) {
        this.controlledBranch = controlledBranch;
        this.controlledSide = controlledSide;
        this.controller = controller;
        this.targetValue = targetValue;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public LfBus getControllerBus() {
        return controller;
    }
}
