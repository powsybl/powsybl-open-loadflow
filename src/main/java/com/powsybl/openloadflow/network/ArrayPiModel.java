/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ArrayPiModel implements PiModel<ArrayPiModel> {

    private final List<PiModel> models = new ArrayList<>();

    private int tapPosition = 0;

    public void addModel(PiModel model) {
        models.add(Objects.requireNonNull(model));
    }

    @Override
    public double getR() {
        return models.get(tapPosition).getR();
    }

    @Override
    public double getX() {
        return models.get(tapPosition).getX();
    }

    @Override
    public double getZ() {
        return models.get(tapPosition).getZ();
    }

    @Override
    public double getKsi() {
        return models.get(tapPosition).getKsi();
    }

    @Override
    public double getG1() {
        return models.get(tapPosition).getG1();
    }

    @Override
    public double getB1() {
        return models.get(tapPosition).getB1();
    }

    @Override
    public double getG2() {
        return models.get(tapPosition).getG2();
    }

    @Override
    public double getB2() {
        return models.get(tapPosition).getB2();
    }

    @Override
    public double getR1() {
        return models.get(tapPosition).getR1();
    }

    @Override
    public double getR2() {
        return models.get(tapPosition).getR2();
    }

    @Override
    public double getA1() {
        return models.get(tapPosition).getA1();
    }

    @Override
    public ArrayPiModel setA1(double a1) {
        models.get(tapPosition).setA1(a1);
        return this;
    }

    @Override
    public double getA2() {
        return models.get(tapPosition).getA2();
    }

    @Override
    public ArrayPiModel setA2(double a2) {
        models.get(tapPosition).setA2(a2);
        return this;
    }

    public void roundA1ToClosestTap() {
        // TODO
    }
}
