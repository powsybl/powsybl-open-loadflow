/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.iidm.network.extensions.SlackTerminalAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class NetworkSlackBusSelectorTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = EurostagTutorialExample1Factory.create(); }

    @Test
    void testUpdateState() {
        LfNetwork lfNetwork = LfNetwork.load(network, new MostMeshedSlackBusSelector()).get(0);
        assertEquals("VLHV1_0", lfNetwork.getSlackBus().getId());
        lfNetwork.updateState(true);
        SlackTerminal slackTerminal =  null;
        for (VoltageLevel vl : network.getVoltageLevels()) {
            slackTerminal = vl.getExtension(SlackTerminal.class);
            if (slackTerminal != null) {
                assertEquals("VLHV1_0", slackTerminal.getTerminal().getBusView().getBus().getId());
            }
        }
    }

    @Test
    void testNetworkSlackBusSelection() {

        VoltageLevel vl = network.getVoltageLevel("VLGEN");
        vl.newExtension(SlackTerminalAdder.class)
                .setTerminal(vl.getBusView().getBuses().iterator().next().getConnectedTerminals().iterator().next())
                .add();
        LfNetwork lfNetwork = LfNetwork.load(network, new NetworkSlackBusSelector(network)).get(0);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());
    }

    @Test
    void errorTest() {
    }
}
