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
public class DiscretePhaseControl {

    public enum ControlledSide {
        ONE,
        TWO
    }

    public enum Mode {
        CONTROLLER,
        LIMITER,
        OFF;
    }

    public enum Unit {
        MW,
        A;
    }

    private final LfBranch controller;
    private final LfBranch controlled;
    private final double targetValue;
    private final double targetDeadband;
    private final ControlledSide controlledSide;
    private Mode mode;
    private final Unit unit;

    public DiscretePhaseControl(LfBranch controller, LfBranch controlled, ControlledSide controlledSide, DiscretePhaseControl.Mode mode,
                                double targetValue, double targetDeadband, Unit unit) {
        this.controller = controller;
        this.controlled = controlled;
        this.targetValue = targetValue;
        this.targetDeadband = targetDeadband;
        this.controlledSide = Objects.requireNonNull(controlledSide);
        this.mode = Objects.requireNonNull(mode);
        this.unit = Objects.requireNonNull(unit);
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public double getTargetDeadband() {
        return targetDeadband;
    }

    public LfBranch getController() {
        return controller;
    }

    public LfBranch getControlled() {
        return controlled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    public Unit getUnit() {
        return unit;
    }
}
