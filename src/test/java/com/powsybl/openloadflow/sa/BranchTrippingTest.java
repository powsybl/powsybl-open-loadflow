/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FictitiousSwitchFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class BranchTrippingTest {

    @Test
    void testLineTripping() {
        Network network = OpenSecurityAnalysisTest.createNetwork();

        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        new LfBranchTripping("L1").traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "C");
        checkTerminalIds(terminalsToDisconnect, "BBS1", "L1");

        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        new LfBranchTripping("L2").traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminalIds(terminalsToDisconnect, "L2");
    }

    @Test
    void testUnknownLineTripping() {
        Network network = OpenSecurityAnalysisTest.createNetwork();
        AbstractTrippingTask trippingTaskUnknownBranch = new LfBranchTripping("L9");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        Exception unknownBranch = assertThrows(PowsyblException.class,
            () -> trippingTaskUnknownBranch.traverse(network, null, switchesToOpen, terminalsToDisconnect));
        assertEquals("Branch 'L9' not found", unknownBranch.getMessage());
    }

    @Test
    void testUnconnectedVoltageLevel() {
        Network network = OpenSecurityAnalysisTest.createNetwork();
        AbstractTrippingTask trippingTaskUnconnectedVl = new LfBranchTripping("L1", "VL3");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        Exception unknownVl = assertThrows(PowsyblException.class,
            () -> trippingTaskUnconnectedVl.traverse(network, null, switchesToOpen, terminalsToDisconnect));
        assertEquals("VoltageLevel 'VL3' not connected to branch 'L1'", unknownVl.getMessage());
    }

    @Test
    void testVoltageLevelFilter() {
        Network network = OpenSecurityAnalysisTest.createNetwork();

        AbstractTrippingTask trippingTaskVl1 = new LfBranchTripping("L1", "VL1");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        trippingTaskVl1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "C");
        checkTerminalIds(terminalsToDisconnect, "BBS1", "L1");

        AbstractTrippingTask trippingTaskVl2 = new LfBranchTripping("L1", "VL2");
        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        trippingTaskVl2.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminalIds(terminalsToDisconnect, "L1");
    }

    @Test
    void testDisconnectorBeforeMultipleSwitches() {
        Network network = FictitiousSwitchFactory.create();

        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        new LfBranchTripping("CJ").traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "CJ");
    }

    @Test
    void testSwitchBeforeOpenedDisconnector() {
        Network network = FictitiousSwitchFactory.create();

        // Close switches to traverse the voltage level further
        network.getSwitch("BB").setOpen(false);
        network.getSwitch("AZ").setOpen(false);

        // First without opening disconnector
        LfBranchTripping lbt1 = new LfBranchTripping("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbt1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BL", "BJ");
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "P", "CJ");

        // Then with the opened disconnector
        network.getSwitch("AH").setOpen(true);
        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        lbt1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BL");
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "P", "CJ");
    }

    @Test
    void testOpenedSwitches() {
        // Testing disconnector and breaker opened just after contingency
        Network network = FictitiousSwitchFactory.create();
        network.getSwitch("L").setOpen(true); // breaker at C side of line CJ
        network.getSwitch("BF").setOpen(true);  // disconnector at N side of line CJ

        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        new LfBranchTripping("CJ").traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminalIds(terminalsToDisconnect, "CJ");
    }

    @Test
    void testEndNodeAfterSwitch() {
        Network network = FictitiousSwitchFactory.create();

        // Close switches to traverse the voltage level until encountering generator/loads
        network.getSwitch("BB").setOpen(false); // BB fictitious breaker
        network.getSwitch("AZ").setOpen(false); // AZ disconnector to BBS1
        network.getSwitch("AV").setOpen(false); // AR disconnector to generator CD

        // Adding breakers between two loads to simulate the case of a branching of two switches at an end node
        network.getVoltageLevel("N").getNodeBreakerView().newSwitch()
            .setId("ZW")
            .setName("ZX")
            .setKind(SwitchKind.BREAKER)
            .setRetained(false)
            .setOpen(true)
            .setFictitious(true)
            .setNode1(22)
            .setNode2(16)
            .add();
        network.getVoltageLevel("N").getNodeBreakerView().newSwitch()
            .setId("ZY")
            .setName("ZZ")
            .setKind(SwitchKind.BREAKER)
            .setRetained(false)
            .setOpen(false)
            .setFictitious(true)
            .setNode1(20)
            .setNode2(18)
            .add();

        LfBranchTripping lbt1 = new LfBranchTripping("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbt1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BJ", "BL", "BV", "BX");
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "P", "O", "CJ");

        // Adding an internal connection and open the ZW switch
        network.getSwitch("ZY").setOpen(true);
        network.getVoltageLevel("N").getNodeBreakerView().newInternalConnection()
            .setNode1(20)
            .setNode2(18)
            .add();
        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        lbt1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BJ", "BL", "BV", "BX");
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "P", "O", "CJ");
    }

    @Test
    void testInternalConnection() {
        Network network = FictitiousSwitchFactory.create();

        // Adding internal connections
        network.getVoltageLevel("N").getNodeBreakerView().newInternalConnection()
            .setNode1(3)
            .setNode2(9)
            .add();
        network.getVoltageLevel("C").getNodeBreakerView().newInternalConnection()
            .setNode1(2)
            .setNode2(3)
            .add();

        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        new LfBranchTripping("CJ").traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BL");
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "CJ");
    }

    @Test
    void testStopAtStartEdges() {
        Network network = FictitiousSwitchFactory.create();

        // Adding edges after CJ line
        network.getVoltageLevel("N").getNodeBreakerView().newBreaker()
            .setId("ZY")
            .setName("ZZ")
            .setRetained(false)
            .setOpen(false)
            .setFictitious(false)
            .setNode1(5)
            .setNode2(9)
            .add();
        network.getVoltageLevel("C").getNodeBreakerView().newInternalConnection()
            .setNode1(1)
            .setNode2(4)
            .add();

        LfBranchTripping lbt1 = new LfBranchTripping("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbt1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "ZY");
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "CJ");

        network.getSwitch("ZY").setOpen(true);
        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        lbt1.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminalIds(terminalsToDisconnect, "D", "CI", "CJ");
    }

    @Test
    void testEurostagNetwork() {
        Network network = EurostagTutorialExample1Factory.create();

        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();

        LfBranchTripping trippingTask = new LfBranchTripping("NHV1_NHV2_1");
        trippingTask.traverse(network, null, switchesToOpen, terminalsToDisconnect);

        assertTrue(switchesToOpen.isEmpty());
        assertEquals(2, terminalsToDisconnect.size());
        checkTerminalStrings(terminalsToDisconnect, "BusTerminal[NHV1]", "BusTerminal[NHV2]");

        Line line = network.getLine("NHV1_NHV2_1");
        line.getTerminal1().disconnect();

        terminalsToDisconnect.clear();
        trippingTask.traverse(network, null, switchesToOpen, terminalsToDisconnect);

        assertTrue(switchesToOpen.isEmpty());
        assertEquals(1, terminalsToDisconnect.size());
        checkTerminalStrings(terminalsToDisconnect, "BusTerminal[NHV2]");
    }

    private static void checkSwitches(Set<Switch> switches, String... sId) {
        assertEquals(new HashSet<>(Arrays.asList(sId)), switches.stream().map(Switch::getId).collect(Collectors.toSet()));
    }

    private static void checkTerminalIds(Set<Terminal> terminals, String... tId) {
        assertEquals(new HashSet<>(Arrays.asList(tId)), terminals.stream().map(t -> t.getConnectable().getId()).collect(Collectors.toSet()));
    }

    private static void checkTerminalStrings(Set<Terminal> terminals, String... strings) {
        assertEquals(new HashSet<>(Arrays.asList(strings)), terminals.stream().map(Object::toString).collect(Collectors.toSet()));
    }

}
