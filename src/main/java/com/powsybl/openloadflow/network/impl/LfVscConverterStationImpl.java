/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfVscConverterStationImpl extends AbstractLfGenerator {

    private final VscConverterStation station;

    private boolean isRectifier;

    private double lossFactor;

    private LfVscConverterStationImpl(VscConverterStation station, boolean breakers, boolean reactiveLimits, LfNetworkLoadingReport report) {
        super(getHvdcLineTargetP(station));
        this.station = station;
        this.isRectifier = HvdcConverterStations.isRectifier(station);
        this.lossFactor = station.getLossFactor();

        // local control only
        if (station.isVoltageRegulatorOn()) {
            setVoltageControl(station.getVoltageSetpoint(), station.getTerminal(), station.getRegulatingTerminal(), breakers, reactiveLimits, report);
        }
    }

    public static LfVscConverterStationImpl create(VscConverterStation station, boolean breakers, boolean reactiveLimits, LfNetworkLoadingReport report) {
        Objects.requireNonNull(station);
        return new LfVscConverterStationImpl(station, breakers, reactiveLimits, report);
    }

    private static double getHvdcLineTargetP(VscConverterStation vscCs) {
        // The active power setpoint is always positive.
        // If the converter station is at side 1 and is rectifier, targetP should be negative.
        // If the converter station is at side 1 and is inverter, targetP should be positive.
        // If the converter station is at side 2 and is rectifier, targetP should be negative.
        // If the converter station is at side 2 and is inverter, targetP should be positive.
        HvdcLine line = vscCs.getHvdcLine();
        return line.getActivePowerSetpoint() * HvdcConverterStations.getActivePowerSetpointMultiplier(vscCs);
    }

    public boolean isRectifier() {
        return isRectifier;
    }

    public double getLossFactor() {
        return lossFactor;
    }

    @Override
    public String getId() {
        return station.getId();
    }

    @Override
    public double getTargetQ() {
        return station.getReactivePowerSetpoint() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return -station.getHvdcLine().getMaxP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return station.getHvdcLine().getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(station.getReactiveLimits());
    }

    @Override
    public void updateState() {
        station.getTerminal()
                .setP(-targetP)
                .setQ(Double.isNaN(calculatedQ) ? -station.getReactivePowerSetpoint() : -calculatedQ);
    }
}
