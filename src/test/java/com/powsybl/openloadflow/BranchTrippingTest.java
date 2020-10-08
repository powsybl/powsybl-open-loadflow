/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FictitiousSwitchFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class BranchTrippingTest {

    @Test
    void testLineTripping() {
        Network network = OpenSecurityAnalysisTest.createNetwork();

        BranchContingency lbc1 = new LfBranchContingency("L1");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbc1.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "C");
        checkTerminals(terminalsToDisconnect, "BBS1");

        BranchContingency lbc2 = new LfBranchContingency("L2");
        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        lbc2.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminals(terminalsToDisconnect);
    }

    @Test
    void testUnknownLineTripping() {
        Network network = OpenSecurityAnalysisTest.createNetwork();
        BranchContingency lbc3 = new LfBranchContingency("L9");
        AbstractTrippingTask trippingTaskUnknownBranch = lbc3.toTask();
        Exception unknownBranch = assertThrows(PowsyblException.class,
            () -> trippingTaskUnknownBranch.traverse(network, null, new HashSet<>(), new HashSet<>()));
        assertEquals("Branch 'L9' not found", unknownBranch.getMessage());
    }

    @Test
    void testUnconnectedVoltageLevel() {
        Network network = OpenSecurityAnalysisTest.createNetwork();
        AbstractTrippingTask trippingTaskUnconnectedVl = new LfBranchTripping("L1", "VL3");
        Exception unknownVl = assertThrows(PowsyblException.class,
            () -> trippingTaskUnconnectedVl.traverse(network, null, new HashSet<>(), new HashSet<>()));
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
        checkTerminals(terminalsToDisconnect, "BBS1");

        AbstractTrippingTask trippingTaskVl2 = new LfBranchTripping("L1", "VL2");
        switchesToOpen.clear();
        terminalsToDisconnect.clear();
        trippingTaskVl2.traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminals(terminalsToDisconnect);
    }

    @Test
    void testEurostagNetwork() {
        Network network = EurostagTutorialExample1Factory.create();

        LfBranchContingency lbc = new LfBranchContingency("NHV1_NHV2_1");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();

        LfBranchTripping trippingTask = lbc.toTask();
        Exception e = assertThrows(PowsyblException.class,
            () -> trippingTask.traverse(network, null, switchesToOpen, terminalsToDisconnect));
        assertEquals("Traverser yet to implement for bus breaker view", e.getMessage());
    }

    @Test
    void testDisconnectorBeforeMultipleSwitches() {
        Network network = FictitiousSwitchFactory.create();

        BranchContingency lbc1 = new LfBranchContingency("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbc1.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminals(terminalsToDisconnect, "D", "CI");
    }

    @Test
    void testSwitchBeforeOpenedDisconnector() {
        Network network = FictitiousSwitchFactory.create();

        // Close switches to traverse the voltage level further
        network.getSwitch("BB").setOpen(false);
        network.getSwitch("AZ").setOpen(false);
        network.getSwitch("AH").setOpen(true); // the opened disconnector

        BranchContingency lbc1 = new LfBranchContingency("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbc1.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BL");
        checkTerminals(terminalsToDisconnect, "D", "CI", "P");
    }

    @Test
    void testOpenedSwitches() {
        // Testing disconnector and breaker opened just after contingency
        Network network = FictitiousSwitchFactory.create();
        network.getSwitch("L").setOpen(true); // breaker at C side of line CJ
        network.getSwitch("BF").setOpen(true);  // disconnector at N side of line CJ

        BranchContingency lbc1 = new LfBranchContingency("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbc1.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen);
        checkTerminals(terminalsToDisconnect);
    }

    @Test
    void testEndNodeAfterSwitch() {
        Network network = FictitiousSwitchFactory.create();

        // Close switches to traverse the voltage level until encountering generator/loads
        network.getSwitch("BB").setOpen(false); // BB fictitious breaker
        network.getSwitch("AZ").setOpen(false); // AZ disconnector to BBS1
        network.getSwitch("AV").setOpen(false); // AR disconnector to generator CD

        // Adding a breaker between two loads to simulate the case of a branching of two switches at an end node
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

        BranchContingency lbc1 = new LfBranchContingency("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbc1.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BJ", "BL", "BV", "BX");
        checkTerminals(terminalsToDisconnect, "D", "CI", "P", "O");
    }

    @Test
    void testInternalConnection() {
        Network network = FictitiousSwitchFactory.create();

        // Adding an internal connection
        network.getVoltageLevel("N").getNodeBreakerView().newInternalConnection()
            .setNode1(3)
            .setNode2(9)
            .add();

        BranchContingency lbc1 = new LfBranchContingency("CJ");
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        lbc1.toTask().traverse(network, null, switchesToOpen, terminalsToDisconnect);
        checkSwitches(switchesToOpen, "BL");
        checkTerminals(terminalsToDisconnect, "D", "CI");
    }

    private static void checkSwitches(Set<Switch> switches, String... sId) {
        assertEquals(new HashSet<>(Arrays.asList(sId)), switches.stream().map(Switch::getId).collect(Collectors.toSet()));
    }

    private static void checkTerminals(Set<Terminal> terminals, String... tId) {
        assertEquals(new HashSet<>(Arrays.asList(tId)), terminals.stream().map(t -> t.getConnectable().getId()).collect(Collectors.toSet()));
    }

}
