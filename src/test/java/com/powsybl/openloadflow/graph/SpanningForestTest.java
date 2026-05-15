/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpanningForestTest {

    @Test
    void testConstructor() {
        SpanningForest<Integer, DefaultEdge> spanningForest = new SpanningForest<>();
        assertEquals(0, spanningForest.treeCount());
        // TODO: check other accessors
    }

    @Test
    void testAddVertex() {
        SpanningForest<Integer, DefaultEdge> spanningForest = new SpanningForest<>();

        for (int i = 0; i < 50; i++) {
            assertFalse(spanningForest.contains(i));
            assertTrue(spanningForest.addVertex(i));
            assertTrue(spanningForest.contains(i));
            assertFalse(spanningForest.addVertex(i));

            assertEquals(1, spanningForest.componentSize(i));
            assertEquals(i + 1, spanningForest.treeCount());
        }
    }

    @Test
    void testConnected() {
        SpanningForest<Integer, Integer> spanningForest = new SpanningForest<>();

        spanningForest.addEdge(0, 1, 0);
        assertTrue(spanningForest.connected(0, 1));
        spanningForest.addEdge(1, 2, 1);
        assertTrue(spanningForest.connected(0, 1));
        assertTrue(spanningForest.connected(0, 2));
        assertTrue(spanningForest.connected(1, 2));

        spanningForest.removeEdge(0, 1, 0);
        assertFalse(spanningForest.connected(0, 1));
        assertTrue(spanningForest.connected(1, 2));
    }
}
