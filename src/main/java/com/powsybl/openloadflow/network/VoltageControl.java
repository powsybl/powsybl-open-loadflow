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
 */
public class VoltageControl extends AbstractControl {

    public enum Mode {
        VOLTAGE,
        OFF
    }

    private Mode mode;

    public VoltageControl(Mode mode, double targetValue, ControlledSide controlledSide) {
        super(targetValue, controlledSide);
        this.mode = Objects.requireNonNull(mode);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }
}
