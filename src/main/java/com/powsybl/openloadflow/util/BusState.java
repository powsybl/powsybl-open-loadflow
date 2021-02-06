/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class BusState {
    private final double v;
    private final double angle;
    private final double loadTargetP;
    private final double loadTargetQ;
    private final Map<String, Double> generatorsTargetP;
    private final boolean disabled;
    private final boolean hasVoltageControl;
    private final double generationTargetQ;

    public BusState(LfBus b) {
        this.v = b.getV();
        this.angle = b.getAngle();
        this.loadTargetP = b.getLoadTargetP();
        this.loadTargetQ = b.getLoadTargetQ();
        this.generatorsTargetP = b.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getTargetP));
        this.disabled = b.isDisabled();
        this.hasVoltageControl = b.hasVoltageControl();
        this.generationTargetQ = b.getGenerationTargetQ();
    }

    public void restoreBusState(LfBus bus, AcloadFlowEngine engine) {
        restoreDcBusState(bus);
        bus.setV(v);
        bus.setLoadTargetQ(loadTargetQ);
        bus.setGenerationTargetQ(generationTargetQ);
        bus.setVoltageControl(hasVoltageControl);
        bus.setVoltageControlSwitchOffCount(0);
    }

    public void restoreDcBusState(LfBus bus) {
        bus.setAngle(angle);
        bus.setLoadTargetP(loadTargetP);
        bus.getGenerators().forEach(g -> {
            g.setTargetP(generatorsTargetP.get(g.getId()));
        });
        bus.setDisabled(disabled);
    }
}

