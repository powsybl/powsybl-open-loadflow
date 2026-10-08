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
class LfSvcShuntTest {

    @Test
    void test() {
        LfNetwork network = Mockito.mock(LfNetwork.class);
        LfBus bus = Mockito.mock(LfBus.class);
        Mockito.when(bus.getNetwork()).thenReturn(network);
        Mockito.when(bus.getNominalV()).thenReturn(380d);
        Mockito.when(bus.getId()).thenReturn("bus");
        LfStaticVarCompensator svc1 = Mockito.mock(LfStaticVarCompensator.class);
        Mockito.when(svc1.getOriginalId()).thenReturn("svc1");
        Mockito.when(svc1.getB()).thenReturn(0.001);
        LfStaticVarCompensator svc2 = Mockito.mock(LfStaticVarCompensator.class);
        Mockito.when(svc2.getOriginalId()).thenReturn("svc2");
        Mockito.when(svc2.getB()).thenReturn(-0.003);
        LfStaticVarCompensator svc3 = Mockito.mock(LfStaticVarCompensator.class);
        Mockito.when(svc3.getOriginalId()).thenReturn("svc3");
        Mockito.when(svc3.getB()).thenReturn(0.005);
        Mockito.when(svc3.isDisabled()).thenReturn(true);
        Mockito.when(bus.getGenerators()).thenReturn(List.of(svc1, svc2, svc3));
        LfSvcShunt shunt = new LfSvcShunt(bus);
        assertEquals("bus_svc_shunt", shunt.getId());
        assertEquals(List.of("svc1", "svc2", "svc3"), shunt.getOriginalIds());
        assertEquals(ElementType.SHUNT_COMPENSATOR, shunt.getType());
        assertEquals(0, shunt.getG(), 0);
        assertEquals(0, shunt.getB(), 0);
        // sum of the susceptances of the enabled SVCs
        shunt.update();
        assertEquals((0.001 - 0.003) * 380 * 380 / 100, shunt.getB(), 1e-12);
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
