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
import com.powsybl.openloadflow.network.impl.LfNetworks;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NameSlackBusSelectorTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private Network network;

    @Before
    public void setUp() {
        network = EurostagTutorialExample1Factory.create();
    }

    @Test
    public void test() {
        LfNetwork lfNetwork = LfNetworks.create(network, new NameSlackBusSelector("VLGEN_0")).get(0);
        assertEquals("VLGEN_0", lfNetwork.getSlackBus().getId());
        lfNetwork = LfNetworks.create(network, new NameSlackBusSelector("VLLOAD_0")).get(0);
        assertEquals("VLLOAD_0", lfNetwork.getSlackBus().getId());
    }

    @Test
    public void errorTest() {
        exception.expect(isA(PowsyblException.class));
        exception.expectMessage("Slack bus '???' not found");
        LfNetworks.create(network, new NameSlackBusSelector("???"));
    }
}
