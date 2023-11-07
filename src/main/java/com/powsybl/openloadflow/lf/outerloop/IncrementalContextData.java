/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;

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
                .filter(bus -> !bus.getVoltageControl(type).orElseThrow().isHidden(true))
                .collect(Collectors.toList());
    }

    public static <E extends LfElement> List<E> getControllerElements(List<LfBus> candidateControlledBuses, VoltageControl.Type type) {
        return candidateControlledBuses.stream()
                .filter(bus -> bus.getVoltageControl(type).orElseThrow().getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .filter(bus -> !bus.getVoltageControl(type).orElseThrow().isHidden(true))
                .flatMap(bus -> bus.getVoltageControl(type).orElseThrow().getMergedControllerElements().stream())
                .filter(Predicate.not(LfElement::isDisabled))
                .map(element -> (E) element)
                .collect(Collectors.toList());
    }
}
