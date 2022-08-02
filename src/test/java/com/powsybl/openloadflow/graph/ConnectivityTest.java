/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class ConnectivityTest {

    @Test
    void circleTest() {
        circleTest(new NaiveGraphConnectivity<>(s -> Integer.parseInt(s) - 1));
        circleTest(new EvenShiloachGraphDecrementalConnectivity<>());
        circleTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void loopCircleTest() {
        loopCircleTest(new NaiveGraphConnectivity<>(s -> Integer.parseInt(s) - 1));
        loopCircleTest(new EvenShiloachGraphDecrementalConnectivity<>());
        loopCircleTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void saveResetTest() {
        saveResetTest(new NaiveGraphConnectivity<>(v -> v - 1));
        saveResetTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    @Test
    void exceptionsTest() {
        exceptionsTest(new NaiveGraphConnectivity<>(v -> v - 1));
        exceptionsTest(new EvenShiloachGraphDecrementalConnectivity<>());
        exceptionsTest(new MinimumSpanningTreeGraphConnectivity<>());
    }

    private void circleTest(GraphConnectivity<String, String> c) {
        String o1 = "1";
        String o2 = "2";
        String o3 = "3";
        String o4 = "4";
        String e12 = "1-2";
        String e23 = "2-3";
        String e34 = "3-4";
        String e41 = "4-1";
        c.addVertex(o1);
        c.addVertex(o2);
        c.addVertex(o3);
        c.addVertex(o4);
        c.addEdge(o1, o2, e12);
        c.addEdge(o2, o3, e23);
        c.addEdge(o3, o4, e34);
        c.addEdge(o4, o1, e41);
        c.save();
        c.removeEdge(e12);
        assertTrue(c.getSmallComponents().isEmpty());
    }

    private void loopCircleTest(GraphConnectivity<String, String> c) {
        String o1 = "1";
        String o2 = "2";
        String o3 = "3";
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        c.addVertex(o1);
        c.addVertex(o2);
        c.addVertex(o3);
        c.addEdge(o1, o1, e11);
        c.addEdge(o1, o2, e12);
        c.addEdge(o2, o3, e23);
        c.addEdge(o3, o1, e31);
        c.save();

        c.removeEdge(e11);
        assertTrue(c.getSmallComponents().isEmpty());

        c.reset();
        c.removeEdge(e12);
        assertTrue(c.getSmallComponents().isEmpty());

        c.removeEdge(e31);
        assertFalse(c.getSmallComponents().isEmpty());
    }

    private void saveResetTest(GraphConnectivity<Integer, String> c) {
        Integer v1 = 1;
        Integer v2 = 2;
        Integer v3 = 3;
        Integer v4 = 4;
        Integer v5 = 5;
        String e11 = "1-1";
        String e12 = "1-2";
        String e23 = "2-3";
        String e31 = "3-1";
        String e45 = "4-5";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addVertex(v3);
        c.addVertex(v4);
        c.addVertex(v5);
        c.addEdge(v1, v1, e11);
        c.addEdge(v1, v2, e12);
        c.addEdge(v2, v3, e23);
        c.addEdge(v3, v1, e31);
        c.addEdge(v4, v5, e45);
        c.save();

        c.removeEdge(e12);
        c.removeEdge(e31);
        assertEquals(2, c.getSmallComponents().size());
        assertEquals(Set.of(v1), c.getConnectedComponent(v1));
        assertEquals(Set.of(v2, v3), c.getConnectedComponent(v2));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));

        c.save();
        c.removeEdge(e23);
        c.addEdge(v1, v2, e12);
        c.removeEdge(e11);
        c.addEdge(v3, v4, "3-4");
        assertEquals(1, c.getSmallComponents().size());
        assertEquals(Set.of(v1, v2), c.getConnectedComponent(v1));
        assertEquals(Set.of(v3, v4, v5), c.getConnectedComponent(v5));

        c.reset();
        assertEquals(2, c.getSmallComponents().size());
        assertEquals(Set.of(v1), c.getConnectedComponent(v1));
        assertEquals(Set.of(v2, v3), c.getConnectedComponent(v2));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));

        c.addEdge(v1, v4, "1-4");
        c.addEdge(v3, v4, "3-4");
        assertTrue(c.getSmallComponents().isEmpty());

        Integer v6 = 6;
        c.addVertex(v6);
        assertFalse(c.getSmallComponents().isEmpty());
        assertEquals(Set.of(v6), c.getSmallComponents().iterator().next());

        c.reset();
        c.reset();
        assertEquals(1, c.getSmallComponents().size());
        assertEquals(Set.of(v1, v2, v3), c.getConnectedComponent(v1));
        assertEquals(Set.of(v4, v5), c.getConnectedComponent(v5));
    }

    private void exceptionsTest(GraphConnectivity<Integer, String> c) {
        Integer v1 = 1;
        Integer v2 = 2;
        String e12 = "1-2";
        String e22 = "2-2";
        c.addVertex(v1);
        c.addVertex(v2);
        c.addEdge(v1, v2, e12);
        c.addEdge(v2, v2, e22);
        c.removeEdge(e22);

        PowsyblException e1 = assertThrows(PowsyblException.class, c::getSmallComponents);
        assertEquals("Cannot compute connectivity without a saved state, please call GraphConnectivity::save at least once beforehand",
                e1.getMessage());

        PowsyblException e2 = assertThrows(PowsyblException.class, c::reset);
        assertEquals("Cannot reset, no remaining saved connectivity", e2.getMessage());

        c.save();
        PowsyblException e3 = assertThrows(PowsyblException.class, c::reset);
        assertEquals("Cannot reset, no remaining saved connectivity", e3.getMessage());
    }
}
