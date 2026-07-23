/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class EvenShiloachGetConnnectedComponent {

    @Test
    void test() {
        EvenShiloachGraphDecrementalConnectivity<Integer, String> conn = new EvenShiloachGraphDecrementalConnectivity<>();
        for (int i = 0; i < 8; i++) {
            conn.addVertex(i);
        }

        conn.addEdge(0, 1, "0-1");
        conn.addEdge(1, 2, "1-2");
        conn.addEdge(2, 3, "2-3");
        conn.addEdge(3, 4, "3-4");

        conn.addEdge(4, 5, "4-5");
        conn.addEdge(5, 6, "5-6");
        conn.addEdge(6, 7, "6-7");

        conn.setMainComponentVertex(0);

        conn.startTemporaryChanges();
        conn.removeEdge("2-3");
        Set<Integer> set = conn.getConnectedComponent(2);
        System.out.println(set);
        set.clear();
        conn.removeEdge("1-2");
        System.out.println(set);
        System.out.println(conn.getConnectedComponent(1));
        System.out.println(conn.getLargestConnectedComponent());
    }
}
