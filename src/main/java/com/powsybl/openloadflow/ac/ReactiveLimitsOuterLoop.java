/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    @Override
    public String getName() {
        return "Reactive limits";
    }

    private void switchPvPq(LfBus bus, EquationSystem equationSystem, VariableSet variableSet, double newGenerationTargetQ) {
        bus.setGenerationTargetQ(newGenerationTargetQ);
        bus.setVoltageControl(false);

        Equation qEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
        qEq.setActive(true);

        LfBus controlledBus = bus.getControlledBus().orElse(null);
        if (controlledBus != null) {
            // clean reactive power distribution equations
            controlledBus.getControllerBuses().forEach(b -> equationSystem.removeEquation(b.getNum(), EquationType.ZERO));

            // controlled bus has a voltage equation only if one of the controller bus has voltage control on
            List<LfBus> controllerBusesWithVoltageControlOn = controlledBus.getControllerBuses().stream()
                    .filter(LfBus::hasVoltageControl)
                    .collect(Collectors.toList());
            equationSystem.createEquation(controlledBus.getNum(), EquationType.BUS_V).setActive(!controllerBusesWithVoltageControlOn.isEmpty());

            // create reactive power equations on controller buses that have voltage control on
            if (!controllerBusesWithVoltageControlOn.isEmpty()) {
                AcEquationSystem.createReactivePowerDistributionEquations(equationSystem, variableSet, controllerBusesWithVoltageControlOn);
            }
        } else {
            Equation vEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V);
            vEq.setActive(false);
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        List<LfBus> pvToPqBuses = new ArrayList<>();
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.hasVoltageControl()) { // PV bus
                double q = bus.getCalculatedQ() + bus.getLoadTargetQ();
                double minQ = bus.getMinQ();
                double maxQ = bus.getMaxQ();
                if (q < minQ) {
                    // switch PV -> PQ
                    switchPvPq(bus, context.getEquationSystem(), context.getVariableSet(), minQ);
                    LOGGER.trace("Switch bus {} PV -> PQ, q={} < minQ={}", bus.getId(), q * PerUnit.SB, minQ * PerUnit.SB);
                    pvToPqBuses.add(bus);
                    status = OuterLoopStatus.UNSTABLE;
                } else if (q > maxQ) {
                    // switch PV -> PQ
                    switchPvPq(bus, context.getEquationSystem(), context.getVariableSet(), maxQ);
                    LOGGER.trace("Switch bus {} PV -> PQ, q={} MVar > maxQ={}", bus.getId(), q * PerUnit.SB, maxQ * PerUnit.SB);
                    pvToPqBuses.add(bus);
                    status = OuterLoopStatus.UNSTABLE;
                }
            } else { // PQ bus
                // TODO
            }
        }
        if (!pvToPqBuses.isEmpty()) {
            LOGGER.info("{} buses switched PV -> PQ", pvToPqBuses.size());
        }
        return status;
    }
}
