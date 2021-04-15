/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.*;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
public class ReactivePowerControl {

    private final LfBus controlled;

    private final Set<LfBus> controllers;

    private final double targetValue;

    public ReactivePowerControl(LfBus controlled, double targetValue) {
        this.controlled = controlled;
        this.targetValue = targetValue;
        this.controllers = new LinkedHashSet<>();
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LfBus getControlledBus() {
        return controlled;
    }

    public Set<LfBus> getControllerBuses() {
        return controllers;
    }

    public void addControllerBus(LfBus controllerBus) {
        Objects.requireNonNull(controllerBus);
        controllers.add(controllerBus);
        controllerBus.setReactivePowerControl(this);
    }

    public boolean isReactivePowerControlLocal() {
        return controllers.size() == 1 && controllers.contains(controlled);
    }
}
