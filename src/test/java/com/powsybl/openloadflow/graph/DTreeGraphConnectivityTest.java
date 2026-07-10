/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class DTreeGraphConnectivityTest {

    @Test
    void testInsertNonTreeEdge() {
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

    @Test
    void testMakeRootUpdateGreatParent() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
        connectivity.addVertex(0);
        connectivity.addVertex(1);
        connectivity.addVertex(2);
        connectivity.addVertex(3);
        connectivity.addVertex(4);
        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(1, 2, "1-2");
        connectivity.addEdge(2, 3, "2-3");
        connectivity.addEdge(0, 4, "0-4");
        connectivity.addEdge(3, 4, "3-4");
        // 0 -- 1 -- 2 -- 3
        // |------4-------|
        // In the spanning tree, the root is 1
        // 4 <-- 0 <-- 1 --> 2 --> 3

        connectivity.startTemporaryChanges();
        assertEquals(6, connectivity.computeSd());
        for (int i = 0; i < 5; i++) {
            assertEquals(0, connectivity.getComponentNumber(i));
        }

        connectivity.removeEdge("0-1");
        // the root is now 3, it involves getting the great parent of 1 (which is 3)
        // 1 <-- 2 <-- 3 --> 4 --> 0
        assertEquals(6, connectivity.computeSd());
        for (int i = 0; i < 5; i++) {
            final int index = i;
            assertEquals(0, connectivity.getComponentNumber(index));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                // if the great parent is updated incorrectly, an infinite loop may appear here
                assertEquals(Set.of(0, 1, 2, 3, 4), connectivity.getConnectedComponent(index));
            });
        }
    }
}
