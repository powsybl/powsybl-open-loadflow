/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.ac.ReactiveLimitsOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
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

    public void restoreBusState(LfBus bus, EquationSystem equationSystem, VariableSet variableSet) {
        restoreDcBusState(bus);
        bus.setV(v);
        bus.setLoadTargetQ(loadTargetQ);
        if (hasVoltageControl && !bus.hasVoltageControl()) { // b is now PQ bus.
            ReactiveLimitsOuterLoop.switchPqPv(bus, equationSystem, variableSet);
        }
        if (!hasVoltageControl && bus.hasVoltageControl()) { // b is now PV bus.
            ReactiveLimitsOuterLoop.switchPvPq(bus, equationSystem, variableSet, generationTargetQ);
        }
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

    /**
     * Get the map of the states of given buses, indexed by the bus itself
     * @param buses the bus for which the state is returned
     * @return the map of the states of given buses, indexed by the bus itself
     */
    public static Map<LfBus, BusState> createBusStates(Collection<LfBus> buses) {
        return buses.stream().collect(Collectors.toMap(Function.identity(), BusState::new));
    }

    /**
     * Set the bus states based on the given map of states
     * @param busStates the map containing the bus states, indexed by buses
     * @param engine AcLoadFlowEngine to operate the PqPv switching if the bus has lost its voltage control
     */
    public static void restoreBusStates(Map<LfBus, BusState> busStates, AcloadFlowEngine engine) {
        restoreBusStates(busStates, engine.getEquationSystem(), engine.getVariableSet());
    }

    /**
     * Set the bus states based on the given map of states
     * @param busStates the map containing the bus states, indexed by buses
     * @param equationSystem to operate the PqPv switching if the bus has lost its voltage control
     * @param variableSet to operate the PqPv switching if the bus has lost its voltage control
     */
    public static void restoreBusStates(Map<LfBus, BusState> busStates, EquationSystem equationSystem, VariableSet variableSet) {
        busStates.forEach((b, state) -> state.restoreBusState(b, equationSystem, variableSet));
    }

    /**
     * Set the bus states based on the given map of states
     * @param busStates the map containing the bus states, indexed by buses
     */
    public static void restoreDcBusStates(Map<LfBus, BusState> busStates) {
        busStates.forEach((b, state) -> state.restoreDcBusState(b));
    }
}

