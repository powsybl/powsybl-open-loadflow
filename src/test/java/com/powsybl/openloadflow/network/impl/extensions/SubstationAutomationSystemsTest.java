/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.test.AbstractConverterTest;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.NetworkXml;
import com.powsybl.openloadflow.network.AutomationSystemNetworkFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SubstationAutomationSystemsTest extends AbstractConverterTest {

    @Test
    void errorTest() {
        Network network = AutomationSystemNetworkFactory.create();
        var s2 = network.getSubstation("s2");

        var adder = s2.newExtension(SubstationAutomationSystemsAdder.class)
                .newOverloadManagementSystem();
        var e = assertThrows(PowsyblException.class, adder::add);
        assertEquals("Line ID to monitor is not set", e.getMessage());
        adder.withLineIdToMonitor("x");
        e = assertThrows(PowsyblException.class, adder::add);
        assertEquals("Threshold is not set", e.getMessage());
        adder.withThreshold(1000);
        e = assertThrows(PowsyblException.class, adder::add);
        assertEquals("Switch ID to operate is not set", e.getMessage());
    }

    @Test
    void xmlRoundTripTest() throws IOException {
        Network network = AutomationSystemNetworkFactory.create();

        Network network2 = roundTripXmlTest(network,
                NetworkXml::writeAndValidate,
                NetworkXml::read,
                "/substationAutomationSystemsRef.xml");

        SubstationAutomationSystems substationAutomationSystems = network2.getSubstation("s1").getExtension(SubstationAutomationSystems.class);
        assertNotNull(substationAutomationSystems);
        assertEquals(1, substationAutomationSystems.getOverloadManagementSystems().size());
    }
}
