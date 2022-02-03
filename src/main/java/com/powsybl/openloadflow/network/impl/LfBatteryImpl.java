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

    private static final double DEFAULT_DROOP = 1; // why not

    private final Battery generator;

    private boolean participating;

    private double droop;

    private LfBatteryImpl(Battery generator, double plausibleActivePowerLimit, LfNetworkLoadingReport report) {
        super(generator.getP0());
        this.generator = generator;
        participating = true;
        droop = DEFAULT_DROOP;
        // get participation factor from extension
        ActivePowerControl<Battery> activePowerControl = generator.getExtension(ActivePowerControl.class);
        if (activePowerControl != null) {
            participating = activePowerControl.isParticipate() && activePowerControl.getDroop() != 0;
            if (activePowerControl.getDroop() != 0) {
                droop = activePowerControl.getDroop();
            }
        }

        if (!checkActivePowerControl(generator.getP0(), generator.getMinP(), generator.getMaxP(), plausibleActivePowerLimit, report)) {
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
        return generator.getId();
    }

    @Override
    public double getTargetQ() {
        return generator.getQ0() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return generator.getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return generator.getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(generator.getReactiveLimits());
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
        generator.getTerminal()
                .setP(-targetP)
                .setQ(-generator.getQ0());
    }
}
