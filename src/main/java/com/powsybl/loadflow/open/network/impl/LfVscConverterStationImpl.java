/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.network.impl;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.loadflow.open.network.AbstractLfGenerator;
import com.powsybl.loadflow.open.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfVscConverterStationImpl extends AbstractLfGenerator {

    private final VscConverterStation station;

    private LfVscConverterStationImpl(VscConverterStation station) {
        super(getHvdcLineTargetP(station));
        this.station = station;
    }

    public static LfVscConverterStationImpl create(VscConverterStation station) {
        Objects.requireNonNull(station);
        return new LfVscConverterStationImpl(station);
    }

    private static double getHvdcLineTargetP(VscConverterStation vscCs) {
        HvdcLine line = vscCs.getHvdcLine();
        return (line.getConverterStation1() == vscCs && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)
                || (line.getConverterStation2() == vscCs && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER)
                ? -line.getActivePowerSetpoint()
                : line.getActivePowerSetpoint();
    }

    @Override
    public String getId() {
        return station.getId();
    }

    @Override
    public boolean hasVoltageControl() {
        return station.isVoltageRegulatorOn();
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
    public double getParticipationFactor() {
        return 0;
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
