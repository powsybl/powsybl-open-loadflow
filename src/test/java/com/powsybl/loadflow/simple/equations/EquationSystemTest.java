/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.simple.network.FirstSlackBusSelector;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.loadflow.simple.network.impl.LfNetworks;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class EquationSystemTest {

    private final List<Equation> equations = new ArrayList<>();
    private final List<EquationEventType> eventTypes = new ArrayList<>();

    private void clearEvents() {
        equations.clear();
        eventTypes.clear();
    }

    @Test
    public void test() {
        LfNetwork network = LfNetworks.create(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector()).get(0);
        LfBus bus = network.getBus(0);
        EquationSystem equationSystem = new EquationSystem(network);
        equationSystem.addListener((equation, eventType) -> {
            equations.add(equation);
            eventTypes.add(eventType);
        });
        VariableSet variableSet = new VariableSet();
        assertTrue(equations.isEmpty());
        assertTrue(eventTypes.isEmpty());
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusPhaseEquationTerm(bus, variableSet));
        assertEquals(1, equations.size());
        assertEquals(1, eventTypes.size());
        assertEquals(EquationEventType.EQUATION_CREATED, eventTypes.get(0));
        assertEquals(1, equationSystem.getSortedEquationsToSolve().size());

        clearEvents();
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).setActive(true);
        assertTrue(equations.isEmpty());
        assertTrue(eventTypes.isEmpty());

        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).setActive(false);
        assertEquals(1, equations.size());
        assertEquals(1, eventTypes.size());
        assertEquals(EquationEventType.EQUATION_DEACTIVATED, eventTypes.get(0));
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        clearEvents();
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).setActive(true);
        assertEquals(1, equations.size());
        assertEquals(1, eventTypes.size());
        assertEquals(EquationEventType.EQUATION_ACTIVATED, eventTypes.get(0));
        assertEquals(1, equationSystem.getSortedEquationsToSolve().size());
    }
}
