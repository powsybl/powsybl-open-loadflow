/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Load;
import com.powsybl.openloadflow.network.impl.LfLoadImpl;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PowerShift {

    private double active;

    private double variableActive;

    private double reactive;

    public PowerShift() {
        this(0d, 0d, 0d);
    }

    public PowerShift(double active, double variableActive, double reactive) {
        this.active = active;
        this.variableActive = variableActive;
        this.reactive = reactive;
    }

    public double getActive() {
        return active;
    }

    public double getVariableActive() {
        return variableActive;
    }

    public double getReactive() {
        return reactive;
    }

    public void add(PowerShift other) {
        Objects.requireNonNull(other);
        active += other.getActive();
        variableActive += other.getVariableActive();
        reactive += other.getReactive();
    }

    @Override
    public String toString() {
        return "PowerShift("
                + active + ", "
                + variableActive + ", "
                + reactive + ")";
    }

    /**
     * Returns the power shift for a complete loss of a load
     */
    public static PowerShift createPowerShift(Load load, boolean slackDistributionOnConformLoad) {
        double variableActivePower = Math.abs(LfLoadImpl.getAbsVariableTargetPPerUnit(load, slackDistributionOnConformLoad));
        return new PowerShift(load.getP0() / PerUnit.SB,
                variableActivePower,
                load.getQ0() / PerUnit.SB); // ensurePowerFactorConstant is not supported.
    }
}
