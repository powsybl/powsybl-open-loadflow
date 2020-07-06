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
public class VoltageControl {

    public enum ControlledSide {
        ONE,
        TWO
    }

    private final ControlledSide controlledSide;

    private final double targetValue;

    public VoltageControl(ControlledSide controlledSide, double targetValue) {
        this.controlledSide = Objects.requireNonNull(controlledSide);
        this.targetValue = targetValue;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public double getTargetValue() {
        return targetValue;
    }

}
