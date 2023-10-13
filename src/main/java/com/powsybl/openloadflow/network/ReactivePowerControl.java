/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public class ReactivePowerControl extends Control {

    public enum Type {
        GENERATOR,
    }

    private final LfBranch controlledBranch;
    private final ControlledSide controlledSide;
    private final LfBus controllerBus;

    public ReactivePowerControl(LfBranch controlledBranch, ControlledSide controlledSide, LfBus controllerBus, double targetValue) {
        super(targetValue);
        this.controlledBranch = Objects.requireNonNull(controlledBranch);
        this.controlledSide = Objects.requireNonNull(controlledSide);
        this.controllerBus = Objects.requireNonNull(controllerBus);
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public LfBus getControllerBus() {
        return controllerBus;
    }
}
