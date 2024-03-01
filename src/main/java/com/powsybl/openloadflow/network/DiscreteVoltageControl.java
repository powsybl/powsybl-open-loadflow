/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Optional;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class DiscreteVoltageControl<T extends LfElement> extends VoltageControl<T> {

    private Double targetDeadband;

    protected DiscreteVoltageControl(LfBus controlled, Type type, int targetPriority, double targetValue, Double targetDeadband) {
        super(targetValue, type, targetPriority, controlled);
        this.targetDeadband = targetDeadband;
    }

    public Optional<Double> getTargetDeadband() {
        return Optional.ofNullable(targetDeadband);
    }

    public void setTargetDeadband(Double targetDeadband) {
        this.targetDeadband = targetDeadband;
    }
}
