/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class DiscreteVoltageControl {

    public enum Mode {
        VOLTAGE_SHUNT,
        VOLTAGE_TRANSFORMER,
        OFF
    }

    private DiscreteVoltageControl.Mode mode;

    private final LfBus controlled;

    protected final List<LfBranch> controllerBranches = new ArrayList<>();

    protected final List<LfBus> controllerBuses = new ArrayList<>();

    private final double targetValue;

    public DiscreteVoltageControl(LfBus controlled, DiscreteVoltageControl.Mode mode, double targetValue) {
        this.controlled = Objects.requireNonNull(controlled);
        this.targetValue = targetValue;
        this.mode = Objects.requireNonNull(mode);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        System.out.println("toto");
        Objects.requireNonNull(mode);
        if (mode != this.mode) {
            Mode oldMode = this.mode;
            this.mode = mode;
            for (LfNetworkListener listener : controlled.getNetwork().getListeners()) {
                listener.onVoltageControlModeChange(this, oldMode, mode);
            }
        }
    }

    public double getTargetValue() {
        return targetValue;
    }

    public List<LfBranch> getControllerBranches() {
        return controllerBranches;
    }

    public List<LfBus> getControllerBuses() {
        return controllerBuses;
    }

    public void addControllerBranch(LfBranch controllerBranch) {
        Objects.requireNonNull(controllerBranch);
        controllerBranches.add(controllerBranch);
    }

    public void addControllerBus(LfBus controllerBus) {
        Objects.requireNonNull(controllerBus);
        controllerBuses.add(controllerBus);
    }

    public LfBus getControlled() {
        return controlled;
    }
}
