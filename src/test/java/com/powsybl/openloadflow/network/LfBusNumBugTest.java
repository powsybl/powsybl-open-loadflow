/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LfBusNumBugTest {

    private LfNetwork lfNetwork;

    @BeforeEach
    void setUp() {
        Network network = EurostagTutorialExample1Factory.create();
        lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
    }

    @Test
    void test() {
        LfBranch branch = lfNetwork.getBranch(0);
        LfBus bus = branch.getBus1();
        assertNotEquals(-1, bus.getNum());
    }

    @Test
    void test2() {
        LfBus bus = lfNetwork.getBusById("VLGEN_0");
        assertNotEquals(-1, bus.getNum());
    }
}
