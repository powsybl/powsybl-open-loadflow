/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
class LfSynchronousNetworkImplTest {

    Network network;
    LfNetwork lfNetwork;

    @BeforeEach
    void setUp() {
        network = AcDcNetworkFactory.createMtDcNetworkWithThreeAcZones();
        LfNetworkParameters params = new LfNetworkParameters().setAcDcNetwork(true);
        lfNetwork = Networks.load(network, params).getFirst();
    }

    @Test
    void testGetBusesReturnsOnlyTheBusesInsideTheSynchronousComponent() {
        LfSynchronousNetwork firstSynchronousNetwork = lfNetwork.getSynchronousNetworks().getFirst();
        List<LfBus> firstScBuses = firstSynchronousNetwork.getBuses();
        assertEquals(List.of(lfNetwork.getBusById("b1_vl_0")), firstScBuses);

        LfSynchronousNetwork secondSynchronousNetwork = lfNetwork.getSynchronousNetworks().get(1);
        List<LfBus> secondScBuses = secondSynchronousNetwork.getBuses();
        assertEquals(List.of(lfNetwork.getBusById("b2_vl_0")), secondScBuses);

        LfSynchronousNetwork thirdSynchronousNetwork = lfNetwork.getSynchronousNetworks().get(2);
        List<LfBus> thirdScBuses = thirdSynchronousNetwork.getBuses();
        assertEquals(List.of(lfNetwork.getBusById("b3_vl_0")), thirdScBuses);
    }

    @Test
    void testSetExcludedSlackBusesInvalidatesSlackBuses() {
        LfSynchronousNetwork synchronousNetwork = spy(lfNetwork.getSynchronousNetwork(0));
        Set<LfBus> excludedSlackBuses = Set.of(
            lfNetwork.getBusById("b1_vl_0"), // Belongs to this synchronous network
            lfNetwork.getBusById("b2_vl_0") // Does not belong to this synchronous network
        );

        // Call method
        synchronousNetwork.setExcludedSlackBuses(excludedSlackBuses);

        // Check excluded slack bus only include b1_vl_0
        assertEquals(Set.of(lfNetwork.getBusById("b1_vl_0")), synchronousNetwork.getExcludedSlackBuses());

        // Check invalidateSlackAndReference has been called
        verify(synchronousNetwork, times(1)).invalidateSlackAndReference();
    }

    @Test
    void testSetExcludedSlackBusesDoesNotInvalidateSlackBuses() {
        LfSynchronousNetwork synchronousNetwork = spy(lfNetwork.getSynchronousNetwork(0));
        Set<LfBus> excludedSlackBuses = Set.of(
            lfNetwork.getBusById("b2_vl_0"), // Does not belong to this synchronous network
            lfNetwork.getBusById("b3_vl_0") // Does not belong to this synchronous network
        );

        // Call method
        synchronousNetwork.setExcludedSlackBuses(excludedSlackBuses);

        // Check excluded slack bus is empty
        assertEquals(Set.of(), synchronousNetwork.getExcludedSlackBuses());

        // Check invalidateSlackAndReference has not been called
        verify(synchronousNetwork, never()).invalidateSlackAndReference();
    }
}
