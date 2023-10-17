/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactiveLimitsOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveLimitsOuterLoop.class);

    public static final String NAME = "ReactiveLimits";

    private static final Comparator<PvToPqBus> BY_NOMINAL_V_COMPARATOR = Comparator.comparingDouble(
        pvToPqBus -> pvToPqBus.controllerBus.getGeneratorVoltageControl()
            .map(vc -> -vc.getControlledBus().getNominalV())
            .orElse(-pvToPqBus.controllerBus.getNominalV()));

    private static final Comparator<PvToPqBus> BY_TARGET_P_COMPARATOR = Comparator.comparingDouble(pvToPqBus -> -pvToPqBus.controllerBus.getTargetP());

    private static final Comparator<PvToPqBus> BY_ID_COMPARATOR = Comparator.comparing(pvToPqBus -> pvToPqBus.controllerBus.getId());

    public static final int MAX_SWITCH_PQ_PV_DEFAULT_VALUE = 3;

    private final int maxPqPvSwitch;
    private final double maxReactivePowerMismatch;

    public ReactiveLimitsOuterLoop(int maxPqPvSwitch, double maxReactivePowerMismatch) {
        this.maxPqPvSwitch = maxPqPvSwitch;
        this.maxReactivePowerMismatch = maxReactivePowerMismatch;
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
    public String getName() {
        return NAME;
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
                controllerBus.setQLimitType(pvToPqBus.limitDirection.equals(ReactiveLimitDirection.MIN) ? LfBus.QLimitType.MIN_Q : LfBus.QLimitType.MAX_Q);
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

    private static final class PqRemoteControlToPqBus {

        private final LfBus controllerBus;

        private final double q;

        private final double qLimit;

        private final ReactiveLimitDirection limitDirection;

        private PqRemoteControlToPqBus(LfBus controllerBus, double q, double qLimit, ReactiveLimitDirection limitDirection) {
            this.controllerBus = controllerBus;
            this.q = q;
            this.qLimit = qLimit;
            this.limitDirection = limitDirection;
        }
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
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
                controllerBus.setQLimitType(null);
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
     * A PQ bus can have its Qmin or Qmax limit updated after a change in targetP of the generator or a change of the voltage magnitude of the bus.
     */
    private static void checkPqBus(LfBus controllerCapableBus, List<PqToPvBus> pqToPvBuses, List<LfBus> busesWithUpdatedQLimits,
                                   double maxReactivePowerMismatch, boolean canSwitchPqToPv) {
        double minQ = controllerCapableBus.getMinQ(); // the actual minQ.
        double maxQ = controllerCapableBus.getMaxQ(); // the actual maxQ.
        double q = controllerCapableBus.getGenerationTargetQ();
        controllerCapableBus.getQLimitType().ifPresent(qLimitType -> {
            if (qLimitType == LfBus.QLimitType.MIN_Q) {
                if (getBusV(controllerCapableBus) < getBusTargetV(controllerCapableBus) && canSwitchPqToPv) {
                    // bus absorb too much reactive power
                    pqToPvBuses.add(new PqToPvBus(controllerCapableBus, ReactiveLimitDirection.MIN));
                } else if (Math.abs(minQ - q) > maxReactivePowerMismatch) {
                    LOGGER.trace("PQ bus {} with updated Q limits, previous minQ {} new minQ {}", controllerCapableBus.getId(), q, minQ);
                    controllerCapableBus.setGenerationTargetQ(minQ);
                    busesWithUpdatedQLimits.add(controllerCapableBus);
                }
            } else if (qLimitType == LfBus.QLimitType.MAX_Q) {
                if (getBusV(controllerCapableBus) > getBusTargetV(controllerCapableBus) && canSwitchPqToPv) {
                    // bus produce too much reactive power
                    pqToPvBuses.add(new PqToPvBus(controllerCapableBus, ReactiveLimitDirection.MAX));
                } else if (Math.abs(maxQ - q) > maxReactivePowerMismatch) {
                    LOGGER.trace("PQ bus {} with updated Q limits, previous maxQ {} new maxQ {}", controllerCapableBus.getId(), q, maxQ);
                    controllerCapableBus.setGenerationTargetQ(maxQ);
                    busesWithUpdatedQLimits.add(controllerCapableBus);
                }
            }
        });
    }

    /**
     * A PQ remote reactive control bus can switch to PQ in 2 cases:
     *  - if Q < Qmin
     *  - if Q > Qmax
     *  In any case, the bus will get stuck in PQ with Q = limit (Qmin or Qmax)
     */
    private static void checkRemotePqBus(LfBus controllerCapableBus, List<PqRemoteControlToPqBus> pqRemoteControlToPqBuses) {
        double minQ = controllerCapableBus.getMinQ(); // the actual minQ.
        double maxQ = controllerCapableBus.getMaxQ(); // the actual maxQ.
        double q = controllerCapableBus.getQ().eval();
        if (q < minQ) {
            // q < minQ : bus absorbs too much reactive power
            pqRemoteControlToPqBuses.add(new PqRemoteControlToPqBus(controllerCapableBus, q, minQ, ReactiveLimitDirection.MIN));
        } else if (q > maxQ) {
            // q > maxQ : bus produces too much reactive power
            pqRemoteControlToPqBuses.add(new PqRemoteControlToPqBus(controllerCapableBus, q, maxQ, ReactiveLimitDirection.MAX));
        }
    }

    private static void switchPqRemoteControlPq(List<PqRemoteControlToPqBus> pqRemoteControlToPqBuses, Reporter reporter) {
        int pqRemoteControlPqSwitchCount = 0;

        for (PqRemoteControlToPqBus pqRemoteControlToPqBus : pqRemoteControlToPqBuses) {
            LfBus controllerBus = pqRemoteControlToPqBus.controllerBus;

            controllerBus.setReactivePowerControlEnabled(false);
            controllerBus.setGenerationTargetQ(pqRemoteControlToPqBus.qLimit);
            pqRemoteControlPqSwitchCount++;

            if (LOGGER.isTraceEnabled()) {
                if (pqRemoteControlToPqBus.limitDirection == ReactiveLimitDirection.MAX) {
                    LOGGER.trace("Switch bus '{}' PQ (remote control) -> PQ, q={} > maxQ={}", controllerBus.getId(), pqRemoteControlToPqBus.q * PerUnit.SB,
                            pqRemoteControlToPqBus.qLimit * PerUnit.SB);
                } else {
                    LOGGER.trace("Switch bus '{}' PQ (remote control) -> PQ, q={} < minQ={}", controllerBus.getId(), pqRemoteControlToPqBus.q * PerUnit.SB,
                            pqRemoteControlToPqBus.qLimit * PerUnit.SB);
                }
            }
        }

        Reports.reportPqRemoteControlToPqBuses(reporter, pqRemoteControlPqSwitchCount);

        LOGGER.info("{} buses switched PQ (remote control) -> PQ", pqRemoteControlPqSwitchCount);
    }

    private static double getBusTargetV(LfBus bus) {
        return bus.getGeneratorVoltageControl().map(GeneratorVoltageControl::getTargetValue).orElse(Double.NaN);
    }

    private static double getBusV(LfBus bus) {
        return bus.getGeneratorVoltageControl().map(vc -> vc.getControlledBus().getV()).orElse(Double.NaN);
    }

    public List<LfBus> getReactivePowerControllerElements(LfNetwork network) {
        return network.getBuses().stream()
                .filter(LfBus::hasReactivePowerControl)
                .flatMap(bus -> Stream.of(bus.getReactivePowerControl().orElseThrow().getControllerBus()))
                .filter(Predicate.not(LfBus::isDisabled))
                .collect(Collectors.toList());
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<PvToPqBus> pvToPqBuses = new ArrayList<>();
        List<PqToPvBus> pqToPvBuses = new ArrayList<>();
        List<LfBus> busesWithUpdatedQLimits = new ArrayList<>();
        MutableInt remainingPvBusCount = new MutableInt();

        List<PqRemoteControlToPqBus> pqRemoteControlToPqBuses = new ArrayList<>();

        context.getNetwork().<LfBus>getControllerElements(VoltageControl.Type.GENERATOR).forEach(bus -> {
            if (bus.isGeneratorVoltageControlEnabled()) {
                checkPvBus(bus, pvToPqBuses, remainingPvBusCount);
            } else {
                // we don't support switching PQ to PV for bus with one controller with slope.
                checkPqBus(bus, pqToPvBuses, busesWithUpdatedQLimits, maxReactivePowerMismatch, !bus.hasGeneratorsWithSlope());
            }
        });

        getReactivePowerControllerElements(context.getNetwork()).forEach(bus -> {
            if (bus.isReactivePowerControlEnabled()) {
                // a bus that has a reactive generator power control, PQ (remoteTargetQ), can't switch PV
                // if its reactive limits are not respected, it will change PQ (targetQ = controller's limit)
                checkRemotePqBus(bus, pqRemoteControlToPqBuses);
            }
        });

        var contextData = (ContextData) context.getData();

        if (!pvToPqBuses.isEmpty() && switchPvPq(pvToPqBuses, remainingPvBusCount.intValue(), contextData, reporter)) {
            status = OuterLoopStatus.UNSTABLE;
        }
        if (!pqToPvBuses.isEmpty() && switchPqPv(pqToPvBuses, contextData, reporter, maxPqPvSwitch)) {
            status = OuterLoopStatus.UNSTABLE;
        }
        if (!busesWithUpdatedQLimits.isEmpty()) {
            LOGGER.info("{} buses blocked to a reactive limit have been adjusted because reactive limit has changed",
                    busesWithUpdatedQLimits.size());

            status = OuterLoopStatus.UNSTABLE;
        }

        if (!pqRemoteControlToPqBuses.isEmpty()) {
            switchPqRemoteControlPq(pqRemoteControlToPqBuses, reporter);
            status = OuterLoopStatus.UNSTABLE;
        }

        return status;
    }
}
