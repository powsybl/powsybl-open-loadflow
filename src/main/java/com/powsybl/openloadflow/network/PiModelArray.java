/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PiModelArray implements PiModel {

    private final List<PiModel> models;

    private final int lowTapPosition;

    private int tapPosition;

    private double a1 = Double.NaN;

    private double r1 = Double.NaN;

    private double continuousR1 = Double.NaN;

    private LfBranch branch;

    private Direction r1Variation;

    public PiModelArray(List<PiModel> models, int lowTapPosition, int tapPosition) {
        this.models = Objects.requireNonNull(models);
        this.lowTapPosition = lowTapPosition;
        this.tapPosition = tapPosition;
        r1Variation = (models.get(0).getR1() > models.get(models.size() - 1).getR1()) ? Direction.DECREASE : Direction.INCREASE; // FIXME: add a control.
    }

    private PiModel getModel() {
        return models.get(tapPosition - lowTapPosition);
    }

    @Override
    public double getR() {
        return getModel().getR();
    }

    @Override
    public PiModel setR(double r) {
        return getModel().setR(r);
    }

    @Override
    public double getX() {
        return getModel().getX();
    }

    @Override
    public PiModel setX(double x) {
        return getModel().setX(x);
    }

    @Override
    public double getZ() {
        return getModel().getZ();
    }

    @Override
    public double getY() {
        return getModel().getY();
    }

    @Override
    public double getKsi() {
        return getModel().getKsi();
    }

    @Override
    public double getG1() {
        return getModel().getG1();
    }

    @Override
    public double getB1() {
        return getModel().getB1();
    }

    @Override
    public double getG2() {
        return getModel().getG2();
    }

    @Override
    public double getB2() {
        return getModel().getB2();
    }

    @Override
    public double getR1() {
        return Double.isNaN(r1) ? getModel().getR1() : r1;
    }

    @Override
    public double getContinuousR1() {
        return continuousR1;
    }

    @Override
    public double getA1() {
        return Double.isNaN(a1) ? getModel().getA1() : a1;
    }

    @Override
    public PiModelArray setA1(double a1) {
        this.a1 = a1;
        return this;
    }

    @Override
    public PiModelArray setR1(double r1) {
        this.r1 = r1;
        return this;
    }

    @Override
    public void roundA1ToClosestTap() {
        if (Double.isNaN(a1)) {
            return; // nothing to do because a1 has not been modified
        }

        // find tap position with the closest a1 value
        double smallestDistance = Math.abs(a1 - getModel().getA1());
        for (int p = 0; p < models.size(); p++) {
            double distance = Math.abs(a1 - models.get(p).getA1());
            if (distance < smallestDistance) {
                tapPosition = lowTapPosition + p;
                smallestDistance = distance;
            }
        }
        a1 = Double.NaN;
    }

    @Override
    public void roundR1ToClosestTap() {
        if (Double.isNaN(r1)) {
            return; // nothing to do because r1 has not been modified
        }

        // find tap position with the closest r1 value
        double smallestDistance = Math.abs(r1 - getModel().getR1());
        for (int p = 0; p < models.size(); p++) {
            double distance = Math.abs(r1 - models.get(p).getR1());
            if (distance < smallestDistance) {
                tapPosition = lowTapPosition + p;
                smallestDistance = distance;
            }
        }
        continuousR1 = r1;
        r1 = Double.NaN;
    }

    @Override
    public boolean updateTapPosition(Direction direction) {
        this.a1 = getA1();
        double previousA1 = Double.NaN;
        double nextA1 = Double.NaN;
        boolean hasChanged = false;
        int oldTapPosition = tapPosition;
        if (tapPosition < lowTapPosition + models.size() - 1) {
            nextA1 = models.get(tapPosition - lowTapPosition + 1).getA1(); // abs?
        }
        if (tapPosition > lowTapPosition) {
            previousA1 = models.get(tapPosition - lowTapPosition - 1).getA1(); // abs?
        }
        if (!Double.isNaN(previousA1) &&
                ((direction == Direction.INCREASE && previousA1 > a1) || (direction == Direction.DECREASE && previousA1 < a1))) {
            tapPosition = tapPosition - 1;
            a1 = Double.NaN;
            hasChanged = true;
        }
        if (!Double.isNaN(nextA1) &&
                ((direction == Direction.INCREASE && nextA1 > a1) || (direction == Direction.DECREASE && nextA1 < a1))) {
            tapPosition = tapPosition + 1;
            a1 = Double.NaN;
            hasChanged = true;
        }
        if (hasChanged) {
            for (LfNetworkListener listener : branch.getNetwork().getListeners()) {
                listener.onDiscretePhaseControlTapPositionChange(branch, oldTapPosition, tapPosition);
            }
        }
        return hasChanged;
    }

    @Override
    public boolean updateTapPositionR1(double deltaR1, int maxTapIncrement, Direction previousVariations) {
        double newR1 = getR1() + deltaR1;
        int pBegin = 0;
        int pEnd = models.size();
        int p0 = tapPosition - lowTapPosition;
        if (previousVariations == Direction.DECREASE_THEN_INCREASE) {
            // we forbid R1 to decrease again after having already decrease then increase
            if (r1Variation == Direction.INCREASE) {
                pBegin = p0;
            } else {
                pEnd = p0;
            }
        }
        if (previousVariations == Direction.INCREASE_THEN_DECREASE) {
            // we forbid R1 to increase again after having already increase then decrease
            if (r1Variation == Direction.INCREASE) {
                pEnd = p0;
            } else {
                pBegin = p0;
            }
        }

        int oldTapPosition = tapPosition;
        // find tap position with the closest r1 value without exceeding the maximum of taps to switch.
        double smallestDistance = Math.abs(deltaR1);
        for (int p = pBegin; p < pEnd; p++) {
            if (Math.abs(lowTapPosition + p - oldTapPosition) <= maxTapIncrement) {
                double distance = Math.abs(newR1 - models.get(p).getR1());
                if (distance < smallestDistance) {
                    tapPosition = lowTapPosition + p;
                    smallestDistance = distance;
                }
            }
        }

        boolean hasChanged = tapPosition != oldTapPosition;
        if (hasChanged) {
            r1 = Double.NaN;
            for (LfNetworkListener listener : branch.getNetwork().getListeners()) {
                listener.onDiscretePhaseControlTapPositionChange(branch, oldTapPosition, tapPosition);
            }
        }
        return hasChanged;
    }

    @Override
    public boolean setMinZ(double minZ, boolean dc) {
        boolean done = false;
        for (PiModel model : models) {
            done |= model.setMinZ(minZ, dc);
        }
        return done;
    }

    @Override
    public void setBranch(LfBranch branch) {
        this.branch = Objects.requireNonNull(branch);
    }
}
