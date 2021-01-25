/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.BusVoltageEquationTerm;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationSystemTest {

    private final List<Equation> equations = new ArrayList<>();
    private final List<EquationEventType> eventTypes = new ArrayList<>();

    private LoadFlowTestTools loadFlowTestToolsSvcVoltage;
    private LfBus lfBus2Voltage;
    private LoadFlowTestTools loadFlowTestToolsSvcVoltageWithSlope;
    private LfBus lfBus2VoltageWithSlope;

    public EquationSystemTest() {
        loadFlowTestToolsSvcVoltage = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE).build());
        lfBus2Voltage = loadFlowTestToolsSvcVoltage.getLfNetwork().getBusById("vl2_0");
        loadFlowTestToolsSvcVoltageWithSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().build());
        lfBus2VoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl2_0");
    }

    private void clearEvents() {
        equations.clear();
        eventTypes.clear();
    }

    @Test
    void test() {
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector()).get(0);
        LfBus bus = network.getBus(0);
        EquationSystem equationSystem = new EquationSystem(network, true);
        equationSystem.addListener((equation, eventType) -> {
            equations.add(equation);
            eventTypes.add(eventType);
        });
        VariableSet variableSet = new VariableSet();
        assertTrue(equations.isEmpty());
        assertTrue(eventTypes.isEmpty());
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        equationSystem.createEquation(bus.getNum(), EquationType.BUS_V).addTerm(new BusPhaseEquationTerm(bus, variableSet));
        assertEquals(2, equations.size());
        assertEquals(2, eventTypes.size());
        assertEquals(EquationEventType.EQUATION_CREATED, eventTypes.get(0));
        assertEquals(EquationEventType.EQUATION_UPDATED, eventTypes.get(1));
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
        assertEquals(1, equations.size());
        assertEquals(1, eventTypes.size());
        assertEquals(EquationEventType.EQUATION_UPDATED, eventTypes.get(0));

        clearEvents();
        equationTerm.setActive(false);
        assertEquals(1, equations.size());
        assertEquals(1, eventTypes.size());
        assertEquals(EquationEventType.EQUATION_UPDATED, eventTypes.get(0));
    }

    @Test
    void writeAcSystemTest() throws IOException {
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector()).get(0);
        EquationSystem equationSystem = AcEquationSystem.create(network);
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
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector()).get(0);
        EquationSystem equationSystem = DcEquationSystem.create(network, new DcEquationSystemCreationParameters(true, false, false, true));
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

    @Test
    void updateActiveEquationVTest() {
        EquationSystem equationSystemVoltage = loadFlowTestToolsSvcVoltage.getEquationSystem();
        Optional<Equation> equationBusV = equationSystemVoltage.getEquation(lfBus2Voltage.getNum(), EquationType.BUS_V);
        EquationSystem equationSystemVoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem();
        Optional<Equation> equationBusVLQ = equationSystemVoltageWithSlope.getEquation(lfBus2VoltageWithSlope.getNum(), EquationType.BUS_VLQ);

        // 1 - should update active field on equation with type EquationType.BUS_V
        equationSystemVoltage.updateActiveEquationV(lfBus2Voltage.getNum(), false);
        assertEquals(false, equationBusV.get().isActive());
        equationSystemVoltage.updateActiveEquationV(lfBus2Voltage.getNum(), true);
        assertEquals(true, equationBusV.get().isActive());

        // 2 - should update active field on equation with type EquationType.BUS_VLQ
        equationSystemVoltageWithSlope.updateActiveEquationV(lfBus2VoltageWithSlope.getNum(), false);
        assertEquals(false, equationBusVLQ.get().isActive());
        equationSystemVoltageWithSlope.updateActiveEquationV(lfBus2VoltageWithSlope.getNum(), true);
        assertEquals(true, equationBusVLQ.get().isActive());

        // 3 - should raise PowsyblException without equation with type EquationType.BUS_V or EquationType.BUS_VLQ
        equationSystemVoltage.removeEquation(lfBus2Voltage.getNum(), EquationType.BUS_V);
        int busNum = lfBus2Voltage.getNum();
        assertThrows(PowsyblException.class, () -> equationSystemVoltage.updateActiveEquationV(busNum, true));
    }

    @Test
    void equationSystemTest() {
        EquationSystem equationSystem = new EquationSystem(loadFlowTestToolsSvcVoltage.getLfNetwork());
        assertEquals(loadFlowTestToolsSvcVoltage.getLfNetwork(), equationSystem.getNetwork(), "should value network attribute in constructor");
    }

    @Test
    void getEquationTermsTest() {
        EquationSystem equationSystem = new EquationSystem(loadFlowTestToolsSvcVoltage.getLfNetwork(), false);
        int busNum = lfBus2Voltage.getNum();
        assertThrows(PowsyblException.class, () -> equationSystem.getEquationTerms(SubjectType.BUS, busNum), "should not return equationTerms with indexTerms set to false");
    }

    @Test
    void updateEquationVectorTest() {
        EquationSystem equationSystemVoltage = loadFlowTestToolsSvcVoltage.getEquationSystem();
        assertThrows(IllegalArgumentException.class, () -> equationSystemVoltage.updateEquationVector(new double[]{0, 0, 0}), "should not update terms of equations with invalid data");
    }
}
