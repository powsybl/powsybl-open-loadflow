/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NameSlackBusSelectorTest {

    private Network network;

    @BeforeEach
    public void setUp() {
        network = EurostagTutorialExample1Factory.create();
    }

    @Test
    public void test() {
        LfNetwork lfNetwork = LfNetwork.load(network, new NameSlackBusSelector("VLGEN_0")).get(0);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());
        lfNetwork = LfNetwork.load(network, new NameSlackBusSelector("VLLOAD_0")).get(0);
        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
    }

    @Test
    public void errorTest() {
        assertThrows(PowsyblException.class,
            () -> LfNetwork.load(network, new NameSlackBusSelector("???")).get(0).getSlackBus(),
            "Slack bus '???' not found");
    }
}
