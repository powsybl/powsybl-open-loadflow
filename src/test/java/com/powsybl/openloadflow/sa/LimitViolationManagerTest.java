/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlowsCache;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.network.ZeroImpedanceNetworkFactory.createWith3BusesNonImpedantSubNetwork;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LimitViolationManagerTest {

    @Test
    void testFlowsOnlyRequestedForBranchesWithLimits() {
        Network network = createWith3BusesNonImpedantSubNetwork();
        network.getLine("l25").getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters()).getFirst();
        assertTrue(lfNetwork.getBranchById("l25").isZeroImpedance(LoadFlowModel.AC));

        ZeroImpedanceFlowsCache zeroImpedanceFlowsCache = spy(new ZeroImpedanceFlowsCache(LoadFlowModel.AC, 1.0));
        new LimitViolationManager(List.of()).detectViolations(lfNetwork, zeroImpedanceFlowsCache);

        verify(zeroImpedanceFlowsCache).getFlows(lfNetwork.getBranchById("l25"));
        verifyNoMoreInteractions(zeroImpedanceFlowsCache);
    }
}
