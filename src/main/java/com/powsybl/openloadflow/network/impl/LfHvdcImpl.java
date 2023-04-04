/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfHvdcImpl extends AbstractElement implements LfHvdc {

    private final String id;

    private final LfBus bus1;

    private final LfBus bus2;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private final double power;

    private boolean isControllerSide1; // for active power set point only, rectifier side.

    private final double r;

    private boolean acEmulation = false;

    private double droop = 0.0;

    private double p0 = 0.0;

    private LfVscConverterStation converterStation1;

    private LfVscConverterStation converterStation2;

    public LfHvdcImpl(String id, LfBus bus1, LfBus bus2, LfNetwork network, HvdcLine hvdcLine) {
        super(network);
        this.id = Objects.requireNonNull(id);
        this.bus1 = bus1;
        this.bus2 = bus2;
        this.power = hvdcLine.getActivePowerSetpoint();
        this.isControllerSide1 = HvdcConverterStations.isRectifier(hvdcLine.getConverterStation1());
        double zb = PerUnit.zb(hvdcLine.getNominalV());
        this.r = hvdcLine.getR() / zb;
        HvdcAngleDroopActivePowerControl control = hvdcLine.getExtension(HvdcAngleDroopActivePowerControl.class);
        if (control != null) {
            droop = control.getDroop();
            p0 = control.getP0();
        }
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
    public LfBus getBus1() {
        return this.bus1;
    }

    @Override
    public LfBus getBus2() {
        return this.bus2;
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
    public double getPower() {
        return power / PerUnit.SB;
    }

    @Override
    public double getLosses(double p) {
        return r * p * p;
    }

    @Override
    public boolean isControllerSide1() {
        return isControllerSide1;
    }

    @Override
    public void enableAcEmulation(boolean enabled) {
        this.acEmulation = enabled;
    }

    @Override
    public boolean isAcEmulationEnabled() {
        return acEmulation;
    }

    @Override
    public double getDroop() {
        return droop / PerUnit.SB;
    }

    @Override
    public double getP0() {
        return p0 / PerUnit.SB;
    }

    @Override
    public LfVscConverterStation getConverterStation1() {
        return converterStation1;
    }

    @Override
    public LfVscConverterStation getConverterStation2() {
        return converterStation2;
    }

    @Override
    public void setConverterStation1(LfVscConverterStation converterStation1) {
        this.converterStation1 = Objects.requireNonNull(converterStation1);
        converterStation1.setHvdc(this);
    }

    @Override
    public void setConverterStation2(LfVscConverterStation converterStation2) {
        this.converterStation2 = Objects.requireNonNull(converterStation2);
        converterStation2.setHvdc(this);
    }

    @Override
    public void updateState() {
        ((LfVscConverterStationImpl) converterStation1).getStation().getTerminal().setP(p1.eval() * PerUnit.SB);
        ((LfVscConverterStationImpl) converterStation2).getStation().getTerminal().setP(p2.eval() * PerUnit.SB);
    }
}
