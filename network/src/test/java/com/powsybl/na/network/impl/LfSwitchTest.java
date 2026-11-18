/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.network.impl;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.na.commons.Evaluable;
import com.powsybl.na.network.LfNetwork;
import com.powsybl.na.network.LfNetworkParameters;
import com.powsybl.na.test.networks.NodeBreakerNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
class LfSwitchTest {

    private Network network;

    private LfNetwork lfNetwork;

    private LfSwitch lfSwitch;

    @BeforeEach
    void setUp() {
        network = NodeBreakerNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new LfNetworkParameters().setBreakers(true), ReportNode.NO_OP);
        assertEquals(1, lfNetworks.size());
        lfNetwork = lfNetworks.get(0);
        lfSwitch = (LfSwitch) lfNetwork.getBranchById("B3");
    }

    @Test
    void getterTest() {
        assertEquals("B3", lfSwitch.getId());
        assertFalse(lfSwitch.hasPhaseControllerCapability());
        assertEquals(Double.NaN, lfSwitch.getP1().eval());
        assertEquals(Double.NaN, lfSwitch.getP2().eval());
        assertEquals(Double.NaN, lfSwitch.getI1().eval());
        assertEquals(Double.NaN, lfSwitch.getI2().eval());
        assertEquals(Collections.emptyList(), lfSwitch.getLimits1(LimitType.CURRENT, null));
        assertEquals(Collections.emptyList(), lfSwitch.getLimits2(LimitType.CURRENT, null));
    }

    @Test
    void setterTest() {
        Evaluable one = () -> 1.0;
        lfSwitch.setP1(one);
        assertEquals(Double.NaN, lfSwitch.getP1().eval());
        lfSwitch.setP2(one);
        assertEquals(Double.NaN, lfSwitch.getP2().eval());

        lfSwitch.setI1(one);
        assertEquals(Double.NaN, lfSwitch.getI1().eval());
        lfSwitch.setI2(one);
        assertEquals(Double.NaN, lfSwitch.getI2().eval());
    }

}
