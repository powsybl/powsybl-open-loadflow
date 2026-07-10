/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeGraphConnectivityTest {

    @Test
    void testDTreeInsertNonTreeEdge() {
        //      0 -- 1
        //      |    |
        // 3 -- 2 -- 5
        //      |
        //      4

        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
        for (int i = 0; i < 6; i++) {
            connectivity.addVertex(i);
        }

        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(2, 0, "2-0");
        connectivity.addEdge(2, 3, "2-3");
        connectivity.addEdge(2, 4, "2-4");
        connectivity.addEdge(1, 5, "1-5");

        connectivity.startTemporaryChanges();
        for (int i = 0; i < 6; i++) {
            assertEquals(0, connectivity.getComponentNumber(i));
        }
        assertEquals(8, connectivity.computeSd());

        // Adding this edge doesn't affect connectivity.
        // However, it modifies the spanning tree.
        // Before:
        //      0 -- 1
        //      |    |
        // 3 -- 2    5  (5 and 2 not connected)
        //      |
        //      4
        connectivity.addEdge(5, 2, "5-2");
        // After:
        //      0    1   (0 and 1 are still connected, but not in the spanning tree!)
        //      |    |
        // 3 -- 1 -- 5
        //      |
        //      4
        for (int i = 0; i < 6; i++) {
            assertEquals(0, connectivity.getComponentNumber(i));
        }
        assertEquals(6, connectivity.computeSd());
    }
}
