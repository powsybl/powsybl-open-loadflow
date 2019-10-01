/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBranch implements LfBranch {

    private final LfBus bus1;

    private final LfBus bus2;

    private final double zb;

    private final double x;
    private final double g1;
    private final double g2;
    private final double b1;
    private final double b2;
    private final double r1;
    private final double a1;

    private final double y;
    private final double ksi;

    protected AbstractLfBranch(LfBus bus1, LfBus bus2, PiModel piModel, double nominalV1, double nominalV2) {
        this.bus1 = bus1;
        this.bus2 = bus2;
        Objects.requireNonNull(piModel);

        zb = nominalV2 * nominalV2 / PerUnit.SB;
        x = piModel.getX() / zb;
        g1 = piModel.getG1() * zb;
        g2 = piModel.getG2() * zb;
        b1 = piModel.getB1() * zb;
        b2 = piModel.getB2() * zb;
        r1 = piModel.getR1() / nominalV2 * nominalV1;
        a1 = piModel.getA1();

        double z = FastMath.hypot(piModel.getR(), piModel.getX()) / zb;
        y = 1 / z;
        ksi = FastMath.atan2(piModel.getR(), piModel.getX());
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfBus getBus2() {
        return bus2;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double ksi() {
        return ksi;
    }

    @Override
    public double g1() {
        return g1;
    }

    @Override
    public double g2() {
        return g2;
    }

    @Override
    public double b1() {
        return b1;
    }

    @Override
    public double b2() {
        return b2;
    }

    @Override
    public double r1() {
        return r1;
    }

    @Override
    public double r2() {
        return 1;
    }

    @Override
    public double a1() {
        return a1;
    }

    @Override
    public double a2() {
        return 0;
    }
}
