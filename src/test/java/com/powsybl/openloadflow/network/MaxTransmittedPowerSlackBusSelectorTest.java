/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class MaxTransmittedPowerSlackBusSelectorTest {

    @Test
    void test() {
        var network = EurostagTutorialExample1Factory.create();
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new MaxTransmittedPowerSlackBusSelector()).get(0);
        var slackBus = lfNetwork.getSlackBus();
        assertEquals("VLHV1_0", slackBus.getId());
    }

    @Test
    void testMultipleSlacks() {
        var network = EurostagTutorialExample1Factory.create();
        var parameters = new LfNetworkParameters()
                .setSlackBusSelector(new MaxTransmittedPowerSlackBusSelector())
                .setMaxSlackBusCount(3);
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters).get(0);
        var slackBusIds = lfNetwork.getSlackBuses().stream().map(LfBus::getId).collect(Collectors.toList());
        assertEquals(List.of("VLHV1_0", "VLHV2_0", "VLGEN_0"), slackBusIds);
    }
}
