/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.apache.commons.math3.util.Pair;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedMap;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PhaseControl {

    public enum Mode {
        CONTROLLER,
        LIMITER,
        OFF
    }

    public enum Unit {
        MW,
        A
    }

    public enum ControlledSide {
        ONE,
        TWO
    }

    private Mode mode;

    private final ControlledSide controlledSide;

    private final double targetValue;

    private final Unit unit;

    private final SortedMap<Integer, Double> a1ByTap;

    public PhaseControl(Mode mode, ControlledSide controlledSide, double targetValue, Unit unit,
                        SortedMap<Integer, Double> a1ByTap) {
        this.mode = Objects.requireNonNull(mode);
        this.controlledSide = Objects.requireNonNull(controlledSide);
        this.targetValue = targetValue;
        this.unit = Objects.requireNonNull(unit);
        this.a1ByTap = Objects.requireNonNull(a1ByTap);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    public ControlledSide getControlledSide() {
        return controlledSide;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public Unit getUnit() {
        return unit;
    }

    public double findClosestA1(double a1) {
        // find tap position with the closest a1 value
        int position = a1ByTap.entrySet().stream()
                .map(e -> Pair.create(e.getKey(), Math.abs(a1 - e.getValue())))
                .min(Comparator.comparingDouble(Pair::getSecond))
                .map(Pair::getFirst)
                .orElseThrow(IllegalStateException::new);
        return a1ByTap.get(position);
    }
}
