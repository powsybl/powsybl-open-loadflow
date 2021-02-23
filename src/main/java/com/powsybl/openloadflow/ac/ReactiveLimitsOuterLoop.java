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
import com.powsybl.openloadflow.network.VoltageControl;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    private static final double Q_EPS = Math.pow(10, -5); // 10^-5 in p.u => 10^-3 in Mw

    private static final Comparator<PvToPqBus> BY_NOMINAL_V_COMPARATOR = Comparator.comparingDouble(pvToPqBus -> {
        LfBus bus = pvToPqBus.bus;
        LfBus controlledBus = bus.hasVoltageControllerCapability() ? bus.getVoltageControl().getControlledBus() : null;
        return controlledBus == null ? -bus.getNominalV() : -controlledBus.getNominalV();
    });

    private static final Comparator<PvToPqBus> BY_TARGET_P_COMPARATOR = Comparator.comparingDouble(pvToPqBus -> -pvToPqBus.bus.getTargetP());

    private static final Comparator<PvToPqBus> BY_ID_COMPARATOR = Comparator.comparing(pvToPqBus -> pvToPqBus.bus.getId());

    private static final int MAX_SWITCH_PQ_PV = 2;

    @Override
    public String getType() {
        return "Reactive limits";
    }

    public static void switchPvPq(LfBus bus, EquationSystem equationSystem, VariableSet variableSet, double newGenerationTargetQ) {
        bus.setGenerationTargetQ(newGenerationTargetQ);
        bus.setVoltageControlEnabled(false);

        Equation qEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
        qEq.setActive(true);

        if (bus.hasVoltageControllerCapability()) {
            updateControlledBus(bus.getVoltageControl(), equationSystem, variableSet);
        } else {
            Equation vEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V);
            vEq.setActive(false);
        }
    }

    public static void updateControlledBus(VoltageControl voltageControl, EquationSystem equationSystem, VariableSet variableSet) {

        LfBus controlledBus = voltageControl.getControlledBus();
        Set<LfBus> controllerBuses = voltageControl.getControllerBuses();

        // clean reactive power distribution equations
        controllerBuses.forEach(b -> equationSystem.removeEquation(b.getNum(), EquationType.ZERO_Q));

        // controlled bus has a voltage equation only if one of the controller bus has voltage control on
        List<LfBus> controllerBusesWithVoltageControlOn = controllerBuses.stream()
                .filter(LfBus::isVoltageController)
                .collect(Collectors.toList());
        equationSystem.createEquation(controlledBus.getNum(), EquationType.BUS_V).setActive(!controllerBusesWithVoltageControlOn.isEmpty());

        // create reactive power equations on controller buses that have voltage control on
        if (!controllerBusesWithVoltageControlOn.isEmpty()) {
            AcEquationSystem.createReactivePowerDistributionEquations(equationSystem, variableSet, controllerBusesWithVoltageControlOn);
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

    private boolean switchPvPq(List<PvToPqBus> pvToPqBuses, EquationSystem equationSystem, VariableSet variableSet, int remainingPvBusCount) {
        boolean done = false;

        int modifiedRemainingPvBusCount = remainingPvBusCount;
        if (modifiedRemainingPvBusCount == 0) {
            // keep one bus PV, the strongest one which is one at the highest nominal level and highest active power
            // target
            PvToPqBus strongestPvToPqBus = pvToPqBuses.stream()
                    .min(BY_NOMINAL_V_COMPARATOR
                            .thenComparing(BY_TARGET_P_COMPARATOR)
                            .thenComparing(BY_ID_COMPARATOR)) // for stability of the sort
                    .orElseThrow(IllegalStateException::new);
            pvToPqBuses.remove(strongestPvToPqBus);
            modifiedRemainingPvBusCount++;
            LOGGER.warn("All PV buses should switch PQ, strongest one '{}' will stay PV", strongestPvToPqBus.bus.getId());
        }

        if (!pvToPqBuses.isEmpty()) {
            done = true;

            for (PvToPqBus pvToPqBus : pvToPqBuses) {
                // switch PV -> PQ
                switchPvPq(pvToPqBus.bus, equationSystem, variableSet, pvToPqBus.qLimit);

                if (LOGGER.isTraceEnabled()) {
                    if (pvToPqBus.limitDirection == ReactiveLimitDirection.MAX) {
                        LOGGER.trace("Switch bus '{}' PV -> PQ, q={} > maxQ={}", pvToPqBus.bus.getId(), pvToPqBus.q * PerUnit.SB,
                                pvToPqBus.qLimit * PerUnit.SB);
                    } else {
                        LOGGER.trace("Switch bus '{}' PV -> PQ, q={} < minQ={}", pvToPqBus.bus.getId(), pvToPqBus.q * PerUnit.SB,
                                pvToPqBus.qLimit * PerUnit.SB);
                    }
                }
            }
        }

        LOGGER.info("{} buses switched PV -> PQ ({} bus remains PV}", pvToPqBuses.size(), modifiedRemainingPvBusCount);

        return done;
    }

    public static void switchPqPv(LfBus bus, EquationSystem equationSystem, VariableSet variableSet) {
        bus.setVoltageControlEnabled(true);
        bus.setGenerationTargetQ(0);

        Equation qEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
        qEq.setActive(false);

        if (bus.hasVoltageControllerCapability()) {
            updateControlledBus(bus.getVoltageControl(), equationSystem, variableSet);
        } else {
            Equation vEq = equationSystem.createEquation(bus.getNum(), EquationType.BUS_V);
            vEq.setActive(true);
        }
    }

    private static final class PqToPvBus {

        private final LfBus bus;

        private final ReactiveLimitDirection limitDirection;

        private PqToPvBus(LfBus bus, ReactiveLimitDirection limitDirection) {
            this.bus = bus;
            this.limitDirection = limitDirection;
        }
    }

    private boolean switchPqPv(List<PqToPvBus> pqToPvBuses, EquationSystem equationSystem, VariableSet variableSet) {
        int pqPvSwitchCount = 0;

        for (PqToPvBus pqToPvBus : pqToPvBuses) {
            LfBus bus = pqToPvBus.bus;

            if (bus.getVoltageControlSwitchOffCount() >= MAX_SWITCH_PQ_PV) {
                LOGGER.trace("Bus '{}' blocked PQ as it has reach its max number of PQ -> PV switch ({})",
                        bus.getId(), bus.getVoltageControlSwitchOffCount());
            } else {
                switchPqPv(bus, equationSystem, variableSet);
                pqPvSwitchCount++;

                if (LOGGER.isTraceEnabled()) {
                    if (pqToPvBus.limitDirection == ReactiveLimitDirection.MAX) {
                        LOGGER.trace("Switch bus '{}' PQ -> PV, q=maxQ and v={} > targetV={}", bus.getId(), bus.getV(),
                            bus.getVoltageControl().getTargetValue());
                    } else {
                        LOGGER.trace("Switch bus '{}' PQ -> PV, q=minQ and v={} < targetV={}", bus.getId(), bus.getV(),
                            bus.getVoltageControl().getTargetValue());
                    }
                }
            }
        }

        LOGGER.info("{} buses switched PQ -> PV ({} buses blocked PQ because have reach max number of switch)",
                pqPvSwitchCount, pqToPvBuses.size() - pqPvSwitchCount);

        return pqPvSwitchCount > 0;
    }

    /**
     * A bus PV bus can be switched to PQ in 2 cases:
     *  - if Q equals to Qmax
     *  - if Q equals to Qmin
     */
    private void checkPvBus(LfBus bus, List<PvToPqBus> pvToPqBuses, MutableInt remainingPvBusCount) {
        double minQ = bus.getMinQ();
        double maxQ = bus.getMaxQ();
        double q = bus.getCalculatedQ() + bus.getLoadTargetQ();
        if (q < minQ) {
            pvToPqBuses.add(new PvToPqBus(bus, q, minQ, ReactiveLimitDirection.MIN));
        } else if (q > maxQ) {
            pvToPqBuses.add(new PvToPqBus(bus, q, maxQ, ReactiveLimitDirection.MAX));
        } else {
            remainingPvBusCount.increment();
        }
    }

    /**
     * A PQ bus can be switched to PV in 2 cases:
     *  - if Q is equal to Qmin and V is less than targetV: it means that the PQ bus can be unlocked in order to increase the reactive power and reach its targetV.
     *  - if Q is equal to Qmax and V is greater than targetV: it means that the PQ bus can be unlocked in order to decrease the reactive power and reach its target V.
     */
    private void checkPqBus(LfBus bus, List<PqToPvBus> pqToPvBuses) {
        double minQ = bus.getMinQ();
        double maxQ = bus.getMaxQ();
        double q = bus.getGenerationTargetQ();
        if (Math.abs(q - maxQ) < Q_EPS && bus.getV() > bus.getVoltageControl().getTargetValue()) { // bus produce too much reactive power
            pqToPvBuses.add(new PqToPvBus(bus, ReactiveLimitDirection.MAX));
        }
        if (Math.abs(q - minQ) < Q_EPS && bus.getV() < bus.getVoltageControl().getTargetValue()) { // bus absorb too much reactive power
            pqToPvBuses.add(new PqToPvBus(bus, ReactiveLimitDirection.MIN));
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<PvToPqBus> pvToPqBuses = new ArrayList<>();
        List<PqToPvBus> pqToPvBuses = new ArrayList<>();
        MutableInt remainingPvBusCount = new MutableInt();
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.isVoltageController()) {
                checkPvBus(bus, pvToPqBuses, remainingPvBusCount);
            } else if (bus.hasVoltageControllerCapability()) {
                checkPqBus(bus, pqToPvBuses);
            }
        }

        if (!pvToPqBuses.isEmpty() && switchPvPq(pvToPqBuses, context.getEquationSystem(), context.getVariableSet(), remainingPvBusCount.intValue())) {
            status = OuterLoopStatus.UNSTABLE;
        }
        if (!pqToPvBuses.isEmpty() && switchPqPv(pqToPvBuses, context.getEquationSystem(), context.getVariableSet())) {
            status = OuterLoopStatus.UNSTABLE;
        }

        return status;
    }
}
