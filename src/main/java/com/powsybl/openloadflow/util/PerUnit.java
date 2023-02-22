/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class PerUnit {

    public static final double SB = 100d;
    public static final double SQRT_3 = Math.sqrt(3);
    public static final double BASE_CURRENT_FACTOR = (1000d * SB) / SQRT_3;

    /**
     * Base current value for a given nominal voltage.
     */
    public static double ib(double nominalV) {
        return BASE_CURRENT_FACTOR / nominalV;
    }

    public static double zb(double nominalV) {
        return nominalV * nominalV / SB;
    }

    private PerUnit() {
    }
}
