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
import com.powsybl.openloadflow.util.WeakReferenceUtil;
import com.powsybl.security.results.BusResult;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final WeakReference<Bus> busRef;

    private final double nominalV;

    private final double lowVoltageLimit;

    private final double highVoltageLimit;

    private final boolean participating;

    private final boolean breakers;

    protected LfBusImpl(Bus bus, LfNetwork network, double v, double angle, boolean distributedOnConformLoad,
                        boolean participating, boolean breakers) {
        super(network, v, angle, distributedOnConformLoad);
        this.busRef = new WeakReference<>(bus);
        nominalV = bus.getVoltageLevel().getNominalV();
        lowVoltageLimit = bus.getVoltageLevel().getLowVoltageLimit();
        highVoltageLimit = bus.getVoltageLevel().getHighVoltageLimit();
        this.participating = participating;
        this.breakers = breakers;
    }

    public static LfBusImpl create(Bus bus, LfNetwork network, boolean distributedOnConformLoad, boolean participating,
                                   boolean breakers) {
        Objects.requireNonNull(bus);
        return new LfBusImpl(bus, network, bus.getV(), bus.getAngle(), distributedOnConformLoad, participating, breakers);
    }

    private Bus getBus() {
        return WeakReferenceUtil.get(busRef);
    }

    @Override
    public String getId() {
        return getBus().getId();
    }

    @Override
    public String getVoltageLevelId() {
        return getBus().getVoltageLevel().getId();
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
        var bus = getBus();
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
    public List<BusResult> createBusResults() {
        var bus = getBus();
        if (breakers) {
            return List.of(new BusResult(getVoltageLevelId(), bus.getId(), v, getAngle()));
        } else {
            return bus.getVoltageLevel().getBusBreakerView().getBusesFromBusViewBusId(bus.getId())
                    .stream().map(b -> new BusResult(getVoltageLevelId(), b.getId(), v, getAngle())).collect(Collectors.toList());
        }
    }
}
