/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.collect.testing.SafeTreeMap;
import org.apache.commons.lang3.stream.Streams;
import org.jgrapht.Graph;
import org.jgrapht.generate.GraphGenerator;
import org.jgrapht.generate.HyperCubeGraphGenerator;
import org.jgrapht.generate.RandomRegularGraphGenerator;
import org.jgrapht.generate.ScaleFreeGraphGenerator;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void testGetConnectedComponentUsesFindRoot() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
        for (int i = 0; i < 3; i++) {
            connectivity.addVertex(i);
        }
        connectivity.addEdge(0, 1, "0-1");
        connectivity.addEdge(1, 2, "1-2");
        connectivity.startTemporaryChanges();
        // 0 -- 1 -- 2
        //      ^ root

        Set<Integer> set = connectivity.getConnectedComponent(0);
        assertEquals(3, set.size());
        assertTrue(set.contains(2));
        assertEquals(Set.of(0, 1, 2), Sets.newHashSet(set));
    }

    /**
     * The goal of this test is to build a connected component such that its root
     * isn't a centroid and there is a centroid at distance 2 from the root. For example
     * in this tree:
     * <pre>
     *     0 -> 1 -> 2 -> 3 -> 4
     * </pre>
     * 0 is the root but the centroid is 2.
     * And then, the test will run a connectivity query (using {@link DTreeGraphConnectivity#getConnectedComponent(Object)}
     * and then {@link Set#contains(Object)}) between 3 and 4. DTree will compare the root
     * of the tree containing 3, and same for 4.
     * First, DTree will get the root of 3, which 0 and reroot the tree to 1.
     * Then, DTree wil get the root of 4, which is now 1, and reroot the tree to 2.
     * But 1 is different from 2, so DTree will return false even if 3 and 4 are in the same tree.
     * Therefore, DTree mustn't use findRootOptReroot the second time.
     */
    @Test
    void testGetConnectComponentContainsDoesntUseFindRootOptRerootOnTheRightSide() {
        DTreeGraphConnectivity<Integer, String> connectivity = new DTreeGraphConnectivity<>();
        for (int i = 0; i < 8; i++) {
            connectivity.addVertex(i);
        }

        // The graph and sequence of remove was found by generating a graph
        // and removing all edges until a centroid at distance 2 from a root
        // is found. If there is none, a new graph (bigger) is generated.
        connectivity.addEdge(7, 7, "7-7");
        connectivity.addEdge(0, 0, "0-0");
        connectivity.addEdge(6, 4, "6-4");
        connectivity.addEdge(0, 3, "0-3");
        connectivity.addEdge(7, 3, "7-3");
        connectivity.addEdge(4, 5, "4-5");
        connectivity.addEdge(1, 4, "1-4");
        connectivity.addEdge(2, 7, "2-7");
        connectivity.addEdge(3, 6, "3-6");
        connectivity.addEdge(2, 3, "2-3");
        connectivity.addEdge(2, 6, "2-6");
        connectivity.addEdge(5, 1, "5-1");
        connectivity.addEdge(4, 2, "4-2");
        connectivity.addEdge(1, 1, "1-1");
        connectivity.addEdge(6, 5, "6-5");
        connectivity.addEdge(0, 5, "0-5");
        //   _         _
        //  | |       | |
        //   7 --- 3 - 0 - 5
        //   |   ╱ |     ╱ | ╲
        //   |  ╱  |   ╱   |   1
        //   | ╱   | ╱     | ╱|_|
        //   2 --- 6 ----- 4
        // Spanning tree:
        // 3 - 7
        //   - 2
        //   - 0
        //   - 6 - 5
        //       - 4 - 1

        connectivity.removeEdge("2-6");
        connectivity.removeEdge("4-2");
        connectivity.removeEdge("3-6");
        connectivity.removeEdge("0-3");

        // New spanning trees:
        // 2 - 3 - 7
        //     ^ root
        // 0 - 5 - 6 - 4 - 1
        // ^ root

        connectivity.startTemporaryChanges();
        Set<Integer> comp = connectivity.getConnectedComponent(1);
        // First, contains will call findRootOptReroot on 4.
        // That will change the root from 0 to 5, and return 5.
        // Then, findRootOptReroot will be called on 1.
        // That will change the root from 5 to 6, and return 6.
        // Finally, contains will compare the two value returned and return false.
        assertTrue(comp.contains(4));
    }
}
