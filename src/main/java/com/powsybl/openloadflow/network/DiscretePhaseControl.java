/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DiscretePhaseControl extends Control {

    public enum Mode {
        CONTROLLER,
        LIMITER
    }

    public enum Unit {
        MW,
        A
    }

    private final LfBranch controllerBranch;
    private final LfBranch controlledBranch;
    private final double targetDeadband;
    private final ControlledSide controlledSide;
    private final Mode mode;
    private final Unit unit;

    public DiscretePhaseControl(LfBranch controllerBranch, LfBranch controlledBranch, ControlledSide controlledSide, DiscretePhaseControl.Mode mode,
                                double targetValue, double targetDeadband, Unit unit) {
        super(targetValue);
        this.controllerBranch = Objects.requireNonNull(controllerBranch);
        this.controlledBranch = Objects.requireNonNull(controlledBranch);
        this.targetDeadband = targetDeadband;
        this.controlledSide = Objects.requireNonNull(controlledSide);
        this.mode = Objects.requireNonNull(mode);
        this.unit = Objects.requireNonNull(unit);
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public double getTargetDeadband() {
        return targetDeadband;
    }

    public LfBranch getControllerBranch() {
        return controllerBranch;
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public Mode getMode() {
        return mode;
    }

    public Unit getUnit() {
        return unit;
    }
}
