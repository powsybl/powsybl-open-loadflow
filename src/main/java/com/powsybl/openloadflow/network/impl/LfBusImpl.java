/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.openloadflow.network.Extensions.AsymBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.security.results.BusResult;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final Ref<Bus> busRef;

    private final double nominalV;

    private final double lowVoltageLimit;

    private final double highVoltageLimit;

    private final boolean participating;

    private final boolean breakers;

    protected LfBusImpl(Bus bus, LfNetwork network, double v, double angle, LfNetworkParameters parameters,
                        boolean participating) {
        super(network, v, angle, parameters.isDistributedOnConformLoad());
        this.busRef = Ref.create(bus, parameters.isCacheEnabled());
        nominalV = bus.getVoltageLevel().getNominalV();
        lowVoltageLimit = bus.getVoltageLevel().getLowVoltageLimit();
        highVoltageLimit = bus.getVoltageLevel().getHighVoltageLimit();
        this.participating = participating;
        this.breakers = parameters.isBreakers();
    }

    public static LfBusImpl create(Bus bus, LfNetwork network, LfNetworkParameters parameters, boolean participating) {
        Objects.requireNonNull(bus);
        Objects.requireNonNull(parameters);
        return new LfBusImpl(bus, network, bus.getV(), Math.toRadians(bus.getAngle()), parameters, participating);
    }

    public Bus getBus() {
        return busRef.get();
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
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var bus = getBus();
        bus.setV(Math.max(v, 0.0)).setAngle(Math.toDegrees(angle));

        // update slack bus
        if (slack && parameters.isWriteSlackBus()) {
            SlackTerminal.attach(bus);
        }

        super.updateState(parameters);
    }

    @Override
    public boolean isParticipating() {
        return participating;
    }

    @Override
    public List<BusResult> createBusResults() {
        var bus = getBus();
        if (breakers) {
            return List.of(new BusResult(getVoltageLevelId(), bus.getId(), v, Math.toDegrees(angle)));
        } else {
            return bus.getVoltageLevel().getBusBreakerView().getBusesFromBusViewBusId(bus.getId())
                    .stream().map(b -> new BusResult(getVoltageLevelId(), b.getId(), v, Math.toDegrees(angle))).collect(Collectors.toList());
        }
    }

    @Override
    public double getTargetP() {
        AsymBus asymBus = (AsymBus) this.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        boolean isAbcConstantLoad = true;
        if (asymBus != null && isAbcConstantLoad) {
            return getGenerationTargetP(); // TODO : uncomment when we will use a ABC constant load
            // we use the detection of the asymmetry extension at bus to check if we are in dissym calculation
            // in this case, load target is set to zero and the constant-balanced load model (in 3 phased representation) is replaced by a model depending on vd, vi, vo (equivalent fortescue representation
        }
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        AsymBus asymBus = (AsymBus) this.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        boolean isAbcConstantLoad = true;
        if (asymBus != null && isAbcConstantLoad) {

            return getGenerationTargetQ(); // TODO : uncomment when we will use a ABC constant load
            // we use the detection of the asymmetry extension at bus to check if we are in dissym calculation
            // in this case, load target is set to zero and the constant-balanced load model (in 3 phased representation) is replaced by a model depending on vd, vi, vo (equivalent fortescue representation
        }
        return getGenerationTargetQ() - getLoadTargetQ();
    }
}
