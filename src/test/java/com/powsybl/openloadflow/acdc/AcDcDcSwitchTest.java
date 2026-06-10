/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.acdc;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.network.AcDcNetworkFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for DC switch resistance modeling in AC/DC load flow.
 * A DC switch with R > 0 must be loaded as a DC branch (LfDcLine) in the LfNetwork.
 * A DC switch with R = 0 must be discarded: PowSyBl Core topology merges its two DC nodes
 * into the same DC bus, so OpenLoadFlow sees both ends as the same node and drops the branch.
 *
 * @author Landry Huet {@literal <landry.huet at supergrid-institute.com>}
 */
class AcDcDcSwitchTest {

    private LfNetworkParameters networkParameters;

    @BeforeEach
    void setUp() {
        networkParameters = new LfNetworkParameters().setAcDcNetwork(true);
    }

    /**
     * When a DC switch has R = 0, PowSyBl Core topology merges its two DC nodes into the same
     * DC bus. OpenLoadFlow must then discard it (both ends point to the same LfDcBus),
     * leaving no DC branch for the switch.
     */
    @Test
    void testDcSwitchWithZeroResistanceIsNotLoadedAsBranch() {
        // dn3 --[sw R=0]--> dn4: PowSyBl Core merges dn3 and dn4 into one DC bus
        Network network = createAcDcNetworkWithDcSwitchOnly(0.0);

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // The switch is discarded because both DC terminals resolve to the same LfDcBus
        assertEquals(0, lfNetwork.getDcBranches().size());
        // dn3+dn4 are merged (1 bus) + dnDummy3 (1) + dnDummy4 (1) = 3 DC buses
        assertEquals(3, lfNetwork.getDcBuses().size());
    }

    /**
     * When a DC switch has R > 0, its two DC nodes remain distinct. OpenLoadFlow must load
     * the switch as a LfDcLine connecting two separate LfDcBus instances.
     */
    @Test
    void testDcSwitchWithNonZeroResistanceIsLoadedAsBranch() {
        // dn3 --[sw R=0.5]--> dn4: nodes stay distinct
        Network network = createAcDcNetworkWithDcSwitchOnly(0.5);

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // The switch must appear as exactly one LfDcLine
        assertEquals(1, lfNetwork.getDcBranches().size());
        // dn3 (1) + dn4 (1) + dnDummy3 (1) + dnDummy4 (1) = 4 DC buses
        assertEquals(4, lfNetwork.getDcBuses().size());
    }

    /**
     * An open DC switch must never be loaded as a branch, regardless of its resistance.
     * Core topology does not merge nodes across an open switch, so both nodes resolve to
     * distinct LfDcBus instances — but the circuit is broken and no branch should appear.
     */
    @Test
    void testOpenDcSwitchIsNotLoadedAsBranch() {
        // dn3 --[sw R=0.5, open]--> dn4: circuit breaker is open
        Network network = createAcDcNetworkWithDcSwitchOnly(0.5);
        network.getDcSwitch("sw34").setOpen(true);

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        assertEquals(0, lfNetwork.getDcBranches().size());
    }

    /**
     * Meshed case: dl34 and an open sw34 both connect dn3 to dn4. Because dl34 keeps the DC
     * component connected, both ends of sw34 resolve to distinct non-null LfDcBuses — the guard
     * must still discard the open switch (the null-bus shortcut does not apply here).
     */
    @Test
    void testOpenDcSwitchInParallelWithDcLineIsNotLoadedAsBranch() {
        Network network = createAcDcNetworkWithParallelOpenDcSwitch();

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // Only dl34 must be present; the open sw34 must be discarded
        assertEquals(1, lfNetwork.getDcBranches().size());
    }
}
