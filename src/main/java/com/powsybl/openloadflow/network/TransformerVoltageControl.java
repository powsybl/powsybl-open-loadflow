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
import java.util.Optional;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class TransformerVoltageControl extends AbstractDiscreteVoltageControl {

    private final List<LfBranch> controllers = new ArrayList<>();

    private Double targetDeadband;

    public TransformerVoltageControl(LfBus controlled, double targetValue, Double targetDeadband) {
        super(controlled, targetValue);
        this.targetDeadband = targetDeadband;
    }

    public Optional<Double> getTargetDeadband() {
        return Optional.ofNullable(targetDeadband);
    }

    public void setTargetDeadband(Double targetDeadband) {
        this.targetDeadband = targetDeadband;
    }

    public List<LfBranch> getControllers() {
        return controllers;
    }

    public void addController(LfBranch controllerBranch) {
        Objects.requireNonNull(controllerBranch);
        controllers.add(controllerBranch);
    }
}
