/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.BusVoltageEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Profiler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationSystemTest {

    private final List<Equation> equations = new ArrayList<>();
    private final List<EquationEventType> equationEventTypes = new ArrayList<>();
    private final List<EquationTermEventType> equationTermEventTypes = new ArrayList<>();

    private void clearEvents() {
        equations.clear();
        equationEventTypes.clear();
        equationTermEventTypes.clear();
    }

    @Test
    void test() {
        Profiler profiler = Profiler.NO_OP;
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector(), profiler).get(0);
        LfBus bus = network.getBus(0);
        EquationSystem equationSystem = new EquationSystem(network, true, profiler);
        equationSystem.addListener(new EquationSystemListener() {
            @Override
            public void onEquationChange(Equation equation, EquationEventType eventType) {
                equations.add(equation);
                equationEventTypes.add(eventType);
            }

            @Override
            public void onEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
                equationTermEventTypes.add(eventType);
            }

            @Override
            public void onStateUpdate(double[] x) {
                // nothing to do
            }
        });
        VariableSet variableSet = new VariableSet();
        assertTrue(equations.isEmpty());
        assertTrue(equationEventTypes.isEmpty());
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusPhaseEquationTerm(bus, variableSet));
        assertEquals(1, equations.size());
        assertEquals(1, equationEventTypes.size());
        assertEquals(1, equationTermEventTypes.size());
        assertEquals(EquationEventType.EQUATION_CREATED, equationEventTypes.get(0));
        assertEquals(EquationTermEventType.EQUATION_TERM_ADDED, equationTermEventTypes.get(0));
        assertEquals(1, equationSystem.getSortedEquationsToSolve().size());

        clearEvents();
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).setActive(true);
        assertTrue(equations.isEmpty());
        assertTrue(equationEventTypes.isEmpty());

        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).setActive(false);
        assertEquals(1, equations.size());
        assertEquals(1, equationEventTypes.size());
        assertEquals(EquationEventType.EQUATION_DEACTIVATED, equationEventTypes.get(0));
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        clearEvents();
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).setActive(true);
        assertEquals(1, equations.size());
        assertEquals(1, equationEventTypes.size());
        assertEquals(EquationEventType.EQUATION_ACTIVATED, equationEventTypes.get(0));
        assertEquals(1, equationSystem.getSortedEquationsToSolve().size());

        assertTrue(equationSystem.getEquation(bus.getNum(), EquationType.BUS_V).isPresent());
        assertEquals(1, equationSystem.getEquations(SubjectType.BUS, bus.getNum()).size());
        assertFalse(equationSystem.getEquation(99, EquationType.BUS_V).isPresent());
        assertTrue(equationSystem.getEquations(SubjectType.BUS, 99).isEmpty());

        assertEquals(1, equationSystem.getEquationTerms(SubjectType.BUS, bus.getNum()).size());
        assertTrue(equationSystem.getEquationTerms(SubjectType.BRANCH, 0).isEmpty());

        clearEvents();
        BusVoltageEquationTerm equationTerm = new BusVoltageEquationTerm(bus, variableSet);
        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(equationTerm);
        assertEquals(2, equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).getTerms().size());
        assertEquals(0, equations.size());
        assertEquals(0, equationEventTypes.size());
        assertEquals(1, equationTermEventTypes.size());
        assertEquals(EquationTermEventType.EQUATION_TERM_ADDED, equationTermEventTypes.get(0));

        clearEvents();
        equationTerm.setActive(false);
        assertEquals(0, equations.size());
        assertEquals(0, equationEventTypes.size());
        assertEquals(1, equationTermEventTypes.size());
        assertEquals(EquationTermEventType.EQUATION_TERM_DEACTIVATED, equationTermEventTypes.get(0));
    }

    @Test
    void writeAcSystemTest() throws IOException {
        Profiler profiler = Profiler.NO_OP;
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector(), profiler).get(0);
        EquationSystem equationSystem = AcEquationSystem.create(network, profiler);
        try (StringWriter writer = new StringWriter()) {
            equationSystem.write(writer);
            writer.flush();
            String ref = String.join(System.lineSeparator(),
                    "v0 = v0",
                    "φ0 = φ0",
                    "p1 = ac_p_closed_2(v0, v1, φ0, φ1) + ac_p_closed_1(v1, v2, φ1, φ2) + ac_p_closed_1(v1, v2, φ1, φ2)",
                    "q1 = ac_q_closed_2(v0, v1, φ0, φ1) + ac_q_closed_1(v1, v2, φ1, φ2) + ac_q_closed_1(v1, v2, φ1, φ2)",
                    "p2 = ac_p_closed_2(v1, v2, φ1, φ2) + ac_p_closed_2(v1, v2, φ1, φ2) + ac_p_closed_1(v2, v3, φ2, φ3)",
                    "q2 = ac_q_closed_2(v1, v2, φ1, φ2) + ac_q_closed_2(v1, v2, φ1, φ2) + ac_q_closed_1(v2, v3, φ2, φ3)",
                    "p3 = ac_p_closed_2(v2, v3, φ2, φ3)",
                    "q3 = ac_q_closed_2(v2, v3, φ2, φ3)")
                    + System.lineSeparator();
            assertEquals(ref, writer.toString());
        }
    }

    @Test
    void writeDcSystemTest() throws IOException {
        Profiler profiler = Profiler.NO_OP;
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector(), profiler).get(0);
        EquationSystem equationSystem = DcEquationSystem.create(network, new DcEquationSystemCreationParameters(true, false, false, true), profiler);
        try (StringWriter writer = new StringWriter()) {
            equationSystem.write(writer);
            writer.flush();
            String ref = String.join(System.lineSeparator(),
                    "φ0 = φ0",
                    "p1 = dc_p_2(φ0, φ1) + dc_p_1(φ1, φ2) + dc_p_1(φ1, φ2)",
                    "p2 = dc_p_2(φ1, φ2) + dc_p_2(φ1, φ2) + dc_p_1(φ2, φ3)",
                    "p3 = dc_p_2(φ2, φ3)")
                    + System.lineSeparator();
            assertEquals(ref, writer.toString());
        }
    }
}
