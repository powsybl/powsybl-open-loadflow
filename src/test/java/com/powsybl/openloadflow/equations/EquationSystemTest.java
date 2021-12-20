/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.apache.commons.lang3.tuple.Pair;
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

    private final List<Equation<AcVariableType, AcEquationType>> equations = new ArrayList<>();
    private final List<EquationEventType> equationEventTypes = new ArrayList<>();
    private final List<EquationTermEventType> equationTermEventTypes = new ArrayList<>();

    private void clearEvents() {
        equations.clear();
        equationEventTypes.clear();
        equationTermEventTypes.clear();
    }

    @Test
    void test() {
        List<LfNetwork> lfNetworks = Networks.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector());
        LfNetwork network = lfNetworks.get(0);

        LfBus bus = network.getBus(0);
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new EquationSystem<>(true);
        equationSystem.addListener(new EquationSystemListener<>() {
            @Override
            public void onEquationChange(Equation<AcVariableType, AcEquationType> equation, EquationEventType eventType) {
                equations.add(equation);
                equationEventTypes.add(eventType);
            }

            @Override
            public void onEquationTermChange(EquationTerm<AcVariableType, AcEquationType> term, EquationTermEventType eventType) {
                equationTermEventTypes.add(eventType);
            }
        });
        assertTrue(equations.isEmpty());
        assertTrue(equationEventTypes.isEmpty());
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).addTerm(EquationTerm.createVariableTerm(bus, AcVariableType.BUS_V, equationSystem.getVariableSet()));
        assertEquals(1, equations.size());
        assertEquals(1, equationEventTypes.size());
        assertEquals(1, equationTermEventTypes.size());
        assertEquals(EquationEventType.EQUATION_CREATED, equationEventTypes.get(0));
        assertEquals(EquationTermEventType.EQUATION_TERM_ADDED, equationTermEventTypes.get(0));
        assertEquals(1, equationSystem.getSortedEquationsToSolve().size());

        clearEvents();
        equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).setActive(true);
        assertTrue(equations.isEmpty());
        assertTrue(equationEventTypes.isEmpty());

        equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).setActive(false);
        assertEquals(1, equations.size());
        assertEquals(1, equationEventTypes.size());
        assertEquals(EquationEventType.EQUATION_DEACTIVATED, equationEventTypes.get(0));
        assertTrue(equationSystem.getSortedEquationsToSolve().isEmpty());

        clearEvents();
        equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).setActive(true);
        assertEquals(1, equations.size());
        assertEquals(1, equationEventTypes.size());
        assertEquals(EquationEventType.EQUATION_ACTIVATED, equationEventTypes.get(0));
        assertEquals(1, equationSystem.getSortedEquationsToSolve().size());

        assertTrue(equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).isPresent());
        assertEquals(1, equationSystem.getEquations(ElementType.BUS, bus.getNum()).size());
        assertFalse(equationSystem.getEquation(99, AcEquationType.BUS_TARGET_V).isPresent());
        assertTrue(equationSystem.getEquations(ElementType.BUS, 99).isEmpty());

        assertEquals(1, equationSystem.getEquationTerms(ElementType.BUS, bus.getNum()).size());
        assertTrue(equationSystem.getEquationTerms(ElementType.BRANCH, 0).isEmpty());

        clearEvents();
        EquationTerm<AcVariableType, AcEquationType> equationTerm = EquationTerm.createVariableTerm(bus, AcVariableType.BUS_V, equationSystem.getVariableSet());
        bus.setCalculatedV(equationTerm);
        equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).addTerm(equationTerm);
        assertEquals(2, equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_V).getTerms().size());
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
        List<LfNetwork> lfNetworks = Networks.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector());
        LfNetwork network = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = AcEquationSystem.create(network);
        try (StringWriter writer = new StringWriter()) {
            equationSystem.write(writer);
            writer.flush();
            String ref = String.join(System.lineSeparator(),
                    "bus_target_v0 = v0",
                    "bus_target_φ0 = φ0",
                    "bus_target_p1 = ac_p_closed_2(v0, v1, φ0, φ1) + ac_p_closed_1(v1, v2, φ1, φ2) + ac_p_closed_1(v1, v2, φ1, φ2)",
                    "bus_target_q1 = ac_q_closed_2(v0, v1, φ0, φ1) + ac_q_closed_1(v1, v2, φ1, φ2) + ac_q_closed_1(v1, v2, φ1, φ2)",
                    "bus_target_p2 = ac_p_closed_2(v1, v2, φ1, φ2) + ac_p_closed_2(v1, v2, φ1, φ2) + ac_p_closed_1(v2, v3, φ2, φ3)",
                    "bus_target_q2 = ac_q_closed_2(v1, v2, φ1, φ2) + ac_q_closed_2(v1, v2, φ1, φ2) + ac_q_closed_1(v2, v3, φ2, φ3)",
                    "bus_target_p3 = ac_p_closed_2(v2, v3, φ2, φ3)",
                    "bus_target_q3 = ac_q_closed_2(v2, v3, φ2, φ3)")
                    + System.lineSeparator();
            assertEquals(ref, writer.toString());
        }
    }

    @Test
    void writeDcSystemTest() throws IOException {
        List<LfNetwork> lfNetworks = Networks.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector());
        LfNetwork network = lfNetworks.get(0);

        EquationSystem<DcVariableType, DcEquationType> equationSystem = DcEquationSystem.create(network, new DcEquationSystemCreationParameters(true, false, false, true));
        try (StringWriter writer = new StringWriter()) {
            equationSystem.write(writer);
            writer.flush();
            String ref = String.join(System.lineSeparator(),
                    "bus_target_φ0 = φ0",
                    "bus_target_p1 = dc_p_2(φ0, φ1) + dc_p_1(φ1, φ2) + dc_p_1(φ1, φ2)",
                    "bus_target_p2 = dc_p_2(φ1, φ2) + dc_p_2(φ1, φ2) + dc_p_1(φ2, φ3)",
                    "bus_target_p3 = dc_p_2(φ2, φ3)")
                    + System.lineSeparator();
            assertEquals(ref, writer.toString());
        }
    }

    @Test
    void findLargestMismatchesTest() {
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = AcEquationSystem.create(mainNetwork);
        NewtonRaphson.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());
        double[] targets = TargetVector.createArray(mainNetwork, equationSystem, AcloadFlowEngine::initTarget);
        double[] fx = equationSystem.createEquationVector();
        Vectors.minus(fx, targets);
        List<Pair<Equation<AcVariableType, AcEquationType>, Double>> largestMismatches = equationSystem.findLargestMismatches(fx, 3);
        assertEquals(3, largestMismatches.size());
        assertEquals(-7.397518453004565, largestMismatches.get(0).getValue(), 0);
        assertEquals(5.999135514403292, largestMismatches.get(1).getValue(), 0);
        assertEquals(1.9259062775721603, largestMismatches.get(2).getValue(), 0);
    }

    @Test
    void currentMagnitudeTest() {
        Network network = EurostagTutorialExample1Factory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = AcEquationSystem.create(mainNetwork);
        NewtonRaphson.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());
        LfBranch branch = mainNetwork.getBranchById("NHV1_NHV2_1");
        EquationTerm<AcVariableType, AcEquationType> i1 = (EquationTerm<AcVariableType, AcEquationType>) branch.getI1();
        EquationTerm<AcVariableType, AcEquationType> i2 = (EquationTerm<AcVariableType, AcEquationType>) branch.getI2();
        Variable<AcVariableType> v1var = equationSystem.getVariableSet().getVariable(branch.getBus1().getNum(), AcVariableType.BUS_V);
        Variable<AcVariableType> v2var = equationSystem.getVariableSet().getVariable(branch.getBus2().getNum(), AcVariableType.BUS_V);
        Variable<AcVariableType> ph1var = equationSystem.getVariableSet().getVariable(branch.getBus1().getNum(), AcVariableType.BUS_PHI);
        Variable<AcVariableType> ph2var = equationSystem.getVariableSet().getVariable(branch.getBus2().getNum(), AcVariableType.BUS_PHI);
        assertEquals(-43.120215, i1.der(v1var), 10E-6);
        assertEquals(43.398907, i1.der(v2var), 10E-6);
        assertEquals(3.945355, i1.der(ph1var), 10E-6);
        assertEquals(-3.945355, i1.der(ph2var), 10E-6);
        assertEquals(43.398907, i2.der(v1var), 10E-6);
        assertEquals(-43.120215, i2.der(v2var), 10E-6);
        assertEquals(-3.945355, i2.der(ph1var), 10E-6);
        assertEquals(3.945355, i2.der(ph2var), 10E-6);
    }

    @Test
    void currentMagnitudeOpenBranchSide2Test() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line1 = network.getLine("NHV1_NHV2_1");
        line1.getTerminal2().disconnect();

        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = AcEquationSystem.create(mainNetwork);
        NewtonRaphson.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());
        LfBranch branch = mainNetwork.getBranchById("NHV1_NHV2_1");
        EquationTerm<AcVariableType, AcEquationType> i1 = (EquationTerm<AcVariableType, AcEquationType>) branch.getI1();
        Variable<AcVariableType> v1var = equationSystem.getVariableSet().getVariable(branch.getBus1().getNum(), AcVariableType.BUS_V);
        Variable<AcVariableType> ph1var = equationSystem.getVariableSet().getVariable(branch.getBus1().getNum(), AcVariableType.BUS_PHI);
        assertEquals(0.559170, i1.der(v1var), 10E-6);
        assertThrows(IllegalStateException.class, () -> i1.der(ph1var));
    }

    @Test
    void currentMagnitudeOpenBranchSide1Test() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line1 = network.getLine("NHV1_NHV2_1");
        line1.getTerminal1().disconnect();
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = AcEquationSystem.create(mainNetwork);
        NewtonRaphson.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());
        LfBranch branch = mainNetwork.getBranchById("NHV1_NHV2_1");
        EquationTerm<AcVariableType, AcEquationType> i2 = (EquationTerm<AcVariableType, AcEquationType>) branch.getI2();
        Variable<AcVariableType> v2var = equationSystem.getVariableSet().getVariable(branch.getBus2().getNum(), AcVariableType.BUS_V);
        Variable<AcVariableType> ph2var = equationSystem.getVariableSet().getVariable(branch.getBus2().getNum(), AcVariableType.BUS_PHI);
        assertEquals(0.55917, i2.der(v2var), 10E-6);
        assertThrows(IllegalStateException.class, () -> i2.der(ph2var));
    }
}
