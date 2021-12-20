/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.testing.EqualsTester;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationTest {

    private EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private LfNetwork network;

    @BeforeEach
    void setUp() {
        equationSystem = Mockito.mock(EquationSystem.class);
        network = Mockito.mock(LfNetwork.class);
        LfBus bus = Mockito.mock(LfBus.class);
        Mockito.when(network.getBus(Mockito.anyInt())).thenReturn(bus);
        Mockito.when(bus.getId()).thenReturn("bus1");
        Mockito.when(bus.getType()).thenReturn(ElementType.BUS);
        LfBranch branch = Mockito.mock(LfBranch.class);
        Mockito.when(network.getBranch(Mockito.anyInt())).thenReturn(branch);
        Mockito.when(branch.getId()).thenReturn("branch1");
        Mockito.when(branch.getType()).thenReturn(ElementType.BRANCH);
    }

    @Test
    void testEquals() {
        new EqualsTester()
                .addEqualityGroup(new Equation<>(0, AcEquationType.BUS_TARGET_P, equationSystem), new Equation<>(0, AcEquationType.BUS_TARGET_P, equationSystem))
                .addEqualityGroup(new Equation<>(1, AcEquationType.BUS_TARGET_Q, equationSystem), new Equation<>(1, AcEquationType.BUS_TARGET_Q, equationSystem))
                .testEquals();
    }

    @Test
    void testGetElement() {
        var eq1 = new Equation<>(0, AcEquationType.BUS_TARGET_P, equationSystem);
        Optional<LfElement> bus = eq1.getElement(network);
        assertTrue(bus.isPresent());
        assertEquals("bus1", bus.map(LfElement::getId).orElseThrow());
        assertEquals(ElementType.BUS, bus.map(LfElement::getType).orElseThrow());

        var eq2 = new Equation<>(0, AcEquationType.BRANCH_TARGET_ALPHA1, equationSystem);
        Optional<LfElement> branch = eq2.getElement(network);
        assertTrue(branch.isPresent());
        assertEquals("branch1", branch.map(LfElement::getId).orElseThrow());
        assertEquals(ElementType.BRANCH, branch.map(LfElement::getType).orElseThrow());
    }

    @Test
    void testToString() {
        assertEquals("Equation(elementNum=0, type=BUS_TARGET_P, column=-1)", new Equation<>(0, AcEquationType.BUS_TARGET_P, equationSystem).toString());
        assertEquals("Equation(elementNum=1, type=DISTR_Q, column=-1)", new Equation<>(1, AcEquationType.DISTR_Q, equationSystem).toString());
    }
}
