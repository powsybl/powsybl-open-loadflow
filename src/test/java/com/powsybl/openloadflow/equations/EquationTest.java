/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.testing.EqualsTester;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTest {

    private EquationSystem equationSystem;

    @BeforeEach
    void setUp() {
        equationSystem = Mockito.mock(EquationSystem.class);
        LfNetwork network = Mockito.mock(LfNetwork.class);
        LfBus bus = Mockito.mock(LfBus.class);
        Mockito.when(network.getBus(Mockito.anyInt())).thenReturn(bus);
        Mockito.when(bus.getId()).thenReturn("bus1");
        Mockito.when(equationSystem.getNetwork()).thenReturn(network);
    }

    @Test
    public void testEquals() {
        new EqualsTester()
                .addEqualityGroup(new Equation(0, EquationType.BUS_P, equationSystem), new Equation(0, EquationType.BUS_P, equationSystem))
                .addEqualityGroup(new Equation(1, EquationType.BUS_Q, equationSystem), new Equation(1, EquationType.BUS_Q, equationSystem))
                .testEquals();
    }

    @Test
    public void testToString() {
        assertEquals("Equation(num=0, busId=bus1, type=BUS_P, row=-1)", new Equation(0, EquationType.BUS_P, equationSystem).toString());
        assertEquals("Equation(num=1, type=ZERO, row=-1)", new Equation(1, EquationType.ZERO, equationSystem).toString());
    }
}
