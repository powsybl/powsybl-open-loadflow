/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final Bus bus;

    private final double nominalV;

    private final double lowVoltageLimit;

    private final double highVoltageLimit;

    protected LfBusImpl(Bus bus, LfNetwork network, double v, double angle) {
        super(network, v, angle);
        this.bus = bus;
        nominalV = bus.getVoltageLevel().getNominalV();
        lowVoltageLimit = bus.getVoltageLevel().getLowVoltageLimit();
        highVoltageLimit = bus.getVoltageLevel().getHighVoltageLimit();
    }

    public static LfBusImpl create(Bus bus, LfNetwork network) {
        Objects.requireNonNull(bus);
        return new LfBusImpl(bus, network, bus.getV(), bus.getAngle());
    }

    @Override
    public String getId() {
        return bus.getId();
    }

    @Override
    public String getVoltageLevelId() {
        return bus.getVoltageLevel().getId();
    }

    @Override
    public boolean isFictitious() {
        return false;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public double getLowVoltageLimit() {
        return lowVoltageLimit / nominalV;
    }

    @Override
    public double getHighVoltageLimit() {
        return highVoltageLimit / nominalV;
    }

    @Override
    public void updateState(boolean reactiveLimits, boolean writeSlackBus) {
        bus.setV(v.eval() * getNominalV()).setAngle(angle);

        // update slack bus
        if (slack && writeSlackBus) {
            SlackTerminal.attach(bus);
        }

        super.updateState(reactiveLimits, writeSlackBus);
    }
}
