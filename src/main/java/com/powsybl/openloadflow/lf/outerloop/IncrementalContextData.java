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

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

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

    /**
     * Linearized prediction of the mismatches (target minus value) of all the controlled elements of an outer loop.
     * Discrete controller moves are decided one after the other, so a move is only accepted if, with the cross effects
     * of the moves already accepted in the same outer loop iteration, it reduces the mismatches outside of deadbands.
     * Otherwise, controllers controlling close elements would each correct their own mismatch and together overshoot.
     */
    public static final class MismatchPrediction<T> {

        private final List<T> elements;

        private final double[] mismatches;

        private final double[] halfDeadbands;

        private final Map<T, Integer> indexes = new HashMap<>();

        public MismatchPrediction(List<T> elements, ToDoubleFunction<T> mismatchGetter, ToDoubleFunction<T> halfDeadbandGetter) {
            this.elements = Objects.requireNonNull(elements);
            mismatches = new double[elements.size()];
            halfDeadbands = new double[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                T element = elements.get(i);
                indexes.put(element, i);
                mismatches[i] = mismatchGetter.applyAsDouble(element);
                halfDeadbands[i] = halfDeadbandGetter.applyAsDouble(element);
            }
        }

        public double getMismatch(T element) {
            return mismatches[indexes.get(element)];
        }

        private double getExcess(T element) {
            int i = indexes.get(element);
            return Math.abs(mismatches[i]) - halfDeadbands[i];
        }

        /**
         * As the first adjusted elements get the largest corrections, cross effects then reducing the corrections of
         * the others, the most deviating elements are adjusted first instead of depending on network element ordering.
         */
        public List<T> sortByDecreasingExcess(List<T> elements) {
            return elements.stream()
                    .sorted(Comparator.comparingDouble(this::getExcess).reversed())
                    .toList();
        }

        private double getObjective(double[] mismatches) {
            double objective = 0;
            for (int i = 0; i < mismatches.length; i++) {
                double excess = Math.max(0, Math.abs(mismatches[i]) - halfDeadbands[i]);
                objective += excess * excess;
            }
            return objective;
        }

        /**
         * @param valueChange predicted change of each controlled element value caused by the move
         * @return true, and predicted mismatches are updated, if the move reduces the mismatches outside of deadbands
         */
        public boolean applyIfImproved(ToDoubleFunction<T> valueChange) {
            double[] newMismatches = new double[mismatches.length];
            for (int i = 0; i < mismatches.length; i++) {
                newMismatches[i] = mismatches[i] - valueChange.applyAsDouble(elements.get(i));
            }
            if (getObjective(newMismatches) < getObjective(mismatches)) {
                System.arraycopy(newMismatches, 0, mismatches, 0, mismatches.length);
                return true;
            }
            return false;
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
