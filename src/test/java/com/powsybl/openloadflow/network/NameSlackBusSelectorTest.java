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
import com.powsybl.openloadflow.util.Profiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class NameSlackBusSelectorTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = EurostagTutorialExample1Factory.create();
    }

    @Test
    void test() {
        Profiler profiler = Profiler.NO_OP;
        LfNetwork lfNetwork = LfNetwork.load(network, new NameSlackBusSelector("VLGEN_0"), profiler).get(0);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());
        lfNetwork = LfNetwork.load(network, new NameSlackBusSelector("VLLOAD_0"), profiler).get(0);
        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
    }

    @Test
    void errorTest() {
        NameSlackBusSelector slackBusSelector = new NameSlackBusSelector("???");
        LfNetwork lfNetwork = LfNetwork.load(network, slackBusSelector, Profiler.NO_OP).get(0);
        assertThrows(PowsyblException.class, () -> lfNetwork.getSlackBus(),
            "Slack bus '???' not found");
    }
}
