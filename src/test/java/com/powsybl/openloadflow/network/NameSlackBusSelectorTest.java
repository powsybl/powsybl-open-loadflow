/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.ComponentConstants;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new NameSlackBusSelector("VLGEN_0"));
        LfNetwork lfNetwork = lfNetworks.stream().filter(n -> n.getNum() == ComponentConstants.MAIN_NUM).findAny().orElse(null);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());

        lfNetworks = LfNetwork.load(network, new NameSlackBusSelector("VLLOAD_0"));
        lfNetwork = lfNetworks.stream().filter(n -> n.getNum() == ComponentConstants.MAIN_NUM).findAny().orElse(null);

        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
    }

    @Test
    void errorTest() {
        NameSlackBusSelector slackBusSelector = new NameSlackBusSelector("???");
        List<LfNetwork> lfNetworks = LfNetwork.load(network, slackBusSelector);
        LfNetwork lfNetwork = lfNetworks.stream().filter(n -> n.getNum() == ComponentConstants.MAIN_NUM).findAny().orElse(null);
        assertThrows(PowsyblException.class, () -> lfNetwork.getSlackBus(),
            "Slack bus '???' not found");
    }
}
