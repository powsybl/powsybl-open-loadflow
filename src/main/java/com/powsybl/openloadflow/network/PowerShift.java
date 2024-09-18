/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.action.LoadAction;
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
     * Returns the poawer shift for a complete loss of a load
     * @param load
     * @param slackDistributionOnConformLoad
     * @return
     */
    public static PowerShift makeLoadPowerShift(Load load, boolean slackDistributionOnConformLoad) {
        double variableActivePower = Math.abs(LfLoadImpl.getAbsVariableTargetPPerUnit(load, slackDistributionOnConformLoad));
        return new PowerShift(load.getP0() / PerUnit.SB,
                variableActivePower,
                load.getQ0() / PerUnit.SB); // ensurePowerFactorConstant is not supported.
    }

    /**
     * Returns the poawer shift for a modification of a load
     * @return
     */
    public static PowerShift makeLoadPowerShift(Load load, LoadAction loadAction) {

        double activePowerShift = loadAction.getActivePowerValue().stream().map(a -> loadAction.isRelativeValue() ? a : a - load.getP0()).findAny().orElse(0);
        double reactivePowerShift = loadAction.getReactivePowerValue().stream().map(r -> loadAction.isRelativeValue() ? r : r - load.getQ0()).findAny().orElse(0);

        //   In case of a power shift, we suppose that the shift on a load P0 is exactly the same on the variable active power
        //   of P0 that could be described in a LoadDetail extension.
        //   Fictitious loads have no variable active power shift
        double variableActivePower = LfLoadImpl.isLoadNotParticipating(load) ? 0.0 : activePowerShift;
        return new PowerShift(activePowerShift / PerUnit.SB,
                variableActivePower / PerUnit.SB,
                reactivePowerShift / PerUnit.SB);
    }
}
