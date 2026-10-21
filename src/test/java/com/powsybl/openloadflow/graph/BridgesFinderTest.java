/**
 * Copyright (c) 2020-2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class BridgesFinderTest {

    @Test
    void testFindBridges() {
        // same topology as the Eurostag tutorial network: NGEN - NHV1 = NHV2 - NLOAD, with a doubled NHV1 - NHV2 line
        BridgesFinder<Integer> graph = new BridgesFinder<>(4, v -> v);
        for (int v = 0; v < 4; v++) {
            graph.addVertex(v);
        }
        graph.addEdge(0, 1);
        graph.addEdge(1, 2);
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);

        List<int[]> bridges = graph.getBridges();

        Set<Set<Integer>> bridgeSet = bridges.stream()
                .map(b -> Set.of(b[0], b[1]))
                .collect(Collectors.toSet());
        assertEquals(Set.of(Set.of(0, 1), Set.of(2, 3)), bridgeSet);
    }
}
