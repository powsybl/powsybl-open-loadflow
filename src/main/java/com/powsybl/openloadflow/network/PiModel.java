/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import net.jafama.FastMath;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PiModel {

    private double r = 0;
    private double x = 0;
    private double g1 = 0;
    private double b1 = 0;
    private double g2 = 0;
    private double b2 = 0;
    private double r1 = 1;
    private double a1 = 0;

    public double getR() {
        return r;
    }

    public PiModel setR(double r) {
        this.r = r;
        return this;
    }

    public double getX() {
        return x;
    }

    public PiModel setX(double x) {
        this.x = x;
        return this;
    }

    public double getZ() {
        return FastMath.hypot(r, x);
    }

    public double getKsi() {
        return FastMath.atan2(r, x);
    }

    public double getG1() {
        return g1;
    }

    public PiModel setG1(double g1) {
        this.g1 = g1;
        return this;
    }

    public double getB1() {
        return b1;
    }

    public PiModel setB1(double b1) {
        this.b1 = b1;
        return this;
    }

    public double getG2() {
        return g2;
    }

    public PiModel setG2(double g2) {
        this.g2 = g2;
        return this;
    }

    public double getB2() {
        return b2;
    }

    public PiModel setB2(double b2) {
        this.b2 = b2;
        return this;
    }

    public double getR1() {
        return r1;
    }

    public PiModel setR1(double r1) {
        this.r1 = r1;
        return this;
    }

    public double getR2() {
        return 1;
    }

    public double getA1() {
        return a1;
    }

    public PiModel setA1(double a1) {
        this.a1 = a1;
        return this;
    }

    public double getA2() {
        return 0;
    }

    public PiModel setA2(double a2) {
        return this;
    }
}
