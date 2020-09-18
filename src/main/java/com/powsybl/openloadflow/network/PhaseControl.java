/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PhaseControl {

    public enum Mode {
        CONTROLLER,
        LIMITER,
        OFF
    }

    public enum Unit {
        MW,
        A
    }

    public enum ControlledSide {
        ONE,
        TWO,
    }

    private Mode mode;

    private ControlledSide controlledSide;

    private final double targetValue;

    private final Unit unit;

    private LfBranch controlledBranch;

    public PhaseControl(Mode mode, double targetValue, Unit unit) {
        this.mode = Objects.requireNonNull(mode);
        this.targetValue = targetValue;
        this.unit = Objects.requireNonNull(unit);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
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

    public Unit getUnit() {
        return unit;
    }

    public Optional<LfBranch> getControlledBranch() {
        return Optional.ofNullable(controlledBranch);
    }

    public void setControlledBranch(LfBranch branch) {
        this.controlledBranch = Objects.requireNonNull(branch);
    }
}
