/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfVoltageControl {

    protected final LfBus controlledBus;

    protected final Set<LfBus> controllerBuses;

    protected double targetValue;

    protected AbstractLfVoltageControl(LfBus controlledBus, double targetValue) {
        this.controlledBus = controlledBus;
        this.targetValue = targetValue;
        this.controllerBuses = new LinkedHashSet<>();
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LfBus getControlledBus() {
        return controlledBus;
    }

    public Set<LfBus> getControllerBuses() {
        return controllerBuses;
    }

    public void addControllerBus(LfBus controllerBus) {
        controllerBuses.add(Objects.requireNonNull(controllerBus));
    }
}
