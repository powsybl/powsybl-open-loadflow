/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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

        private Direction currentDirection;

        public AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        private boolean insensitive = false;

        public void updateAllowedDirection(Direction direction) {
            if (directionChangeCount.intValue() < maxDirectionChange) {
                if (currentDirection != null && currentDirection != direction) {
                    directionChangeCount.increment();
                }
                currentDirection = direction;
            } else {
                allowedDirection = direction.getAllowedDirection();
            }
        }

        public void setInsensitive() {
            insensitive = true;
        }

        private void resetInsensitive() {
            insensitive = false;
        }

        public boolean isInsensitive() {
            return insensitive;
        }
    }

    private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

    private final List<LfBus> candidateControlledBuses;

    private int lastOuterLoopTotalIterations = 0;

    public Map<String, ControllerContext> getControllersContexts() {
        return controllersContexts;
    }

    public List<LfBus> getCandidateControlledBuses() {
        return candidateControlledBuses;
    }

    public IncrementalContextData(LfNetwork network, VoltageControl.Type type) {
        candidateControlledBuses = network.getBuses().stream()
                .filter(bus -> bus.isVoltageControlled(type))
                .toList();
    }

    public IncrementalContextData() {
        candidateControlledBuses = Collections.emptyList();
    }

    public static List<LfBus> getControlledBuses(List<LfBus> candidateControlledBuses, VoltageControl.Type type) {
        return candidateControlledBuses.stream()
                .filter(bus -> bus.getVoltageControl(type).orElseThrow().getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                .filter(bus -> !bus.getVoltageControl(type).orElseThrow().isDisabled())
                .toList();
    }

    public static <E extends LfElement> List<E> getControllerElements(List<LfBus> candidateControlledBuses, VoltageControl.Type type) {
        return getControlledBuses(candidateControlledBuses, type).stream()
                .flatMap(bus -> bus.getVoltageControl(type).orElseThrow().getMergedControllerElements().stream())
                .filter(Predicate.not(LfElement::isDisabled))
                .map(element -> (E) element)
                .toList();
    }

    public void check(int outerLoopTotalIterations) {
        if (outerLoopTotalIterations > lastOuterLoopTotalIterations + 1) {
            // another outer loop executed before our last run, reset insensitive status
            controllersContexts.values().forEach(ControllerContext::resetInsensitive);
        }
        lastOuterLoopTotalIterations = outerLoopTotalIterations;
    }
}
