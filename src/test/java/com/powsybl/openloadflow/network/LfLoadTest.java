/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LfLoadTest {

    @Test
    void test() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        vlload.newLoad()
                .setId("LOAD2")
                .setConnectableBus("NLOAD")
                .setBus("NLOAD")
                .setP0(10)
                .setQ0(15)
                .add();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        LfBus bus = lfNetwork.getBusById("VLLOAD_0");
        assertEquals(List.of("LOAD", "LOAD2"), bus.getAggregatedLoads().getOriginalIds());
        assertEquals(2, bus.getAggregatedLoads().getLoadCount());
        assertEquals(610, bus.getAggregatedLoads().getAbsVariableLoadTargetP());
        var lfLoads = bus.getLoads();
        assertEquals(2, lfLoads.size());
        assertEquals("LOAD", lfLoads.get(0).getId());
        assertEquals(600, lfLoads.get(0).getTargetP());
        assertEquals(200, lfLoads.get(0).getTargetQ());
        assertEquals("LOAD2", lfLoads.get(1).getId());
        assertEquals(10, lfLoads.get(1).getTargetP());
        assertEquals(15, lfLoads.get(1).getTargetQ());
    }
}
