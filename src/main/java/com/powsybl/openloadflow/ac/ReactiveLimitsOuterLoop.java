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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    private static final Comparator<PvToPqBus> BY_NOMINAL_V_COMPARISON = Comparator.comparingDouble(pvToPqBus -> -pvToPqBus.bus.getNominalV());

    private static final Comparator<PvToPqBus> BY_TARGET_P_COMPARISON = Comparator.comparingDouble(pvToPqBus -> -pvToPqBus.bus.getTargetP());

    private static final Comparator<PvToPqBus> BY_ID_COMPARISON = Comparator.comparing(pvToPqBus -> pvToPqBus.bus.getId());

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
            controlledBus.getControllerBuses().forEach(b -> equationSystem.removeEquation(b.getNum(), EquationType.ZERO_Q));

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

    private enum ReactiveLimitDirection {
        MIN,
        MAX
    }

    private static final class PvToPqBus {

        private final LfBus bus;

        private final double q;

        private final double qLimit;

        private final ReactiveLimitDirection limitDirection;

        private PvToPqBus(LfBus bus, double q, double qLimit, ReactiveLimitDirection limitDirection) {
            this.bus = bus;
            this.q = q;
            this.qLimit = qLimit;
            this.limitDirection = limitDirection;
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<PvToPqBus> pvToPqBuses = new ArrayList<>();
        int remainingPvBusCount = 0;
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.hasVoltageControl()) { // PV bus
                double q = bus.getCalculatedQ() + bus.getLoadTargetQ();
                double minQ = bus.getMinQ();
                double maxQ = bus.getMaxQ();
                if (q < minQ) {
                    pvToPqBuses.add(new PvToPqBus(bus, q, minQ, ReactiveLimitDirection.MIN));
                } else if (q > maxQ) {
                    // switch PV -> PQ
                    pvToPqBuses.add(new PvToPqBus(bus, q, maxQ, ReactiveLimitDirection.MAX));
                } else {
                    remainingPvBusCount++;
                }
            } else { // PQ bus
                // TODO
            }
        }

        if (!pvToPqBuses.isEmpty()) {
            if (remainingPvBusCount == 0) {
                // keep one bus PV, the strongest one which is one at the highest nominal level and highest active power
                // target
                PvToPqBus strongestPvToPqBus = pvToPqBuses.stream()
                        .min(BY_NOMINAL_V_COMPARISON
                                .thenComparing(BY_TARGET_P_COMPARISON)
                                .thenComparing(BY_ID_COMPARISON)) // for stability of the sort
                        .orElseThrow(IllegalStateException::new);
                pvToPqBuses.remove(strongestPvToPqBus);
                remainingPvBusCount++;
                LOGGER.warn("All PV buses should switch PQ, strongest one '{}' will stay PV", strongestPvToPqBus.bus.getId());
            }
            for (PvToPqBus pvToPqBus : pvToPqBuses) {
                // switch PV -> PQ
                switchPvPq(pvToPqBus.bus, context.getEquationSystem(), context.getVariableSet(), pvToPqBus.qLimit);
                LOGGER.trace("Switch bus {} PV -> PQ, q={} < {}Q={}", pvToPqBus.bus.getId(), pvToPqBus.q * PerUnit.SB,
                        pvToPqBus.limitDirection == ReactiveLimitDirection.MAX ? "max" : "min", pvToPqBus.qLimit * PerUnit.SB);
            }
            status = OuterLoopStatus.UNSTABLE;

            LOGGER.info("{} buses switched PV -> PQ ({} bus remains PV}", pvToPqBuses.size(), remainingPvBusCount);
        }

        return status;
    }
}
