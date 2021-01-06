/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.graph;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Gaël Macherel <gael.macherel at artelys.com>
 */
class ConnectivityTest {

    @Test
    void testConnectivity() {
        testConnectivity(new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        testConnectivity(new EvenShiloachGraphDecrementalConnectivity<>());
    }

    private void testConnectivity(GraphDecrementalConnectivity<LfBus> connectivity) {
        Network network = new ConnectedFactory().createThreeCcLinkedByASingleBus();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new FirstSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        for (LfBus lfBus : lfNetwork.getBuses()) {
            connectivity.addVertex(lfBus);
        }

        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2());
        }

        List<LfBranch> branchesToCut = Arrays.asList(lfNetwork.getBranchById("l34"), lfNetwork.getBranchById("l48"));
        branchesToCut.forEach(lfBranch -> connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2()));

        assertEquals(1, connectivity.getComponentNumber(lfNetwork.getBusById("b3_vl_0")));
        assertEquals(0, connectivity.getComponentNumber(lfNetwork.getBusById("b4_vl_0")));
        assertEquals(2, connectivity.getComponentNumber(lfNetwork.getBusById("b8_vl_0")));
        assertEquals(2, connectivity.getSmallComponents().size());
    }

    public static class ConnectedFactory extends AbstractLoadFlowNetworkFactory {
        public Network createThreeCcLinkedByASingleBus() {
            Network network = Network.create("test", "code");
            Bus b1 = createBus(network, "b1");
            Bus b2 = createBus(network, "b2");
            Bus b3 = createBus(network, "b3");
            Bus b4 = createBus(network, "b4");
            Bus b5 = createBus(network, "b5");
            Bus b6 = createBus(network, "b6");
            Bus b7 = createBus(network, "b7");
            Bus b8 = createBus(network, "b8");
            Bus b9 = createBus(network, "b9");
            Bus b10 = createBus(network, "b10");
            createLine(network, b1, b2, "l12", 0.1f);
            createLine(network, b1, b3, "l13", 0.1f);
            createLine(network, b2, b3, "l23", 0.1f);
            createLine(network, b3, b4, "l34", 0.1f);
            createLine(network, b4, b5, "l45", 0.1f);
            createLine(network, b5, b6, "l56", 0.1f);
            createLine(network, b5, b7, "l57", 0.1f);
            createLine(network, b6, b7, "l67", 0.1f);
            createLine(network, b4, b8, "l48", 0.1f);
            createLine(network, b8, b9, "l89", 0.1f);
            createLine(network, b8, b10, "l810", 0.1f);
            createLine(network, b9, b10, "l910", 0.1f);

            createGenerator(b2, "g2", 3);
            createGenerator(b6, "g6", 2);
            createGenerator(b10, "g10", 4);
            createLoad(b1, "d1", 1);
            createLoad(b3, "d3", 1);
            createLoad(b4, "d4", 1);
            createLoad(b5, "d5", 2);
            createLoad(b7, "d7", 2);
            createLoad(b8, "d8", 1);
            createLoad(b9, "d9", 1);

            return network;
        }
    }
}
