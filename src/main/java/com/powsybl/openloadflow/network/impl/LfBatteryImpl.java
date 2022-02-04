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
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfBatteryImpl extends AbstractLfGenerator {

    private final Battery battery;

    private boolean participating;

    private double droop;

    private LfBatteryImpl(Battery battery, double plausibleActivePowerLimit, LfNetworkLoadingReport report) {
        super(battery.getP0());
        this.battery = battery;
        participating = true;
        droop = DEFAULT_DROOP;
        // get participation factor from extension
        ActivePowerControl<Battery> activePowerControl = battery.getExtension(ActivePowerControl.class);
        if (activePowerControl != null) {
            participating = activePowerControl.isParticipate() && activePowerControl.getDroop() != 0;
            if (activePowerControl.getDroop() != 0) {
                droop = activePowerControl.getDroop();
            }
        }

        if (!checkActivePowerControl(battery.getP0(), battery.getMinP(), battery.getMaxP(), plausibleActivePowerLimit, report)) {
            participating = false;
        }
    }

    public static LfBatteryImpl create(Battery generator, double plausibleActivePowerLimit, LfNetworkLoadingReport report) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(report);
        return new LfBatteryImpl(generator, plausibleActivePowerLimit, report);
    }

    @Override
    public String getId() {
        return battery.getId();
    }

    @Override
    public double getTargetQ() {
        return battery.getQ0() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return battery.getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return battery.getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(battery.getReactiveLimits());
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
    public void updateState() {
        battery.getTerminal()
                .setP(-targetP)
                .setQ(-battery.getQ0());
    }
}
