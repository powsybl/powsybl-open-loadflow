/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class BusState {

    private final LfBus bus;

    private final double angle;
    private final double loadTargetP;
    private final double loadTargetQ;
    private final Map<String, Double> generatorsTargetP;
    private final double generationTargetQ;
    private final boolean isVoltageControllerEnabled;
    private final DiscreteVoltageControl.Mode discreteVoltageControlMode;
    private final boolean disabled;

    public BusState(LfBus bus) {
        this.bus = Objects.requireNonNull(bus);
        this.angle = bus.getAngle();
        this.loadTargetP = bus.getLoadTargetP();
        this.loadTargetQ = bus.getLoadTargetQ();
        this.generatorsTargetP = bus.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getTargetP));
        this.generationTargetQ = bus.getGenerationTargetQ();
        this.isVoltageControllerEnabled = bus.isVoltageControllerEnabled();
        discreteVoltageControlMode = bus.getDiscreteVoltageControl().map(DiscreteVoltageControl::getMode).orElse(null);
        this.disabled = bus.isDisabled();
    }

    public void restore() {
        bus.setAngle(angle);
        bus.setLoadTargetP(loadTargetP);
        bus.setLoadTargetQ(loadTargetQ);
        bus.getGenerators().forEach(g -> g.setTargetP(generatorsTargetP.get(g.getId())));
        bus.setGenerationTargetQ(generationTargetQ);
        bus.setVoltageControllerEnabled(isVoltageControllerEnabled);
        bus.setVoltageControlSwitchOffCount(0);
        if (discreteVoltageControlMode != null) {
            bus.getDiscreteVoltageControl().ifPresent(control -> control.setMode(discreteVoltageControlMode));
        }
        bus.setDisabled(disabled);
    }

    public static BusState save(LfBus bus) {
        return new BusState(bus);
    }

    public static List<BusState> save(Collection<LfBus> buses) {
        return buses.stream().map(BusState::save).collect(Collectors.toList());
    }

    public static void restore(Collection<BusState> busStates) {
        busStates.forEach(BusState::restore);
    }
}
