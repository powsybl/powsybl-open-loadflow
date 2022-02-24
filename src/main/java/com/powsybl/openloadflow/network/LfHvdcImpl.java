/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.openloadflow.network.impl.LfVscConverterStationImpl;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public final class LfHvdcImpl extends AbstractElement implements LfHvdc {

    private String id;

    private LfBus bus1;

    private LfBus bus2;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private double droop;

    private double p0;

    private LfVscConverterStationImpl vsc1 = null;

    private LfVscConverterStationImpl vsc2 = null;

    public LfHvdcImpl(HvdcAngleDroopActivePowerControl control, LfBus bus1, LfBus bus2, LfNetwork network, String hvdcId) {
        super(network);
        this.id = hvdcId;
        this.bus1 = bus1;
        this.bus2 = bus2;
        droop = control.getDroop(); // should be in per unit? In degree/MW?
        p0 = control.getP0();
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
    public double getDroop() {
        return droop;
    }

    @Override
    public double getP0() {
        return p0 / PerUnit.SB;
    }

    @Override
    public LfVscConverterStationImpl getConverterStation1() {
        return vsc1;
    }

    @Override
    public LfVscConverterStationImpl getConverterStation2() {
        return vsc2;
    }

    @Override
    public void setConverterStation1(LfVscConverterStationImpl converterStation1) {
        this.vsc1 = converterStation1;
        converterStation1.setTargetP(0.);
    }

    @Override
    public void setConverterStation2(LfVscConverterStationImpl converterStation2) {
        this.vsc2 = converterStation2;
        converterStation2.setTargetP(0);
    }

    @Override
    public ElementType getType() {
        return ElementType.HVDC;
    }

    @Override
    public String getId() {
        return id;
    }
}
