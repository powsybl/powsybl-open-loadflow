/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Etienne Lesot <etienne.lesot@rte-france.com>
 */
class MostMeshedSlackBusSelectorTest {
    @Test
    void testCountryToFiler() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(),
                new MostMeshedSlackBusSelector(Collections.emptySet())).get(0);
        LfBus slackBus = lfNetwork.getSlackBus();
        assertEquals("S3VL1_0", slackBus.getId());
        network.getSubstation("S1").setCountry(Country.FR);
        network.getSubstation("S2").setCountry(Country.BE);
        network.getSubstation("S3").setCountry(Country.BE);
        network.getSubstation("S4").setCountry(Country.FR);
        lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(),
                new MostMeshedSlackBusSelector(Collections.singleton(Country.FR))).get(0);
        slackBus = lfNetwork.getSlackBus();
        assertEquals("S4VL1_0", slackBus.getId());

        lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(),
                new MostMeshedSlackBusSelector(new HashSet<>(Arrays.asList(Country.BE, Country.FR)))).get(0);
        slackBus = lfNetwork.getSlackBus();
        assertEquals("S3VL1_0", slackBus.getId());
    }
}
