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
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.BusVoltageEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1IntensityMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2IntensityMagnitudeEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
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
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector()).get(0);
        LfBus bus = network.getBus(0);
        EquationSystem equationSystem = new EquationSystem(network, true);
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
    void findLargestMismatchesTest() {
        Network network = EurostagTutorialExample1Factory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new FirstSlackBusSelector()).get(0);
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork);
        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());
        double[] targets = equationSystem.createTargetVector();
        equationSystem.updateEquations(x);
        double[] fx = equationSystem.createEquationVector();
        Vectors.minus(fx, targets);
        List<Pair<Equation, Double>> largestMismatches = equationSystem.findLargestMismatches(fx, 3);
        assertEquals(3, largestMismatches.size());
        assertEquals(-7.397518453004565, largestMismatches.get(0).getValue(), 0);
        assertEquals(5.999135514403292, largestMismatches.get(1).getValue(), 0);
        assertEquals(1.9259062775721603, largestMismatches.get(2).getValue(), 0);
    }

    @Test
    void intensityMagnitudeTest() {
        Network network = EurostagTutorialExample1Factory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new FirstSlackBusSelector()).get(0);
        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, variableSet);
        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());
        equationSystem.updateEquations(x);
        LfBranch branch = lfNetwork.getBranchById("NHV1_NHV2_1");
        EquationTerm i1 = equationSystem.getEquation(branch.getBus1().getNum(), EquationType.BUS_I).orElse(null).getTerms().get(1);
        EquationTerm i2 = equationSystem.getEquation(branch.getBus2().getNum(), EquationType.BUS_I).orElse(null).getTerms().get(0);
        Variable v1var = variableSet.getVariable(branch.getBus1().getNum(), VariableType.BUS_V);
        Variable v2var = variableSet.getVariable(branch.getBus2().getNum(), VariableType.BUS_V);
        Variable ph1var = variableSet.getVariable(branch.getBus1().getNum(), VariableType.BUS_PHI);
        Variable ph2var = variableSet.getVariable(branch.getBus2().getNum(), VariableType.BUS_PHI);
        assertEquals(-24895.468, i1.der(v1var), 10E-3);
        assertEquals(25056.371, i1.der(v2var), 10E-3);
        assertEquals(2277.852, i1.der(ph1var), 10E-3);
        assertEquals(-2277.852, i1.der(ph2var), 10E-3);
        assertEquals(25056.371, i2.der(v1var), 10E-3);
        assertEquals(-24895.468, i2.der(v2var), 10E-3);
        assertEquals(-2277.852, i2.der(ph1var), 10E-3);
        assertEquals(2277.852, i2.der(ph2var), 10E-3);
    }

    @Test
    void intensityMagnitudeOpenBranchSide2Test() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line1 = network.getLine("NHV1_NHV2_1");
        line1.getTerminal2().disconnect();
        LfNetwork lfNetwork = LfNetwork.load(network, new FirstSlackBusSelector()).get(0);
        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, variableSet);
        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());
        equationSystem.updateEquations(x);
        LfBranch branch = lfNetwork.getBranchById("NHV1_NHV2_1");
        EquationTerm i1 = equationSystem.getEquation(branch.getBus1().getNum(), EquationType.BUS_I).orElse(null).getTerms().stream().filter(OpenBranchSide2IntensityMagnitudeEquationTerm.class::isInstance).findAny().get();
        Variable v1var = variableSet.getVariable(branch.getBus1().getNum(), VariableType.BUS_V);
        Variable ph1var = variableSet.getVariable(branch.getBus1().getNum(), VariableType.BUS_PHI);
        assertEquals(322.837, i1.der(v1var), 10E-3);
        assertThrows(IllegalStateException.class, () -> i1.der(ph1var));
    }

    @Test
    void intensityMagnitudeOpenBranchSide1Test() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line1 = network.getLine("NHV1_NHV2_1");
        line1.getTerminal1().disconnect();
        LfNetwork lfNetwork = LfNetwork.load(network, new FirstSlackBusSelector()).get(0);
        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, variableSet);
        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());
        equationSystem.updateEquations(x);
        LfBranch branch = lfNetwork.getBranchById("NHV1_NHV2_1");
        EquationTerm i2 = equationSystem.getEquation(branch.getBus2().getNum(), EquationType.BUS_I).orElse(null).getTerms().stream().filter(OpenBranchSide1IntensityMagnitudeEquationTerm.class::isInstance).findAny().get();
        Variable v2var = variableSet.getVariable(branch.getBus2().getNum(), VariableType.BUS_V);
        Variable ph2var = variableSet.getVariable(branch.getBus2().getNum(), VariableType.BUS_PHI);
        assertEquals(322.837, i2.der(v2var), 10E-3);
        assertThrows(IllegalStateException.class, () -> i2.der(ph2var));
    }
}
