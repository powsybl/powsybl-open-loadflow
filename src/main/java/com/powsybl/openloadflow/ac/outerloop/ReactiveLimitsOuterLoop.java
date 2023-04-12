/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.OuterLoop;
import com.powsybl.openloadflow.ac.OuterLoopContext;
import com.powsybl.openloadflow.ac.OuterLoopStatus;
import com.powsybl.openloadflow.network.GeneratorVoltageControl;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    private static final Comparator<PvToPqBus> BY_NOMINAL_V_COMPARATOR = Comparator.comparingDouble(
        pvToPqBus -> pvToPqBus.controllerBus.getGeneratorVoltageControl()
            .map(vc -> -vc.getControlledBus().getNominalV())
            .orElse(-pvToPqBus.controllerBus.getNominalV()));

    private static final Comparator<PvToPqBus> BY_TARGET_P_COMPARATOR = Comparator.comparingDouble(pvToPqBus -> -pvToPqBus.controllerBus.getTargetP());

    private static final Comparator<PvToPqBus> BY_ID_COMPARATOR = Comparator.comparing(pvToPqBus -> pvToPqBus.controllerBus.getId());

    public static final int MAX_SWITCH_PQ_PV = 3;

    private final int maxPqPvSwitch;

    public ReactiveLimitsOuterLoop(int maxPqPvSwitch) {
        this.maxPqPvSwitch = maxPqPvSwitch;
    }

    private static final class ContextData {

        private final Map<String, MutableInt> pvPqSwitchCount = new HashMap<>();

        void incrementPvPqSwitchCount(String busId) {
            pvPqSwitchCount.computeIfAbsent(busId, k -> new MutableInt(0))
                    .increment();
        }

        int getPvPqSwitchCount(String busId) {
            MutableInt counter = pvPqSwitchCount.get(busId);
            if (counter == null) {
                return 0;
            }
            return counter.getValue();
        }
    }

    @Override
    public String getType() {
        return "Reactive limits";
    }

    private enum ReactiveLimitDirection {
        MIN,
        MAX
    }

    private static final class PvToPqBus {

        private final LfBus controllerBus;

        private final double q;

        private final double qLimit;

        private final ReactiveLimitDirection limitDirection;

        private PvToPqBus(LfBus controllerBus, double q, double qLimit, ReactiveLimitDirection limitDirection) {
            this.controllerBus = controllerBus;
            this.q = q;
            this.qLimit = qLimit;
            this.limitDirection = limitDirection;
        }
    }

    private boolean switchPvPq(List<PvToPqBus> pvToPqBuses, int remainingPvBusCount, ContextData contextData,
                               Reporter reporter) {
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
            LOGGER.warn("All PV buses should switch PQ, strongest one '{}' will stay PV", strongestPvToPqBus.controllerBus.getId());
        }

        if (!pvToPqBuses.isEmpty()) {
            done = true;

            for (PvToPqBus pvToPqBus : pvToPqBuses) {
                LfBus controllerBus = pvToPqBus.controllerBus;

                // switch PV -> PQ
                controllerBus.setGenerationTargetQ(pvToPqBus.qLimit);
                controllerBus.setGeneratorVoltageControlEnabled(false);
                // increment PV -> PQ switch counter
                contextData.incrementPvPqSwitchCount(controllerBus.getId());

                if (LOGGER.isTraceEnabled()) {
                    if (pvToPqBus.limitDirection == ReactiveLimitDirection.MAX) {
                        LOGGER.trace("Switch bus '{}' PV -> PQ, q={} > maxQ={}", controllerBus.getId(), pvToPqBus.q * PerUnit.SB,
                                pvToPqBus.qLimit * PerUnit.SB);
                    } else {
                        LOGGER.trace("Switch bus '{}' PV -> PQ, q={} < minQ={}", controllerBus.getId(), pvToPqBus.q * PerUnit.SB,
                                pvToPqBus.qLimit * PerUnit.SB);
                    }
                }
            }
        }

        Reports.reportPvToPqBuses(reporter, pvToPqBuses.size(), modifiedRemainingPvBusCount);

        LOGGER.info("{} buses switched PV -> PQ ({} bus remains PV)", pvToPqBuses.size(), modifiedRemainingPvBusCount);

        return done;
    }

    private static final class PqToPvBus {

        private final LfBus controllerBus;

        private final ReactiveLimitDirection limitDirection;

        private PqToPvBus(LfBus controllerBus, ReactiveLimitDirection limitDirection) {
            this.controllerBus = controllerBus;
            this.limitDirection = limitDirection;
        }
    }

    @Override
    public void initialize(OuterLoopContext context) {
        context.setData(new ContextData());
    }

    private static boolean switchPqPv(List<PqToPvBus> pqToPvBuses, ContextData contextData, Reporter reporter, int maxPqPvSwitch) {
        int pqPvSwitchCount = 0;

        for (PqToPvBus pqToPvBus : pqToPvBuses) {
            LfBus controllerBus = pqToPvBus.controllerBus;

            int pvPqSwitchCount = contextData.getPvPqSwitchCount(controllerBus.getId());
            if (pvPqSwitchCount >= maxPqPvSwitch) {
                LOGGER.trace("Bus '{}' blocked PQ as it has reach its max number of PQ -> PV switch ({})",
                        controllerBus.getId(), pvPqSwitchCount);
            } else {
                controllerBus.setGeneratorVoltageControlEnabled(true);
                controllerBus.setGenerationTargetQ(0);
                pqPvSwitchCount++;

                if (LOGGER.isTraceEnabled()) {
                    if (pqToPvBus.limitDirection == ReactiveLimitDirection.MAX) {
                        LOGGER.trace("Switch bus '{}' PQ -> PV, q=maxQ and v={} > targetV={}", controllerBus.getId(), controllerBus.getV(), getBusTargetV(controllerBus));
                    } else {
                        LOGGER.trace("Switch bus '{}' PQ -> PV, q=minQ and v={} < targetV={}", controllerBus.getId(), controllerBus.getV(), getBusTargetV(controllerBus));
                    }
                }
            }
        }

        Reports.reportPqToPvBuses(reporter, pqPvSwitchCount, pqToPvBuses.size() - pqPvSwitchCount);

        LOGGER.info("{} buses switched PQ -> PV ({} buses blocked PQ because have reach max number of switch)",
                pqPvSwitchCount, pqToPvBuses.size() - pqPvSwitchCount);

        return pqPvSwitchCount > 0;
    }

    /**
     * A bus PV bus can be switched to PQ in 2 cases:
     *  - if Q equals to Qmax
     *  - if Q equals to Qmin
     */
    private static void checkPvBus(LfBus controllerBus, List<PvToPqBus> pvToPqBuses, MutableInt remainingPvBusCount) {
        double minQ = controllerBus.getMinQ();
        double maxQ = controllerBus.getMaxQ();
        double q = controllerBus.getQ().eval() + controllerBus.getLoadTargetQ();
        if (q < minQ) {
            pvToPqBuses.add(new PvToPqBus(controllerBus, q, minQ, ReactiveLimitDirection.MIN));
        } else if (q > maxQ) {
            pvToPqBuses.add(new PvToPqBus(controllerBus, q, maxQ, ReactiveLimitDirection.MAX));
        } else {
            remainingPvBusCount.increment();
        }
    }

    /**
     * A PQ bus can be switched to PV in 2 cases:
     *  - if Q is equal to Qmin and V is less than targetV: it means that the PQ bus can be unlocked in order to increase the reactive power and reach its targetV.
     *  - if Q is equal to Qmax and V is greater than targetV: it means that the PQ bus can be unlocked in order to decrease the reactive power and reach its targetV.
     */
    private static void checkPqBus(LfBus controllerCapableBus, List<PqToPvBus> pqToPvBuses) {
        double minQ = controllerCapableBus.getMinQ();
        double maxQ = controllerCapableBus.getMaxQ();
        double q = controllerCapableBus.getGenerationTargetQ();
        double distanceToMaxQ = Math.abs(q - maxQ);
        double distanceToMinQ = Math.abs(q - minQ);
        if (distanceToMaxQ < distanceToMinQ && getBusV(controllerCapableBus) > getBusTargetV(controllerCapableBus)) { // bus produce too much reactive power
            pqToPvBuses.add(new PqToPvBus(controllerCapableBus, ReactiveLimitDirection.MAX));
        }
        if (distanceToMaxQ > distanceToMinQ && getBusV(controllerCapableBus) < getBusTargetV(controllerCapableBus)) { // bus absorb too much reactive power
            pqToPvBuses.add(new PqToPvBus(controllerCapableBus, ReactiveLimitDirection.MIN));
        }
    }

    private static double getBusTargetV(LfBus bus) {
        return bus.getGeneratorVoltageControl().map(GeneratorVoltageControl::getTargetValue).orElse(Double.NaN);
    }

    private static double getBusV(LfBus bus) {
        return bus.getGeneratorVoltageControl().map(vc -> vc.getControlledBus().getV()).orElse(Double.NaN);
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<PvToPqBus> pvToPqBuses = new ArrayList<>();
        List<PqToPvBus> pqToPvBuses = new ArrayList<>();
        MutableInt remainingPvBusCount = new MutableInt();
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.isGeneratorVoltageControlEnabled() && !bus.isDisabled()) {
                checkPvBus(bus, pvToPqBuses, remainingPvBusCount);
            } else if (bus.hasGeneratorVoltageControllerCapability() && !bus.isDisabled()) {
                if (!bus.hasGeneratorsWithSlope()) {
                    checkPqBus(bus, pqToPvBuses);
                } else {
                    // we don't support switching PQ to PV for bus with one controller with slope.
                    LOGGER.warn("Controller bus '{}' wants to control back voltage with slope: not supported", bus.getId());
                }
            }
        }

        var contextData = (ContextData) context.getData();

        if (!pvToPqBuses.isEmpty() && switchPvPq(pvToPqBuses, remainingPvBusCount.intValue(), contextData, reporter)) {
            status = OuterLoopStatus.UNSTABLE;
        }
        if (!pqToPvBuses.isEmpty() && switchPqPv(pqToPvBuses, contextData, reporter, maxPqPvSwitch)) {
            status = OuterLoopStatus.UNSTABLE;
        }

        return status;
    }
}
