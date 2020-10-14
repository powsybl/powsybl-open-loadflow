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
        return getModel().getR1();
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
    public int getTapPosition() {
        return tapPosition;
    }

    @Override
    public boolean decreaseA1WithTapPositionIncrement() {
        double a1 = getA1();
        double previousA1 = Double.NaN;
        double nextA1 = Double.NaN;
        if (tapPosition < lowTapPosition + models.size() - 1) {
            nextA1 = models.get(tapPosition - lowTapPosition + 1).getA1();
        }
        if (tapPosition > lowTapPosition) {
            previousA1 = models.get(tapPosition - lowTapPosition - 1).getA1();
        }
        if (previousA1 != Double.NaN && previousA1 < a1) {
            tapPosition = tapPosition - 1;
            return true;
        }
        if (nextA1 != Double.NaN && nextA1 < a1) {
            tapPosition = tapPosition + 1;
            return true;
        }
        return false;
    }

    @Override
    public boolean increaseA1WithTapPositionIncrement() {
        double a1 = getA1();
        double previousA1 = Double.NaN;
        double nextA1 = Double.NaN;
        if (tapPosition < lowTapPosition + models.size()) {
            nextA1 = models.get(tapPosition - lowTapPosition + 1).getA1();
        }
        if (tapPosition > lowTapPosition) {
            previousA1 = models.get(tapPosition - lowTapPosition - 1).getA1();
        }
        if (previousA1 != Double.NaN && previousA1 > a1) {
            tapPosition = tapPosition - 1;
            return true;
        }
        if (nextA1 != Double.NaN && nextA1 > a1) {
            tapPosition = tapPosition + 1;
            return true;
        }
        return false;
    }
}
