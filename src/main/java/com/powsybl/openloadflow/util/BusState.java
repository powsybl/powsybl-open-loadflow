/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.ac.ReactiveLimitsOuterLoop;
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
    private final Map<String, Boolean> generatorsIsParticipating;
    private final boolean isParticipating;
    private final boolean hasVoltageControl;
    private final double generationTargetQ;

    public BusState(LfBus b) {
        this.v = b.getV();
        this.angle = b.getAngle();
        this.loadTargetP = b.getLoadTargetP();
        this.loadTargetQ = b.getLoadTargetQ();
        this.generatorsTargetP = b.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::getTargetP));
        this.generatorsIsParticipating = b.getGenerators().stream().collect(Collectors.toMap(LfGenerator::getId, LfGenerator::isParticipating));
        this.isParticipating = b.isParticipatingToLoadActivePowerDistribution();
        this.hasVoltageControl = b.hasVoltageControl();
        this.generationTargetQ = b.getGenerationTargetQ();
    }

    public void restoreBusState(LfBus bus, AcloadFlowEngine engine) {
        restoreBusStateActiveOnly(bus);
        bus.setV(v);
        bus.setLoadTargetQ(loadTargetQ);
        if (hasVoltageControl && !bus.hasVoltageControl()) { // b is now PQ bus.
            ReactiveLimitsOuterLoop.switchPqPv(bus, engine.getEquationSystem(), engine.getVariableSet());
        }
        if (!hasVoltageControl && bus.hasVoltageControl()) { // b is now PV bus.
            ReactiveLimitsOuterLoop.switchPvPq(bus, engine.getEquationSystem(), engine.getVariableSet(), generationTargetQ);
        }
        bus.setVoltageControlSwitchOffCount(0);
    }

    public void restoreBusStateActiveOnly(LfBus bus) {
        bus.setAngle(angle);
        bus.setLoadTargetP(loadTargetP);
        bus.getGenerators().forEach(g -> {
            g.setTargetP(generatorsTargetP.get(g.getId()));
            g.setParticipating(generatorsIsParticipating.get(g.getId()));
        });
        bus.setParticipatingToLoadActivePowerDistribution(isParticipating);
    }
}

