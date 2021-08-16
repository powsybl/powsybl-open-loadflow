/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class VoltageControl {

    private final LfBus controlled;

    private final Set<LfBus> controllers;

    private double targetValue;

    public VoltageControl(LfBus controlled, double targetValue) {
        this.controlled = controlled;
        this.targetValue = targetValue;
        this.controllers = new LinkedHashSet<>();
    }

    public double getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(double targetValue) {
        this.targetValue = targetValue;
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
        controllerBus.setVoltageControl(this);
    }

    /**
     * Check if the voltage control is ONLY local
     * @return true if the voltage control is ONLY local, false otherwise
     */
    public boolean isVoltageControlLocal() {
        return controllers.size() == 1 && controllers.contains(controlled);
    }

    /**
     * Check if the voltage control is shared
     * @return true if the voltage control is shared, false otherwise
     */
    public boolean isSharedControl() {
        return controllers.stream().flatMap(lfBus -> lfBus.getGenerators().stream()).filter(LfGenerator::hasVoltageControl).count() > 1;
    }
}
