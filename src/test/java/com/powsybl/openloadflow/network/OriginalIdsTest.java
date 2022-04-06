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
    void test() {
        var network = T3wtFactory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        assertEquals(List.of("3wt"), lfNetwork.getBranchById("3wt_leg_1").getOriginalIds());
        assertEquals(LfBranch.BranchType.TRANSFO_3_LEG_1, lfNetwork.getBranchById("3wt_leg_1").getBranchType());
        assertEquals(LfBranch.BranchType.TRANSFO_3_LEG_2, lfNetwork.getBranchById("3wt_leg_2").getBranchType());
        assertEquals(LfBranch.BranchType.TRANSFO_3_LEG_3, lfNetwork.getBranchById("3wt_leg_3").getBranchType());
        assertEquals(List.of("vl1_0"), lfNetwork.getBusById("vl1_0").getOriginalIds());
        assertEquals("g1", lfNetwork.getGeneratorById("g1").getOriginalId());
        assertEquals(List.of("ld2"), lfNetwork.getBusById("vl2_0").getLoads().getOriginalIds());
    }
}
