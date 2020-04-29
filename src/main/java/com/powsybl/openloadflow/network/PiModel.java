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

    double getR();

    double getX();

    double getZ();

    double getKsi();

    double getG1();

    double getB1();

    double getG2();

    double getB2();

    double getR1();

    double getR2();

    double getA1();

    PiModel setA1(double a1);

    double getA2();

    PiModel setA2(double a2);

    void roundA1ToClosestTap();
}
