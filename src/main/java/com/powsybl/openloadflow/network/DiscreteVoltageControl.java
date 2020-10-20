/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class DiscreteVoltageControl {

    public enum Mode {
        VOLTAGE,
        OFF
    }

    private DiscreteVoltageControl.Mode mode;

    private final LfBus controlled;

    private final LfBranch controller;

    private final double targetValue;

    public DiscreteVoltageControl(LfBranch controller, LfBus controlled, DiscreteVoltageControl.Mode mode, double targetValue) {
        this.controller = controller;
        this.controlled = controlled;
        this.targetValue = targetValue;
        this.mode = Objects.requireNonNull(mode);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LfBranch getController() {
        return controller;
    }

    public LfBus getControlled() {
        return controlled;
    }
}
