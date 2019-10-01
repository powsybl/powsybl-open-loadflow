/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBus implements LfBus {

    private final int num;

    private boolean slack = false;

    protected double v;

    protected double angle;

    protected double q = Double.NaN;

    protected AbstractLfBus(int num, double v, double angle) {
        this.num = num;
        this.v = v;
        this.angle = angle;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public boolean isSlack() {
        return slack;
    }

    @Override
    public void setSlack(boolean slack) {
        this.slack = slack;
    }

    @Override
    public double getTargetP() {
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        return getGenerationTargetQ() - getLoadTargetQ();
    }

    @Override
    public double getV() {
        return v;
    }

    @Override
    public void setV(double v) {
        this.v = v;
    }

    @Override
    public double getAngle() {
        return angle;
    }

    @Override
    public void setAngle(double angle) {
        this.angle = angle;
    }

    @Override
    public double getQ() {
        return q / PerUnit.SB;
    }

    @Override
    public void setQ(double q) {
        this.q = q * PerUnit.SB;
    }
}
