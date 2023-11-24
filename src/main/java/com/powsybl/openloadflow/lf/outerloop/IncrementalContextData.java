/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.ac.outerloop.IncrementalTransformerVoltageControlOuterLoop;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class IncrementalContextData {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalContextData.class);

    public static final class ControllerContext {

        private final int maxDirectionChange;

        public ControllerContext(int maxDirectionChange) {
            this.maxDirectionChange = maxDirectionChange;
        }

        private final MutableInt directionChangeCount = new MutableInt();

        private AllowedDirection allowedDirection = AllowedDirection.BOTH;

        public AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        public void updateAllowedDirection(Direction direction) {
            if (directionChangeCount.getValue() <= maxDirectionChange) {
                if (allowedDirection != direction.getAllowedDirection()) {
                    // both vs increase or decrease
                    // increase vs decrease
                    // decrease vs increase
                    directionChangeCount.increment();
                }
                allowedDirection = direction.getAllowedDirection();
            }
        }
    }

    private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

    private final List<LfBus> candiateControlledBuses;

    public Map<String, ControllerContext> getControllersContexts() {
        return controllersContexts;
    }

    public List<LfBus> getCandidateControlledBuses() {
        return candiateControlledBuses;
    }

    public IncrementalContextData(LfNetwork network, VoltageControl.Type type) {
        candiateControlledBuses = network.getBuses().stream()
                .filter(bus -> bus.isVoltageControlled(type))
                .collect(Collectors.toList());
    }

    public IncrementalContextData() {
        candiateControlledBuses = Collections.emptyList();
    }

    public static List<LfBus> getControlledBuses(List<LfBus> candidateControlledBuses, VoltageControl.Type type) {
        return candidateControlledBuses.stream()
                .filter(bus -> bus.getVoltageControl(type).orElseThrow().getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .filter(bus -> !bus.getVoltageControl(type).orElseThrow().isDisabled())
                .collect(Collectors.toList());
    }

    public static <E extends LfElement> List<E> getControllerElements(List<LfBus> candidateControlledBuses, VoltageControl.Type type) {
        return getControlledBuses(candidateControlledBuses, type).stream()
                .flatMap(bus -> bus.getVoltageControl(type).orElseThrow().getMergedControllerElements().stream())
                .filter(Predicate.not(LfElement::isDisabled))
                .map(element -> (E) element)
                .collect(Collectors.toList());
    }

    public static List<LfBus> getControlledBusesOutOfDeadband(IncrementalContextData contextData, VoltageControl.Type type) {
        return IncrementalContextData.getControlledBuses(contextData.getCandidateControlledBuses(), type).stream()
                .filter(bus -> isOutOfDeadband((TransformerVoltageControl) bus.getVoltageControl(type).orElseThrow()))
                .collect(Collectors.toList());
    }

    private static boolean isOutOfDeadband(TransformerVoltageControl voltageControl) {
        double diffV = getDiffV(voltageControl);
        double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
        boolean outOfDeadband = Math.abs(diffV) > halfTargetDeadband;
        if (outOfDeadband) {
            List<LfBranch> controllers = voltageControl.getMergedControllerElements().stream()
                    .filter(b -> !b.isDisabled())
                    .collect(Collectors.toList());
            LOGGER.trace("Controlled bus '{}' ({} controllers) is outside of its deadband (half is {} kV) and could need a voltage adjustment of {} kV",
                    voltageControl.getControlledBus().getId(), controllers.size(), halfTargetDeadband * voltageControl.getControlledBus().getNominalV(),
                    diffV * voltageControl.getControlledBus().getNominalV());
        }
        return outOfDeadband;
    }

    public static double getDiffV(TransformerVoltageControl voltageControl) {
        double targetV = voltageControl.getTargetValue();
        double v = voltageControl.getControlledBus().getV();
        return targetV - v;
    }

    // TODO fix min deadband
    protected static double getHalfTargetDeadband(TransformerVoltageControl voltageControl) {
        return voltageControl.getTargetDeadband().orElse(0.1 / voltageControl.getControlledBus().getNominalV()) / 2;
    }
}
