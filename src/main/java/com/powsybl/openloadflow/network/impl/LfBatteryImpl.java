/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.extensions.VoltageRegulation;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class LfBatteryImpl extends AbstractLfGenerator {

    private final Ref<Battery> batteryRef;

    private boolean initialParticipating;

    private boolean participating;

    private final double droop;

    private final double participationFactor;

    private final double maxTargetP;

    private final double minTargetP;

    private LfBatteryImpl(Battery battery, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, battery.getTargetP() / PerUnit.SB);
        this.batteryRef = Ref.create(battery, parameters.isCacheEnabled());
        var apcHelper = ActivePowerControlHelper.create(battery, battery.getMinP(), battery.getMaxP());
        initialParticipating = apcHelper.participating();
        participating = initialParticipating;
        participationFactor = apcHelper.participationFactor();
        droop = apcHelper.droop();
        minTargetP = apcHelper.minTargetP();
        maxTargetP = apcHelper.maxTargetP();

        if (!checkActivePowerControl(getId(), battery.getTargetP(), battery.getMaxP(), minTargetP, maxTargetP,
                parameters.getPlausibleActivePowerLimit(), parameters.isUseActiveLimits(), report)) {
            participating = false;
        }

        // get voltage control from extension
        VoltageRegulation voltageRegulation = battery.getExtension(VoltageRegulation.class);
        if (voltageRegulation != null && voltageRegulation.isVoltageRegulatorOn()) {
            setVoltageControl(voltageRegulation.getTargetV(), battery.getTerminal(), voltageRegulation.getRegulatingTerminal(), parameters, report);
        }
    }

    public static LfBatteryImpl create(Battery battery, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        Objects.requireNonNull(battery);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(report);
        return new LfBatteryImpl(battery, network, parameters, report);
    }

    private Battery getBattery() {
        return batteryRef.get();
    }

    @Override
    public String getId() {
        return getBattery().getId();
    }

    @Override
    public double getTargetQ() {
        return getBattery().getTargetQ() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return getBattery().getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return getBattery().getMaxP() / PerUnit.SB;
    }

    @Override
    public double getMinTargetP() {
        return minTargetP / PerUnit.SB;
    }

    @Override
    public double getMaxTargetP() {
        return maxTargetP / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(getBattery().getReactiveLimits());
    }

    @Override
    public boolean isParticipating() {
        return participating;
    }

    @Override
    public void setParticipating(boolean participating) {
        this.participating = participating;
    }

    @Override
    public double getDroop() {
        return droop;
    }

    @Override
    public double getParticipationFactor() {
        return participationFactor;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var battery = getBattery();
        battery.getTerminal()
                .setP(-targetP * PerUnit.SB)
                .setQ(Double.isNaN(calculatedQ) ? -getTargetQ() * PerUnit.SB : -calculatedQ * PerUnit.SB);
    }

    @Override
    public void reApplyActivePowerControlChecks(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        participating = initialParticipating;
        var battery = getBattery();
        if (!checkActivePowerControl(battery.getId(), targetP * PerUnit.SB, battery.getMaxP(), minTargetP, maxTargetP,
                parameters.getPlausibleActivePowerLimit(), parameters.isUseActiveLimits(), report)) {
            participating = false;
        }
    }
}
