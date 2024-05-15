/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class PerUnitPrecisionLossTest {

    @Test
    void test() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters()).get(0);
        LfBus bus = lfNetwork.getBus(3);
        bus.getLoads().get(0).setTargetP(0.47585192963466116);
        assertEquals(0.47585192963466116, bus.getLoadTargetP(), 0);
    }
}
