/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfSwitchTest {

    Network network;

    LfNetwork lfNetwork;

    LfSwitch lfSwitch;

    AcLoadFlowParameters acLoadFlowParameters;

    @BeforeEach
    void setUp() {
        network = NodeBreakerNetworkFactory.create();
        acLoadFlowParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), new LoadFlowParameters(),
                new OpenLoadFlowParameters(), true);
        List<LfNetwork> lfNetworks = AcloadFlowEngine.createNetworks(network, acLoadFlowParameters);
        assertEquals(1, lfNetworks.size());
        lfNetwork = lfNetworks.get(0);
        lfSwitch = (LfSwitch) lfNetwork.getBranchById("B3");
    }

    @Test
    void loadTest() {
        assertEquals("B3", lfSwitch.getId());
        assertEquals(false, lfSwitch.hasPhaseControlCapability());
        assertEquals(Double.NaN, lfSwitch.getP1());
        assertEquals(Double.NaN, lfSwitch.getP2());
        assertEquals(Double.NaN, lfSwitch.getI1());
        assertEquals(Double.NaN, lfSwitch.getI2());
        assertEquals(Double.NaN, lfSwitch.getPermanentLimit1());
        assertEquals(Double.NaN, lfSwitch.getPermanentLimit2());
    }

    @Test
    void afterLoadFlowTest() {
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acLoadFlowParameters);
        engine.run();
        assertEquals(Double.NaN, lfSwitch.getP1());
        assertEquals(Double.NaN, lfSwitch.getP2());
        assertEquals(Double.NaN, lfSwitch.getI1());
        assertEquals(Double.NaN, lfSwitch.getI2());
        assertEquals(Double.NaN, lfSwitch.getPermanentLimit1());
        assertEquals(Double.NaN, lfSwitch.getPermanentLimit2());
    }

}
