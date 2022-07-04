/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LargestGeneratorSlackBusSelectorTest {

    @Test
    void test() {
        var network = DistributedSlackNetworkFactory.create();
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LargestGeneratorSlackBusSelector()).get(0);
        var slackBus = lfNetwork.getSlackBus();
        assertEquals("b2_vl_0", slackBus.getId());
    }
}
