/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
class BusDistanceTest {

    private LfNetwork network;

    private LfBus bus1;
    private LfBus bus2;

    @BeforeEach
    void setUp() {
        network = LfNetwork.load(VoltageControlNetworkFactory.createWithGeneratorFarFromRemoteControl(),
                new LfNetworkLoaderImpl(),
                new LfNetworkParameters()).get(0);
    }

    @Test
    void testBusDistance() {
        bus1 = network.getBusById("vl1_0"); // Bus "b1"
        bus2 = network.getBusById("vl4_0"); // Bus "b4"
        assertEquals(1, BusDistance.distanceBetweenBuses(bus1, bus2, 4));

        bus1 = network.getBusById("vl1_0"); // Bus "b1"
        bus2 = network.getBusById("vl2_0"); // Bus "b2"
        assertEquals(2, BusDistance.distanceBetweenBuses(bus1, bus2, 4));

        bus1 = network.getBusById("vl1_0"); // Bus "b1"
        bus2 = network.getBusById("vl4_2"); // Bus "b6"
        assertEquals(3, BusDistance.distanceBetweenBuses(bus1, bus2, 3));
    }

    @Test
    void testFartherThanMaxDistance() {
        bus1 = network.getBusById("vl1_0"); // Bus "b1"
        bus2 = network.getBusById("vl4_2"); // Bus "b6"
        assertEquals(Integer.MAX_VALUE, BusDistance.distanceBetweenBuses(bus1, bus2, 2));
    }

}
