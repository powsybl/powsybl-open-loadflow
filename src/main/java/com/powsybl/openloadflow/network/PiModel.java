/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface PiModel {

    double A2 = 0;

    double R2 = 1;

    double getR();

    PiModel setR(double r);

    double getX();

    PiModel setX(double x);

    double getZ();

    double getKsi();

    double getG1();

    double getB1();

    double getG2();

    double getB2();

    double getR1();

    PiModel setR1(double r1);

    double getA1();

    PiModel setA1(double a1);

    void roundA1ToClosestTap();
}
