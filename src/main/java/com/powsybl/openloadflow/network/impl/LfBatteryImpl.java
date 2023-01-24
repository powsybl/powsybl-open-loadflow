/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfBatteryImpl extends AbstractLfGenerator {

    private final Ref<Battery> batteryRef;

    private boolean participating;

    private double droop;

    private double participationFactor = 0.0;

    private LfBatteryImpl(Battery battery, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, battery.getTargetP());
        this.batteryRef = new Ref<>(battery);
        participating = true;
        droop = DEFAULT_DROOP;
        // get participation factor from extension
        ActivePowerControl<Battery> activePowerControl = battery.getExtension(ActivePowerControl.class);
        if (activePowerControl != null) {
            participating = activePowerControl.isParticipate();
            if (!Double.isNaN(activePowerControl.getDroop())) {
                droop = activePowerControl.getDroop();
            }
            if (!Double.isNaN(activePowerControl.getParticipationFactor())) {
                participationFactor = activePowerControl.getParticipationFactor();
            }
        }

        if (!checkActivePowerControl(battery.getTargetP(), battery.getMinP(), battery.getMaxP(), parameters, report)) {
            participating = false;
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
    public void updateState() {
        var battery = getBattery();
        battery.getTerminal()
                .setP(-targetP)
                .setQ(-battery.getTargetQ());
    }
}
