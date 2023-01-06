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
public class LfSecondaryVoltageControl {

    protected final LfBus pilotBus;

    protected final Set<LfBus> controlledBuses;

    protected double targetValue;

    public LfSecondaryVoltageControl(LfBus pilotBus, double targetValue) {
        this.pilotBus = pilotBus;
        this.targetValue = targetValue;
        this.controlledBuses = new LinkedHashSet<>();
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LfBus getPilotBus() {
        return pilotBus;
    }

    public Set<LfBus> getControlledBuses() {
        return controlledBuses;
    }

    public void addControlledBus(LfBus controlledBus) {
        controlledBuses.add(Objects.requireNonNull(controlledBus));
    }
}
