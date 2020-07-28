/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.extensions.SlackTerminal;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final Bus bus;

    private final double nominalV;

    protected LfBusImpl(Bus bus, double v, double angle) {
        super(v, angle);
        this.bus = bus;
        nominalV = bus.getVoltageLevel().getNominalV();
    }

    public static LfBusImpl create(Bus bus) {
        Objects.requireNonNull(bus);
        return new LfBusImpl(bus, bus.getV(), bus.getAngle());
    }

    @Override
    public String getId() {
        return bus.getId();
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
    public void updateState(boolean reactiveLimits, boolean writeSlackBus) {
        bus.setV(v).setAngle(angle);

        // update slack bus
        if (slack && writeSlackBus) {
            SlackTerminal.attach(bus);
        }

        super.updateState(reactiveLimits, writeSlackBus);
    }
}
