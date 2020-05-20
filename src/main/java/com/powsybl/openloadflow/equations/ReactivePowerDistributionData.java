/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

/**
 * Additional data for {@link EquationType#ZERO_Q} equation.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ReactivePowerDistributionData {

    private final int firstControllerBusNum;

    private final double c;

    public ReactivePowerDistributionData(int firstControllerBusNum, double c) {
        this.firstControllerBusNum = firstControllerBusNum;
        this.c = c;
    }

    public int getFirstControllerBusNum() {
        return firstControllerBusNum;
    }

    public double getC() {
        return c;
    }
}
