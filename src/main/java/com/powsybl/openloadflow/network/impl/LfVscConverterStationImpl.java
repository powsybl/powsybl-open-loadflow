/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfVscConverterStation;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfVscConverterStationImpl extends AbstractLfGenerator implements LfVscConverterStation {

    private final Ref<VscConverterStation> stationRef;

    private final double lossFactor;

    private LfHvdc hvdc; // set only when AC emulation is activated

    public LfVscConverterStationImpl(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, HvdcConverterStations.getConverterStationTargetP(station, parameters.isBreakers()) / PerUnit.SB);
        this.stationRef = Ref.create(station, parameters.isCacheEnabled());
        this.lossFactor = station.getLossFactor();

        // local control only
        if (station.isVoltageRegulatorOn()) {
            setVoltageControl(station.getVoltageSetpoint(), station.getTerminal(), station.getRegulatingTerminal(), parameters, report);
        }
    }

    public static LfVscConverterStationImpl create(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        Objects.requireNonNull(station);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        return new LfVscConverterStationImpl(station, network, parameters, report);
    }

    VscConverterStation getStation() {
        return stationRef.get();
    }

    public void setHvdc(LfHvdc hvdc) {
        this.hvdc = hvdc;
    }

    @Override
    public double getTargetP() {
        // because in case of AC emulation, active power is injected by HvdcAcEmulationSideXActiveFlowEquationTerm equations
        return hvdc == null ? super.getTargetP() : 0;
    }

    @Override
    public double getLossFactor() {
        return lossFactor;
    }

    @Override
    public String getId() {
        return getStation().getId();
    }

    @Override
    public double getTargetQ() {
        return getStation().getReactivePowerSetpoint() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return -getStation().getHvdcLine().getMaxP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return getStation().getHvdcLine().getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(getStation().getReactiveLimits());
    }

    @Override
    public void updateState() {
        var station = getStation();
        station.getTerminal()
                .setQ(Double.isNaN(calculatedQ) ? -station.getReactivePowerSetpoint() : -calculatedQ * PerUnit.SB);
        if (hvdc == null) { // because when AC emulation is activated, update of p is done in LFHvdcImpl
            station.getTerminal().setP(-targetP * PerUnit.SB);
        }
    }
}
