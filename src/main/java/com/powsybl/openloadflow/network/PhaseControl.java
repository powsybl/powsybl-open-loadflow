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
public class PhaseControl extends AbstractControl {

    public enum Mode {
        CONTROLLER,
        LIMITER,
        OFF
    }

    public enum Unit {
        MW,
        A
    }

    private Mode mode;

    private final Unit unit;

    public PhaseControl(Mode mode, double targetValue, Unit unit) {
        super(targetValue);
        this.mode = Objects.requireNonNull(mode);
        this.unit = Objects.requireNonNull(unit);
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
