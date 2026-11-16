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
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlowsCache;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.network.ZeroImpedanceNetworkFactory.createWith3BusesNonImpedantSubNetwork;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LimitViolationManagerTest {

    @Test
    void testCacheOnlyQueriedForZeroImpedanceBranchesWithLimits() {
        Network network = createWith3BusesNonImpedantSubNetwork();
        network.getLine("l25").getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        network.getLine("l12").getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).getFirst();
        LfBranch l25 = lfNetwork.getBranchById("l25");
        LfBranch l12 = lfNetwork.getBranchById("l12");
        assertTrue(l25.isZeroImpedance(LoadFlowModel.AC));
        assertFalse(l12.isZeroImpedance(LoadFlowModel.AC));

        // flow values are irrelevant (no load flow run): the spy only records which cache calls detection makes
        ZeroImpedanceFlowsCache zeroImpedanceFlowsCache = spy(new ZeroImpedanceFlowsCache(LoadFlowModel.AC, 1.0));
        new LimitViolationManager(List.of()).detectViolations(lfNetwork, zeroImpedanceFlowsCache);

        // Only limit violation detection is checked here. In security analysis the same cache is shared with state
        // monitor results, which request flows of every monitored branch, with or without limits: a zero impedance
        // network can therefore still be computed for a branch without limits if it is monitored.

        // zero impedance branch with limits: detection needs its flows, computed by the cache
        verify(zeroImpedanceFlowsCache).isZeroImpedance(l25);
        verify(zeroImpedanceFlowsCache).getFlows(l25);
        // impedant branch with limits: detection reads its evaluables directly, only for limited quantities
        // (getFlows would evaluate all six flows)
        verify(zeroImpedanceFlowsCache).isZeroImpedance(l12);
        verify(zeroImpedanceFlowsCache, never()).getFlows(l12);
        // branches without limits (l23, l35 zero impedance, l34, l56 impedant): detection must not query the cache
        // at all, so it never triggers a zero impedance flows computation by itself
        verifyNoMoreInteractions(zeroImpedanceFlowsCache);
    }
}
