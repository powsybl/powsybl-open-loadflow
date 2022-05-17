/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class ConnectivityTest {

    @Test
    public void circleTest() {
        GraphDecrementalConnectivity<String, String> dc = new EvenShiloachGraphDecrementalConnectivity<>();
        String o1 = "1";
        String o2 = "2";
        String o3 = "3";
        String o4 = "4";
        String e12 = "1-2";
        String e23 = "2-3";
        String e34 = "3-4";
        String e41 = "4-1";
        dc.addVertex(o1);
        dc.addVertex(o2);
        dc.addVertex(o3);
        dc.addVertex(o4);
        dc.addEdge(o1, o2, e12);
        dc.addEdge(o2, o3, e23);
        dc.addEdge(o3, o4, e34);
        dc.addEdge(o4, o1, e41);
        dc.cut(e12);
        assertTrue(dc.getSmallComponents().isEmpty());
    }
}
