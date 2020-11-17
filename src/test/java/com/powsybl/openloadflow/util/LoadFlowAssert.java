/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.loadflow.LoadFlowResult;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    public static void assertBetterResults(LoadFlowResult loadFlowResult, LoadFlowResult loadFlowResultBetter) {
        assertTrue(loadFlowResult.isOk(), "results should be ok");
        assertTrue(loadFlowResultBetter.isOk(), "results should be ok");
        assertEquals(loadFlowResult.getComponentResults().size(), loadFlowResultBetter.getComponentResults().size(), "results should have same subnetwork count");
        Iterator<LoadFlowResult.ComponentResult> componentResultIterator = loadFlowResult.getComponentResults().iterator();
        Iterator<LoadFlowResult.ComponentResult> componentResultIteratorBetter = loadFlowResultBetter.getComponentResults().iterator();
        // loop over sub networks
        while (componentResultIterator.hasNext()) {
            LoadFlowResult.ComponentResult componentResult = componentResultIterator.next();
            LoadFlowResult.ComponentResult componentResultBetter = componentResultIteratorBetter.next();
            assertEquals(componentResult.getComponentNum(), componentResultBetter.getComponentNum(), "this assert has a bug, please fix it");
            assertEquals(componentResult.getSlackBusId(), componentResultBetter.getSlackBusId(), "this assert has a bug, please fix it");
            assertEquals(componentResult.getStatus(), componentResultBetter.getStatus(), "status results should be the same");
            assertEquals(componentResult.getIterationCount(), componentResultBetter.getIterationCount(), "iteration count results should be the same");
            assertTrue(Math.abs(componentResult.getSlackBusActivePowerMismatch()) > Math.abs(componentResultBetter.getSlackBusActivePowerMismatch()), "it was expected that improved process returns a better result");
        }
    }
}
