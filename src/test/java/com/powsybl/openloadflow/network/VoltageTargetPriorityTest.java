/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
class VoltageTargetPriorityTest {

    @Test
    void testControlTargetPriority() {
        assertEquals(3, ControlTargetPriority.values().length);
        assertEquals(0, ControlTargetPriority.GENERATOR.getPriority());
        assertEquals(1, ControlTargetPriority.TRANSFORMER.getPriority());
        assertEquals(2, ControlTargetPriority.SHUNT.getPriority());
    }

    @Test
    void testVoltageTargetPriority() {
        List<Integer> voltageTargetPriority = List.of();
        assertThrows(IllegalStateException.class, () -> VoltageControl.setVoltageTargetPriority(voltageTargetPriority));

        List<Integer> voltageTargetPriority2 = List.of(0, 1, 4);
        assertThrows(IllegalStateException.class, () -> VoltageControl.setVoltageTargetPriority(voltageTargetPriority2));
    }

}
