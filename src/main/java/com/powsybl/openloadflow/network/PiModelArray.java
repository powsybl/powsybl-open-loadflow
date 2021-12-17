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

    private LfBranch branch;

    public PiModelArray(List<PiModel> models, int lowTapPosition, int tapPosition) {
        this.models = Objects.requireNonNull(models);
        this.lowTapPosition = lowTapPosition;
        this.tapPosition = tapPosition;
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
            return; // nothing to do because a1 has not been modified
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
        r1 = Double.NaN;
    }

    @Override
    public boolean updateTapPosition(Direction direction) {
        this.a1 = getA1();
        double previousA1 = Double.NaN;
        double nextA1 = Double.NaN;
        boolean hasChange = false;
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
            hasChange = true;
        }
        if (!Double.isNaN(nextA1) &&
                ((direction == Direction.INCREASE && nextA1 > a1) || (direction == Direction.DECREASE && nextA1 < a1))) {
            tapPosition = tapPosition + 1;
            a1 = Double.NaN;
            hasChange = true;
        }
        if (hasChange) {
            for (LfNetworkListener listener : branch.getNetwork().getListeners()) {
                listener.onDiscretePhaseControlTapPositionChange(branch, oldTapPosition, tapPosition);
            }
        }
        return hasChange;
    }

    @Override
    public boolean setMinZ(double minZ) {
        boolean done = false;
        for (PiModel model : models) {
            done |= model.setMinZ(minZ);
        }
        return done;
    }

    @Override
    public void setBranch(LfBranch branch) {
        this.branch = Objects.requireNonNull(branch);
    }
}
