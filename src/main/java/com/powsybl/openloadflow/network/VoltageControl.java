package com.powsybl.openloadflow.network;

import java.util.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class VoltageControl {

    public enum Mode {
        VOLTAGE,        // Voltage control on
        OFF;            // Voltage control off: switched off after Pv Pq switching
    }

    private final Mode mode;

    private final LfBus controlled;

    private final Set<LfBus> controllers;

    private final double targetValue;

    public VoltageControl(LfBus controlled, double targetValue) {
        this.controlled = controlled;
        this.targetValue = targetValue;
        this.controllers = new LinkedHashSet<>();
        this.mode = Mode.VOLTAGE;
    }

    public Mode getMode() {
        return mode;
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
        controllerBus.setVoltageControl(this);
    }

    public boolean isVoltageControlLocal() {
        return controllers.contains(controlled);
    }
}
