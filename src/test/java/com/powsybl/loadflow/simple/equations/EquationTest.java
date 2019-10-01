/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationTest {

    @Test
    public void testEquals() {
        EquationSystem equationSystem = Mockito.mock(EquationSystem.class);
        new EqualsTester()
                .addEqualityGroup(new Equation(0, EquationType.BUS_P, equationSystem), new Equation(0, EquationType.BUS_P, equationSystem))
                .addEqualityGroup(new Equation(1, EquationType.BUS_Q, equationSystem), new Equation(1, EquationType.BUS_Q, equationSystem))
                .testEquals();
    }

    @Test
    public void testToString() {
        EquationSystem equationSystem = Mockito.mock(EquationSystem.class);
        assertEquals("Equation(num=0, type=BUS_P, row=-1)", new Equation(0, EquationType.BUS_P, equationSystem).toString());
    }
}
