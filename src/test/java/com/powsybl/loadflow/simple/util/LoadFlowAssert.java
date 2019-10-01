/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LoadFlowAssert {

    public static final double DELTA_ANGLE = 1E-6d;
    public static final double DELTA_V = 1E-3d;
    public static final double DELTA_POWER = 1E-3d;

    private LoadFlowAssert() {
    }

    public static void assertVoltageEquals(double v, Bus bus) {
        assertEquals(v, bus.getV(), DELTA_V);
    }

    public static void assertAngleEquals(double a, Bus bus) {
        assertEquals(a, bus.getAngle(), DELTA_ANGLE);
    }

    public static void assertActivePowerEquals(double p, Terminal terminal) {
        assertEquals(p, terminal.getP(), DELTA_POWER);
    }

    public static void assertReactivePowerEquals(double q, Terminal terminal) {
        assertEquals(q, terminal.getQ(), DELTA_POWER);
    }

    public static void assertUndefinedActivePower(Terminal terminal) {
        assertTrue(Double.isNaN(terminal.getP()));
    }

    public static void assertUndefinedReactivePower(Terminal terminal) {
        assertTrue(Double.isNaN(terminal.getQ()));
    }
}
