/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LargestGeneratorSlackBusSelectorTest {

    @Test
    void test() {
        var network = DistributedSlackNetworkFactory.create();
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LargestGeneratorSlackBusSelector(5000)).get(0);
        var slackBus = lfNetwork.getSlackBus();
        assertEquals("b2_vl_0", slackBus.getId());
    }

    @Test
    void testFictitiousGenerator() {
        var network = DistributedSlackNetworkFactory.create();
        network.getGenerator("g2").setFictitious(true);
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LargestGeneratorSlackBusSelector(5000)).get(0);
        var slackBus = lfNetwork.getSlackBus();
        assertEquals("b3_vl_0", slackBus.getId());
    }

    @Test
    void testNonPlausibleMaxP() {
        var network = DistributedSlackNetworkFactory.create();
        network.getGenerator("g2").setMaxP(10000);
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LargestGeneratorSlackBusSelector(5000)).get(0);
        var slackBus = lfNetwork.getSlackBus();
        assertEquals("b3_vl_0", slackBus.getId());
    }

    @Test
    void testMultipleSlacks() {
        var network = DistributedSlackNetworkFactory.create();
        var parameters = new LfNetworkParameters()
                .setSlackBusSelector(new LargestGeneratorSlackBusSelector(5000))
                .setMaxSlackBusCount(3);
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters).get(0);
        var slackBusIds = lfNetwork.getSlackBuses().stream().map(LfBus::getId).collect(Collectors.toList());
        assertEquals(List.of("b2_vl_0", "b3_vl_0", "b1_vl_0"), slackBusIds);
    }
}
