/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.SlackTerminalAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class NetworkSlackBusSelectorTest {

    private Network network;

    private SlackBusSelector selectorMock;

    private boolean[] fallbackUsed;

    @BeforeEach
    void setUp() {
        network = EurostagTutorialExample1Factory.create();
        fallbackUsed = new boolean[1];
        MostMeshedSlackBusSelector selectorFallback = new MostMeshedSlackBusSelector();
        selectorMock = buses -> {
            fallbackUsed[0] = true;
            return selectorFallback.select(buses);
        };
    }

    @Test
    void noExtensionTest() {
        LfNetwork lfNetwork = LfNetwork.load(network, new NetworkSlackBusSelector(network, selectorMock)).get(0);
        assertEquals("VLHV1_0", lfNetwork.getSlackBus().getId());
        assertTrue(fallbackUsed[0]);
    }

    @Test
    void oneExtensionTest() {
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Load load = network.getLoad("LOAD");
        vlload.newExtension(SlackTerminalAdder.class)
                .withTerminal(load.getTerminal())
                .add();
        LfNetwork lfNetwork = LfNetwork.load(network, new NetworkSlackBusSelector(network, selectorMock)).get(0);
        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
        assertFalse(fallbackUsed[0]);
    }

    @Test
    void twoExtensionsTest() {
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        VoltageLevel vlgen = network.getVoltageLevel("VLGEN");
        Load load = network.getLoad("LOAD");
        Generator gen = network.getGenerator("GEN");
        vlload.newExtension(SlackTerminalAdder.class)
                .withTerminal(load.getTerminal())
                .add();
        vlgen.newExtension(SlackTerminalAdder.class)
                .withTerminal(gen.getTerminal())
                .add();
        LfNetwork lfNetwork = LfNetwork.load(network, new NetworkSlackBusSelector(network, selectorMock)).get(0);
        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
        assertTrue(fallbackUsed[0]);
    }
}
