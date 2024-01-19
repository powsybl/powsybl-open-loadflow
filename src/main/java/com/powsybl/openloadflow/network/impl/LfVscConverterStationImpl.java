/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.util.HvdcUtils;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfVscConverterStationImpl extends AbstractLfGenerator implements LfVscConverterStation {

    private final Ref<VscConverterStation> stationRef;

    private final double lossFactor;

    private final double targetPInIdmTopology;

    private LfHvdc hvdc; // always set excepted if HVDCLine is not in the network

    private boolean initialized = false;

    public LfVscConverterStationImpl(VscConverterStation station, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, HvdcUtils.getConverterStationTargetP(station) / PerUnit.SB);
        this.stationRef = Ref.create(station, parameters.isCacheEnabled());
        this.lossFactor = station.getLossFactor();
        this.targetPInIdmTopology = LfHvdc.getTargetPInIdmTopology(station) / PerUnit.SB;

        // local control only
        if (station.isVoltageRegulatorOn()) {
            setVoltageControl(station.getVoltageSetpoint(), station.getTerminal(), station.getRegulatingTerminal(), parameters, report);
        }
        initialized = true;
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

        if (hvdc == null) {
            // If called during initialization return the nominal target P
            // If called at runtime, may return 0 if there is no transit per IDM topology
            return initialized ? targetPInIdmTopology : super.getTargetP();
        }
        // because in case of AC emulation, active power is injected by HvdcAcEmulationSideXActiveFlowEquationTerm equations
        if (hvdc.isAcEmulationActive()) {
            return 0;
        }

        return hvdc.canTransferActivePower() ? super.getTargetP() : 0;
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
        HvdcLine hvdcLine = getStation().getHvdcLine();
        return hvdcLine != null ? -hvdcLine.getMaxP() / PerUnit.SB : -Double.MAX_VALUE;
    }

    @Override
    public double getMaxP() {
        HvdcLine hvdcLine = getStation().getHvdcLine();
        return hvdcLine != null ? hvdcLine.getMaxP() / PerUnit.SB : Double.MAX_VALUE;
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
        if (hvdc == null || !hvdc.isAcEmulationActive()) { // because when AC emulation is activated, update of p is done in LFHvdcImpl
            station.getTerminal().setP(hvdc.canTransferActivePower() ? -targetP * PerUnit.SB : 0);
        }
    }

    @Override
    public Optional<LfBus> getOtherStationBus() {
        if (hvdc == null) {
            return Optional.empty();
        }
        boolean isStation1 = hvdc.getConverterStation1().map(s -> s == this).orElse(false);
        return isStation1 ? hvdc.getBus2() : hvdc.getBus1();
    }
}
