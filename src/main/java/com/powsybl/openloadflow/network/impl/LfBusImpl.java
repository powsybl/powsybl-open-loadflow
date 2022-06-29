/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final Bus bus;

    private MutableInt copyCount;

    private final int copyNumber;

    private final double nominalV;

    private final double lowVoltageLimit;

    private final double highVoltageLimit;

    private final boolean participating;

    protected LfBusImpl(Bus bus, int copyNumber, MutableInt copyCount, LfNetwork network, double v, double angle, boolean participating) {
        super(network, v, angle);
        this.bus = bus;
        this.copyNumber = copyNumber;
        this.copyCount = Objects.requireNonNull(copyCount);
        nominalV = bus.getVoltageLevel().getNominalV();
        lowVoltageLimit = bus.getVoltageLevel().getLowVoltageLimit();
        highVoltageLimit = bus.getVoltageLevel().getHighVoltageLimit();
        this.participating = participating;
    }

    public static LfBusImpl create(Bus bus, LfNetwork network, boolean participating) {
        Objects.requireNonNull(bus);
        return new LfBusImpl(bus, 0, new MutableInt(), network, bus.getV(), bus.getAngle(), participating);
    }

    @Override
    public String getId() {
        return bus.getId() + (copyNumber > 0 ? "_copy_" + copyNumber : "");
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
    public void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean distributedOnConformLoad, boolean loadPowerFactorConstant) {
        if (copyNumber != 0) {
            return;
        }

        bus.setV(v).setAngle(angle);

        // update slack bus
        if (slack && writeSlackBus) {
            SlackTerminal.attach(bus);
        }

        super.updateState(reactiveLimits, writeSlackBus, distributedOnConformLoad, loadPowerFactorConstant);
    }

    @Override
    public boolean isParticipating() {
        return participating;
    }

    @Override
    public LfBus copy() {
        copyCount.increment();
        return new LfBusImpl(bus, copyCount.getValue(), copyCount, network, v, angle, participating);
    }
}
