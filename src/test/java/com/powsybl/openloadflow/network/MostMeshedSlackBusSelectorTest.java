/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class MostMeshedSlackBusSelectorTest {

    @Test
    void test() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new MostMeshedSlackBusSelector()).get(0);
        assertEquals("VLHV1_0", lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst().getId());
        lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new MostMeshedSlackBusSelector(50, Collections.emptySet())).get(0);
        assertEquals("VLLOAD_0", lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst().getId());
    }
}
