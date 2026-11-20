/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.graph.dtree.DTreeGraphConnectivityFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class GraphConnectivityFactoryTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFactoriesSupportingIncremental")
    void testAddEdgeWhenFactorySupportsIncremental(GraphConnectivityFactory<Integer, String> factory) {
        assertTrue(factory.supportsIncremental());

        GraphConnectivity<Integer, String> c = factory.create();
        c.addVertex(1);
        c.addVertex(2);
        c.addVertex(3);
        c.addEdge(1, 2, "1-2");
        c.addEdge(2, 3, "2-3");
        c.startTemporaryChanges();

        assertDoesNotThrow(() -> c.addEdge(1, 3, "1-3"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFactoriesNotSupportingIncremental")
    void testAddEdgeWhenFactoryDoesNotSupportIncremental(GraphConnectivityFactory<Integer, String> factory) {
        assertFalse(factory.supportsIncremental());

        GraphConnectivity<Integer, String> c = factory.create();
        c.addVertex(1);
        c.addVertex(2);
        c.addVertex(3);
        c.addEdge(1, 2, "1-2");
        c.addEdge(2, 3, "2-3");
        c.startTemporaryChanges();

        assertThrows(PowsyblException.class, () -> c.addEdge(1, 3, "1-3"));
    }

    private static Stream<Arguments> provideFactoriesSupportingIncremental() {
        return Stream.of(
                Arguments.of(new NaiveGraphConnectivityFactory<Integer, String>(v -> v - 1)),
                Arguments.of(new DTreeGraphConnectivityFactory<>()));
    }

    private static Stream<Arguments> provideFactoriesNotSupportingIncremental() {
        return Stream.of(
                Arguments.of(new EvenShiloachGraphDecrementalConnectivityFactory<>()));
    }
}
