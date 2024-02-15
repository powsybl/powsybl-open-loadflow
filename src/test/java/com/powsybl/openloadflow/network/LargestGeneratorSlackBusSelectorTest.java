/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
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

    @Test
    void testCountryToFiler() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(),
                new LargestGeneratorSlackBusSelector(5000)).get(0);
        LfBus slackBus = lfNetwork.getSlackBus();
        assertEquals("S2VL1_0", slackBus.getId());

        network.getSubstation("S1").setCountry(Country.FR);
        network.getSubstation("S2").setCountry(Country.BE);
        network.getSubstation("S3").setCountry(Country.FR);
        network.getSubstation("S4").setCountry(Country.FR);
        lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(),
                new LargestGeneratorSlackBusSelector(5000, Collections.singleton(Country.FR))).get(0);
        slackBus = lfNetwork.getSlackBus();
        assertEquals("S3VL1_0", slackBus.getId());

        lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(),
                new LargestGeneratorSlackBusSelector(5000,
                        Set.of(Country.BE, Country.FR))).get(0);
        slackBus = lfNetwork.getSlackBus();
        assertEquals("S2VL1_0", slackBus.getId());
    }
}
