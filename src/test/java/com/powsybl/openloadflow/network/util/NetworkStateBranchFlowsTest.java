/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.test.networks.FourBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkStateBranchFlowsTest {

    private LfNetwork lfNetwork;

    private NetworkStateBranchFlows branchFlows;

    @BeforeEach
    void setUp() {
        // two separate zero impedance networks {b1, b2} and {b3, b4}, plus zero impedance l14 open at side 2
        Network network = FourBusNetworkFactory.create();
        network.getLine("l12").setR(0).setX(0);
        network.getLine("l34").setR(0).setX(0);
        network.getLine("l14").setR(0).setX(0).getTerminal(TwoSides.TWO).disconnect();
        lfNetwork = Networks.load(network, new LfNetworkParameters()).getFirst();
        branchFlows = new NetworkStateBranchFlows(LoadFlowModel.AC, 1.0);
    }

    @Test
    void testOnlyRequestedZeroImpedanceNetworksAreComputed() {
        LfBranch l12 = lfNetwork.getBranchById("l12");
        LfBranch l34 = lfNetwork.getBranchById("l34");
        LfBranch l13 = lfNetwork.getBranchById("l13");
        assertTrue(l12.isZeroImpedance(LoadFlowModel.AC));
        assertTrue(l34.isZeroImpedance(LoadFlowModel.AC));
        assertFalse(l13.isZeroImpedance(LoadFlowModel.AC));

        assertNotNull(branchFlows.getFlows(l13));
        assertEquals(0, branchFlows.getComputedZeroImpedanceNetworkCount());

        assertNotNull(branchFlows.getFlows(l12));
        assertEquals(1, branchFlows.getComputedZeroImpedanceNetworkCount());

        assertNotNull(branchFlows.getFlows(l12));
        assertEquals(1, branchFlows.getComputedZeroImpedanceNetworkCount());

        assertNotNull(branchFlows.getFlows(l34));
        assertEquals(2, branchFlows.getComputedZeroImpedanceNetworkCount());
    }

    @Test
    void testZeroImpedanceBranchOpenAtOneSide() {
        LfBranch l14 = lfNetwork.getBranchById("l14");
        assertTrue(l14.isZeroImpedance(LoadFlowModel.AC));
        assertNull(l14.getBus2());

        LfBranch.LfBranchResults flows = branchFlows.getFlows(l14);
        assertEquals(0, branchFlows.getComputedZeroImpedanceNetworkCount());
        assertEquals(new LfBranch.LfBranchResults(0, Double.NaN, 0, Double.NaN, 0, Double.NaN), flows);
    }
}
