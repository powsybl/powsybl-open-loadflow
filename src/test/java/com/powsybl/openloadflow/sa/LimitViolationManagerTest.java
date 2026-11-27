/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.NetworkStateBranchFlows;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.test.networks.ZeroImpedanceNetworkFactory.createWith3BusesNonImpedantSubNetwork;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LimitViolationManagerTest {

    @Test
    void testBranchFlowsOnlyQueriedForZeroImpedanceBranchesWithLimits() {
        Network network = createWith3BusesNonImpedantSubNetwork();
        network.getLine("l25").getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        network.getLine("l12").getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).getFirst();
        LfBranch l25 = lfNetwork.getBranchById("l25");
        LfBranch l12 = lfNetwork.getBranchById("l12");
        assertTrue(l25.isZeroImpedance(LoadFlowModel.AC));
        assertFalse(l12.isZeroImpedance(LoadFlowModel.AC));

        // no load flow run: only the calls made by detection are checked, not the flow values
        NetworkStateBranchFlows branchFlows = spy(new NetworkStateBranchFlows(LoadFlowModel.AC, 1.0));
        new LimitViolationManager(List.of()).detectViolations(lfNetwork, branchFlows);

        verify(branchFlows).isZeroImpedance(l25);
        verify(branchFlows).getFlows(l25);
        // impedant branch evaluables are read directly
        verify(branchFlows).isZeroImpedance(l12);
        // branches without limits are never queried, so they never trigger a zero impedance flows computation
        verifyNoMoreInteractions(branchFlows);
    }
}
