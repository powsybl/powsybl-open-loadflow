/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfStaticVarCompensator;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class MonitoringVoltageOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringVoltageOuterLoop.class);

    @Override
    public String getType() {
        return "Voltage monitoring";
    }

    private enum VoltageLimitDirection {
        MIN,
        MAX
    }

    private static final class PqToPvBus {

        private final LfBus controllerBus;

        private final VoltageLimitDirection voltageLimitDirection;

        private PqToPvBus(LfBus controllerBus, VoltageLimitDirection voltageLimitDirection) {
            this.controllerBus = controllerBus;
            this.voltageLimitDirection = voltageLimitDirection;
        }
    }

    private static void switchPqPv(List<PqToPvBus> pqToPvBuses, Reporter reporter) {
        for (PqToPvBus pqToPvBus : pqToPvBuses) {
            LfBus controllerBus = pqToPvBus.controllerBus;

            controllerBus.setVoltageControlEnabled(true);
            controllerBus.setGenerationTargetQ(0);
            double newTargetV;
            if (pqToPvBus.voltageLimitDirection == VoltageLimitDirection.MAX
                    || pqToPvBus.voltageLimitDirection == VoltageLimitDirection.MIN) {
                newTargetV = getSvcTargetV(controllerBus, pqToPvBus.voltageLimitDirection);
                controllerBus.getVoltageControl().ifPresent(vc -> vc.setTargetValue(newTargetV));
                Reports.reportStandByAutomatonActivation(reporter, controllerBus.getId(), newTargetV);
                if (LOGGER.isTraceEnabled()) {
                    if (pqToPvBus.voltageLimitDirection == VoltageLimitDirection.MAX) {
                        LOGGER.trace("Switch bus '{}' PQ -> PV with high targetV={}", controllerBus.getId(), newTargetV);
                    } else {
                        LOGGER.trace("Switch bus '{}' PQ -> PV with low targetV={}", controllerBus.getId(), newTargetV);
                    }
                }
            }
        }
    }

    private static double getBusV(LfBus bus) {
        return bus.getVoltageControl().map(vc -> vc.getControlledBus().getV()).orElse(Double.NaN);
    }

    /**
     * A PQ bus with a static var compensator monitoring voltage can be switched to PV in 2 cases:
     *  - if the voltage of the controlled bus is higher than the high voltage threshold.
     *  - if the voltage of the controlled bus is lower that the low voltage threshold.
     */
    private static void checkPqBusForVoltageLimits(LfBus controllerCapableBus, List<PqToPvBus> pqToPvBuses, Range<Double> voltageLimit) {
        double busV = getBusV(controllerCapableBus);
        if (busV > voltageLimit.getMaximum()) {
            pqToPvBuses.add(new PqToPvBus(controllerCapableBus, VoltageLimitDirection.MAX));
        }
        if (busV < voltageLimit.getMinimum()) {
            pqToPvBuses.add(new PqToPvBus(controllerCapableBus, VoltageLimitDirection.MIN));
        }
    }

    private static Optional<LfStaticVarCompensator> findMonitoringSvc(LfBus bus) {
        return bus.getGenerators().stream()
                .filter(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.MONITORING_VOLTAGE)
                .findFirst()
                .map(LfStaticVarCompensator.class::cast);
    }

    private static Optional<Range<Double>> getControlledBusVoltageLimits(LfBus controllerCapableBus) {
        return findMonitoringSvc(controllerCapableBus)
                .flatMap(generator -> generator.getStandByAutomaton()
                        .map(automaton -> Range.between(automaton.getLowVoltageThreshold(), automaton.getHighVoltageThreshold())));
    }

    private static double getSvcTargetV(LfBus bus, VoltageLimitDirection direction) {
        return findMonitoringSvc(bus).flatMap(svc -> svc.getStandByAutomaton()
            .map(automaton -> {
                svc.setGeneratorControlType(LfGenerator.GeneratorControlType.VOLTAGE); // FIXME
                if (direction == VoltageLimitDirection.MIN) {
                    return automaton.getLowTargetV();
                } else {
                    return automaton.getHighTargetV();
                }
            })
        )
        .orElse(Double.NaN);
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<PqToPvBus> pqToPvBuses = new ArrayList<>();
        for (LfBus bus : context.getNetwork().getBuses()) {
            if (bus.hasVoltageControllerCapability() && !bus.isDisabled()) {
                getControlledBusVoltageLimits(bus).ifPresent(voltageLimits -> checkPqBusForVoltageLimits(bus, pqToPvBuses, voltageLimits));
            }
        }

        if (!pqToPvBuses.isEmpty()) {
            switchPqPv(pqToPvBuses, reporter);
            status = OuterLoopStatus.UNSTABLE;
        }

        return status;
    }
}
