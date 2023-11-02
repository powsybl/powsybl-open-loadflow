/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NameSlackBusSelectorTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = EurostagTutorialExample1Factory.create();
    }

    @Test
    void test() {
        List<LfNetwork> lfNetworks = Networks.load(network, new NameSlackBusSelector("VLGEN_0"));
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());

        lfNetworks = Networks.load(network, new NameSlackBusSelector("VLLOAD_0"));
        lfNetwork = lfNetworks.get(0);

        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
    }

    @Test
    void voltageLevelIdTest() {
        List<LfNetwork> lfNetworks = Networks.load(network, new NameSlackBusSelector("VLGEN"));
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());

        // test with multiple buses
        var vlgen = network.getVoltageLevel("VLGEN");
        vlgen.getBusBreakerView().newBus()
                .setId("NEW_BUS")
                .add();
        vlgen.getBusBreakerView().newSwitch()
                .setId("NEW_SWITCH")
                .setBus1("NGEN")
                .setBus2("NEW_BUS")
                .setOpen(false)
                .add();
        vlgen.newLoad()
                .setId("NEW_LOAD")
                .setConnectableBus("NEW_BUS")
                .setBus("NEW_BUS")
                .setP0(10)
                .setQ0(10)
                .add();
        LfNetworkParameters parameters = new LfNetworkParameters()
                .setSlackBusSelector(new NameSlackBusSelector("VLGEN"))
                .setBreakers(true);
        lfNetworks = Networks.load(network, parameters);
        lfNetwork = lfNetworks.get(0);
        assertEquals("NGEN", lfNetwork.getSlackBus().getId());
    }
}
