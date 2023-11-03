/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public class ReactivePowerControl extends Control {

    public enum Type {
        GENERATOR,
    }

    protected final ReactivePowerControl.Type type;

    protected final LfBranch controlledBranch;

    protected final ControlledSide controlledSide;

    protected final List<LfBus> controllerBuses = new ArrayList<>();

    public ReactivePowerControl(double targetValue, Type type, LfBranch controlledBranch, ControlledSide controlledSide) {
        super(targetValue);
        this.type = Objects.requireNonNull(type);
        this.controlledBranch = Objects.requireNonNull(controlledBranch);
        this.controlledSide = Objects.requireNonNull(controlledSide);
    }

    public LfBranch getControlledBranch() {
        return controlledBranch;
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public List<LfBus> getControllerBuses() {
        return controllerBuses;
    }

    public LfBus getMainControllerBus() {
        return controllerBuses.isEmpty() ? null : controllerBuses.get(0);
    }

    public void addControllerBus(LfBus controllerBus) {
        controllerBuses.add(Objects.requireNonNull(controllerBus));
    }

    public ReactivePowerControl.Type getType() {
        return type;
    }
}
