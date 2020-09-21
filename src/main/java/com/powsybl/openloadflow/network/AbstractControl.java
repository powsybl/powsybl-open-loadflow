/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Thomas Adam <tadam at silicom.fr>
 */
public abstract class AbstractControl {

    public enum ControlledSide {
        ONE,
        TWO,
    }

    private ControlledSide controlledSide;

    private final double targetValue;

    public AbstractControl(double targetValue) {
        this.targetValue = targetValue;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public void setControlledSide(ControlledSide controlledSide) {
        this.controlledSide = Objects.requireNonNull(controlledSide);
    }

    public double getTargetValue() {
        return targetValue;
    }
}
