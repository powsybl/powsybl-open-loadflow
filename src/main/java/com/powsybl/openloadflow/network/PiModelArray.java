/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.apache.commons.lang3.Range;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PiModelArray implements PiModel {

    private final List<PiModel> models;

    private final int lowTapPosition;

    private int tapPositionIndex;

    private double a1 = Double.NaN;

    private double r1 = Double.NaN;

    private double continuousR1 = Double.NaN;

    private LfBranch branch;

    public PiModelArray(List<PiModel> models, int lowTapPosition, int tapPosition) {
        this.models = Objects.requireNonNull(models);
        this.lowTapPosition = lowTapPosition;
        tapPositionIndex = tapPosition - lowTapPosition;
    }

    private PiModel getModel() {
        return models.get(tapPositionIndex);
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

    private int findClosestTapPosition(double targetValue, ToDoubleFunction<PiModel> valueGetter,
                                       Range<Integer> positionIndexRange, int maxTapShift) {
        int closestTapPositionIndex = tapPositionIndex;
        double smallestDistance = Math.abs(targetValue - valueGetter.applyAsDouble(models.get(tapPositionIndex)));
        for (int i = positionIndexRange.getMinimum(); i <= positionIndexRange.getMaximum(); i++) {
            if (Math.abs(i - tapPositionIndex) > maxTapShift) { // we are not allowed to go further than maxTapShift positions
                continue;
            }
            double distance = Math.abs(targetValue - valueGetter.applyAsDouble(models.get(i)));
            if (distance < smallestDistance) {
                closestTapPositionIndex = i;
                smallestDistance = distance;
            }
        }
        return closestTapPositionIndex;
    }

    private Optional<Direction> updateTapPositionToReachTargetValue(double targetValue, ToDoubleFunction<PiModel> valueGetter,
                                                                    Range<Integer> positionIndexRange, int maxTapShift) {
        int oldPositionIndex = tapPositionIndex;

        // find tap position with the closest value without exceeding the maximum of taps to switch.
        tapPositionIndex = findClosestTapPosition(targetValue, valueGetter, positionIndexRange, maxTapShift);

        if (tapPositionIndex != oldPositionIndex) {
            for (LfNetworkListener listener : branch.getNetwork().getListeners()) {
                listener.onTapPositionChange(branch, lowTapPosition + oldPositionIndex, lowTapPosition + tapPositionIndex);
            }

            return Optional.of(tapPositionIndex - oldPositionIndex > 0 ? Direction.INCREASE : Direction.DECREASE);
        }

        return Optional.empty();
    }

    private Range<Integer> getAllowedPositionIndexRange(AllowedDirection allowedDirection) {
        switch (allowedDirection) {
            case INCREASE:
                return Range.between(tapPositionIndex, models.size() - 1);
            case DECREASE:
                return Range.between(0, tapPositionIndex);
            case BOTH:
                return Range.between(0, models.size() - 1);
            default:
                throw new IllegalStateException("Unknown direction: " + allowedDirection);
        }
    }

    @Override
    public void roundA1ToClosestTap() {
        if (Double.isNaN(a1)) {
            return; // nothing to do because a1 has not been modified
        }

        // find tap position with the closest a1 value
        updateTapPositionToReachTargetValue(a1, PiModel::getA1, getAllowedPositionIndexRange(AllowedDirection.BOTH), Integer.MAX_VALUE);
        a1 = Double.NaN;
    }

    @Override
    public void roundR1ToClosestTap() {
        if (Double.isNaN(r1)) {
            return; // nothing to do because r1 has not been modified
        }

        // find tap position with the closest r1 value
        updateTapPositionToReachTargetValue(r1, PiModel::getR1, getAllowedPositionIndexRange(AllowedDirection.BOTH), Integer.MAX_VALUE);
        continuousR1 = r1;
        r1 = Double.NaN;
    }

    @Override
    public boolean shiftOneTapPositionToChangeA1(Direction direction) {
        // an increase direction means that A1 should increase.
        // a decrease direction means that A1 should decrease.
        double currentA1 = getA1();
        int oldTapPositionIndex = tapPositionIndex;

        if (tapPositionIndex < models.size() - 1) {
            double nextA1 = models.get(tapPositionIndex + 1).getA1(); // abs?
            if ((direction == Direction.INCREASE && nextA1 > currentA1)
                    || (direction == Direction.DECREASE && nextA1 < currentA1)) {
                tapPositionIndex++;
            }
        }

        if (tapPositionIndex > 0) {
            double previousA1 = models.get(tapPositionIndex - 1).getA1(); // abs?
            if ((direction == Direction.INCREASE && previousA1 > currentA1)
                    || (direction == Direction.DECREASE && previousA1 < currentA1)) {
                tapPositionIndex--;
            }
        }

        if (tapPositionIndex != oldTapPositionIndex) {
            a1 = Double.NaN;
            for (LfNetworkListener listener : branch.getNetwork().getListeners()) {
                listener.onTapPositionChange(branch, lowTapPosition + oldTapPositionIndex, lowTapPosition + tapPositionIndex);
            }
            return true;
        }

        return false;
    }

    @Override
    public Optional<Direction> updateTapPositionToReachNewR1(double deltaR1, int maxTapShift, AllowedDirection allowedDirection) {
        double newR1 = getR1() + deltaR1;
        Range<Integer> positionIndexRange = getAllowedPositionIndexRange(allowedDirection);
        Optional<Direction> direction = updateTapPositionToReachTargetValue(newR1, PiModel::getR1, positionIndexRange, maxTapShift);
        if (direction.isPresent()) {
            r1 = Double.NaN;
        }
        return direction;
    }

    @Override
    public Optional<Direction> updateTapPositionToReachNewA1(double deltaA1, int maxTapShift, AllowedDirection allowedDirection) {
        double newA1 = getA1() + deltaA1;
        Range<Integer> positionIndexRange = getAllowedPositionIndexRange(allowedDirection);
        Optional<Direction> direction = updateTapPositionToReachTargetValue(newA1, PiModel::getA1, positionIndexRange, maxTapShift);
        if (direction.isPresent()) {
            a1 = Double.NaN;
        }
        return direction;
    }

    private Optional<Direction> probeDirectionToShiftValue(double delta, ToDoubleFunction<PiModel> valueGetter) {
        if (delta == 0) {
            throw new IllegalArgumentException("Delta should not be zero");
        }
        double value = valueGetter.applyAsDouble(models.get(tapPositionIndex));
        if (tapPositionIndex > 0
                && Math.signum(delta) == Math.signum(valueGetter.applyAsDouble(models.get(tapPositionIndex - 1)) - value)) {
            return Optional.of(Direction.DECREASE);
        }
        if (tapPositionIndex < models.size() - 1
                && Math.signum(delta) == Math.signum(valueGetter.applyAsDouble(models.get(tapPositionIndex + 1)) - value)) {
            return Optional.of(Direction.INCREASE);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Direction> probeDirectionToShiftA1(double deltaA1) {
        return probeDirectionToShiftValue(deltaA1, PiModel::getA1);
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

    @Override
    public int getTapPosition() {
        return lowTapPosition + tapPositionIndex;
    }

    @Override
    public PiModel setTapPosition(int tapPosition) {
        Range<Integer> tapPositionRange = getTapPositionRange();
        if (!tapPositionRange.contains(tapPosition)) {
            throw new IllegalArgumentException("Tap position " + tapPosition + " out of range " + tapPositionRange);
        }
        if (lowTapPosition + tapPosition != tapPositionIndex) {
            int oldTapPositionIndex = tapPositionIndex;
            tapPositionIndex = tapPosition - lowTapPosition;
            r1 = Double.NaN;
            continuousR1 = Double.NaN;
            a1 = Double.NaN;
            for (LfNetworkListener listener : branch.getNetwork().getListeners()) {
                listener.onTapPositionChange(branch, lowTapPosition + oldTapPositionIndex, tapPosition);
            }
        }
        return this;
    }

    @Override
    public Range<Integer> getTapPositionRange() {
        return Range.between(lowTapPosition, lowTapPosition + models.size() - 1);
    }
}
