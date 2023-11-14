/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Pierre Arvy <bertrand.rix at artelys.com>
 */
public class TransformerReactivePowerControl extends Control {

    private final LfBranch controllerBranch;
    private final LfBranch controlledBranch;
    private final Double targetDeadband;
    private final ControlledSide controlledSide;

    public TransformerReactivePowerControl(LfBranch controlledBranch, ControlledSide controlledSide, LfBranch controllerRTC, double targetValue, double targetDeadband) {
        super(targetValue);
        this.targetDeadband = Objects.requireNonNull(targetDeadband);
        this.controlledBranch = Objects.requireNonNull(controlledBranch);
        this.controlledSide = Objects.requireNonNull(controlledSide);
        this.controllerBranch = Objects.requireNonNull(controllerRTC);
    }

    public Optional<Double> getTargetDeadband() {
        return Optional.ofNullable(targetDeadband);
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public LfBranch getControllerBranch() {
        return controllerBranch;
    }
}
