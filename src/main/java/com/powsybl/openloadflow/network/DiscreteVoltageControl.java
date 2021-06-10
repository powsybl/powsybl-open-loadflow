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

    private boolean enabled;

    private final LfBus controlled;

    protected final List<LfBranch> controllers = new ArrayList<>();

    private final double targetValue;

    public DiscreteVoltageControl(LfBus controlled, boolean enabled, double targetValue) {
        this.controlled = Objects.requireNonNull(controlled);
        this.targetValue = targetValue;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setControlEnabled(boolean enabled) {
        Objects.requireNonNull(enabled);
        if (enabled != this.enabled) {
            this.enabled = enabled;
            for (LfNetworkListener listener : controlled.getNetwork().getListeners()) {
                listener.onDiscreteVoltageControlChange(this, enabled);
            }
        }
    }

    public double getTargetValue() {
        return targetValue;
    }

    public List<LfBranch> getControllers() {
        return controllers;
    }

    public void addController(LfBranch controllerBranch) {
        Objects.requireNonNull(controllerBranch);
        controllers.add(controllerBranch);
    }

    public LfBus getControlled() {
        return controlled;
    }
}
