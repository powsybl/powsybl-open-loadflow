/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class LfHvdcImpl extends AbstractElement implements LfHvdc {

    private final String id;

    private HvdcBus bus1;

    private HvdcBus bus2;

    private boolean acEmulation = false;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private double droop = Double.NaN;

    private double p0 = Double.NaN;

    private LfVscConverterStation converterStation1; // can be null if not in this syncrhonous network

    private LfVscConverterStation converterStation2; // can be null not in this synchronous network

    public LfHvdcImpl(String id, LfNetwork network, HvdcLine hvdcLine, boolean isHvdcAcEmulation) {
        super(network);
        this.id = Objects.requireNonNull(id);
        // buses will be updated if an LF station is connected
        this.bus1 = makeEternalHvdcBus(hvdcLine.getConverterStation1().getTerminal().getBusView().getBus());
        this.bus2 = makeEternalHvdcBus(hvdcLine.getConverterStation2().getTerminal().getBusView().getBus());
        HvdcAngleDroopActivePowerControl control = hvdcLine.getExtension(HvdcAngleDroopActivePowerControl.class);
        if (control != null && isHvdcAcEmulation) {
            acEmulation = control.isEnabled();
            droop = control.getDroop();
            p0 = control.getP0();
        }
    }

    private HvdcBus makeEternalHvdcBus(Bus bus) {
        // Simple criteria. If hte bus is only connected to the HVDC station it cannot flow
        // power. Otherwise it can.
        return new ExternalHvdcBus(bus != null && bus.getConnectedTerminalCount() > 1);
    }

    @Override
    public ElementType getType() {
        return ElementType.HVDC;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Optional<LfBus> getBus1() {
        return this.bus1.getLfBus();
    }

    @Override
    public Optional<LfBus> getBus2() {
        return this.bus2.getLfBus();
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public Evaluable getP1() {
        return this.p1;
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public Evaluable getP2() {
        return this.p2;
    }

    @Override
    public double getDroop() {
        if (Double.isNaN(droop)) {
            throw new IllegalStateException("Hvdc droop is used but control is not present");
        }
        return droop / PerUnit.SB;
    }

    @Override
    public double getP0() {
        if (Double.isNaN(p0)) {
            throw new IllegalStateException("Hvdc P0 is used but control is not present");
        }
        return p0 / PerUnit.SB;
    }

    @Override
    public boolean isAcEmulationEnabled() {
        return acEmulation;
    }

    @Override
    public Optional<LfVscConverterStation> getConverterStation1() {
        return Optional.ofNullable(converterStation1);
    }

    @Override
    public Optional<LfVscConverterStation> getConverterStation2() {
        return Optional.ofNullable(converterStation2);
    }

    @Override
    public void setConverterStation1(LfVscConverterStation converterStation1) {
        this.converterStation1 = converterStation1;
        if (converterStation1 != null) {
            this.bus1 = new LfHvdcBus(converterStation1.getBus(), converterStation1);
            converterStation1.setHvdc(this);
        }
    }

    @Override
    public void setConverterStation2(LfVscConverterStation converterStation2) {
        this.converterStation2 = converterStation2;
        if (converterStation2 != null) {
            this.bus2 = new LfHvdcBus(converterStation2.getBus(), converterStation2);
            converterStation2.setHvdc(this);
        }
    }

    @Override
    public void updateState() {
        if (converterStation1 != null) {
            ((LfVscConverterStationImpl) converterStation1).getStation().getTerminal().setP(p1.eval() * PerUnit.SB);
        }
        if (converterStation2 != null) {
            ((LfVscConverterStationImpl) converterStation2).getStation().getTerminal().setP(p2.eval() * PerUnit.SB);
        }
    }

    @Override
    public boolean isInjectingActiveFlow() {
        return !isDisabled() && acEmulation && bus1.isInternal() && bus2.isInternal();
    }

    @Override
    public boolean canTransferActivePower() {
        return bus1.canTransferActivePower() && bus2.canTransferActivePower();
    }

}
