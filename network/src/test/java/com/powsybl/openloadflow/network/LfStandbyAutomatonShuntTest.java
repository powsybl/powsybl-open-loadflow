/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class LfStandbyAutomatonShuntTest {

    @Test
    void test() {
        LfNetwork network = Mockito.mock(LfNetwork.class);
        LfBus bus = Mockito.mock(LfBus.class);
        Mockito.when(bus.getNetwork()).thenReturn(network);
        Mockito.when(bus.getNominalV()).thenReturn(380d);
        LfStaticVarCompensator svc = Mockito.mock(LfStaticVarCompensator.class);
        Mockito.when(svc.getBus()).thenReturn(bus);
        Mockito.when(svc.getId()).thenReturn("svc");
        Mockito.when(svc.getOriginalId()).thenReturn("svc");
        Mockito.when(svc.getB0()).thenReturn(0.001);
        LfStandbyAutomatonShunt shunt = LfStandbyAutomatonShunt.create(svc);
        assertEquals("svc_standby_automaton_b0", shunt.getId());
        assertEquals(List.of("svc"), shunt.getOriginalIds());
        assertEquals(ElementType.SHUNT_COMPENSATOR, shunt.getType());
        assertEquals(0, shunt.getG(), 0);
        assertEquals(0, shunt.getB(), 1.444);
        assertTrue(shunt.getVoltageControl().isEmpty());
        assertFalse(shunt.isVoltageControlEnabled());
        assertFalse(shunt.hasVoltageControlCapability());
        assertNotNull(assertThrows(UnsupportedOperationException.class, () -> shunt.setG(0)));
        assertNotNull(assertThrows(UnsupportedOperationException.class, () -> shunt.setVoltageControl(null)));
        assertNotNull(assertThrows(UnsupportedOperationException.class, () -> shunt.setVoltageControlCapability(true)));
        assertNotNull(assertThrows(UnsupportedOperationException.class, () -> shunt.setVoltageControlEnabled(true)));
        assertNotNull(assertThrows(UnsupportedOperationException.class, shunt::dispatchB));
    }
}
