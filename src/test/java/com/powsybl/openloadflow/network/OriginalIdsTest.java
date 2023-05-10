/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OriginalIdsTest {

    @Test
    void testWith3wtNetwork() {
        var network = T3wtFactory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        assertEquals(List.of("3wt"), lfNetwork.getBranchById("3wt_leg_1").getOriginalIds());
        assertEquals(LfBranch.BranchType.TRANSFO_3_LEG_1, lfNetwork.getBranchById("3wt_leg_1").getBranchType());
        assertEquals(LfBranch.BranchType.TRANSFO_3_LEG_2, lfNetwork.getBranchById("3wt_leg_2").getBranchType());
        assertEquals(LfBranch.BranchType.TRANSFO_3_LEG_3, lfNetwork.getBranchById("3wt_leg_3").getBranchType());
        assertEquals(List.of("3wt"), lfNetwork.getBusById("3wt_BUS0").getOriginalIds());
        assertEquals(List.of("vl1_0"), lfNetwork.getBusById("vl1_0").getOriginalIds());
        assertEquals("g1", lfNetwork.getGeneratorById("g1").getOriginalId());
        assertEquals(List.of("ld2"), lfNetwork.getBusById("vl2_0").getLoad().getOriginalIds());
    }

    @Test
    void testWithBoundaryNetwork() {
        var network = BoundaryFactory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        assertEquals(List.of("l1"), lfNetwork.getBranchById("l1").getOriginalIds());
        assertEquals(LfBranch.BranchType.LINE, lfNetwork.getBranchById("l1").getBranchType());
        assertEquals(List.of("dl1"), lfNetwork.getBranchById("dl1").getOriginalIds());
        assertEquals(LfBranch.BranchType.DANGLING_LINE, lfNetwork.getBranchById("dl1").getBranchType());
        assertEquals(List.of("dl1"), lfNetwork.getBusById("dl1_BUS").getOriginalIds());
        assertEquals("dl1", lfNetwork.getGeneratorById("dl1_GEN").getOriginalId());
    }
}
