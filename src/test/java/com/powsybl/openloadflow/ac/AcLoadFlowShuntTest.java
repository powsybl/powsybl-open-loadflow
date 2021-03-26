/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shunt test case.
 *
 * g1        ld1
 * |          |
 * b1---------b2
 *      l1    |
 *           shunt
 *
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class AcLoadFlowShuntTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Line l1;
    private Line l2;
    private ShuntCompensator shunt;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private Network createNetwork() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("S2")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(101)
                .setQ0(150)
                .add();
        VoltageLevel vl3 = s2.newVoltageLevel()
                .setId("vl3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        shunt = vl3.newShuntCompensator()
                .setId("SHUNT")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();
        l1 = network.newLine()
                .setId("l1")
                .setVoltageLevel1("vl1")
                .setBus1("b1")
                .setVoltageLevel2("vl2")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        l2 = network.newLine()
                .setId("l2")
                .setVoltageLevel1("vl3")
                .setBus1("b3")
                .setVoltageLevel2("vl2")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(false)
                .setDistributedSlack(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void testBaseCase() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390, bus1);
        assertVoltageEquals(388.581, bus2);
        assertVoltageEquals(388.581, bus3);
    }

    @Test
    void testShuntSectionOne() {
        shunt.setSectionCount(1);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390, bus1);
        assertVoltageEquals(389.758, bus2);
        assertVoltageEquals(390.93051, bus3);
    }

    @Test
    void testShuntSectionTwo() {
        shunt.setSectionCount(2);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390, bus1);
        assertVoltageEquals(392.149468, bus2);
        assertVoltageEquals(395.709, bus3);
    }

    @Test
    void testVoltageControl() {
        parameters.setSimulShunt(true);
        shunt.setSectionCount(0);
        shunt.setVoltageRegulatorOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390.930, bus3);
        assertEquals(1, shunt.getSectionCount());
    }

    @Test
    void testRemoteVoltageControl() {
        parameters.setSimulShunt(true);
        shunt.setSectionCount(0)
            .setRegulatingTerminal(network.getLoad("ld1").getTerminal())
            .setTargetV(392.)
            .setVoltageRegulatorOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(395.709, bus3);
        assertEquals(2, shunt.getSectionCount());
    }

    @Test
    void testLocalSharedVoltageControl() {
        network = createNetwork();
        shunt.setSectionCount(2);
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();

        parameters.setSimulShunt(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        // assertTrue(result.isOk());
        // assertVoltageEquals(393.308, bus3);
        // assertEquals(1, shunt.getSectionCount());
        // assertEquals(1, shunt2.getSectionCount());
    }

    @Test
    void testLocalSharedVoltageControl2() {
        network = createNetwork();
        // in that test case, we test two shunts connected to the same bus, both are in voltage regulation
        // we decrease the b per section of shunt2
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-4)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-4)
                .setG(0.)
                .endSection()
                .add()
                .add();

        parameters.setSimulShunt(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        //FIXME
        // assertTrue(result.isOk());
        // assertVoltageEquals(391.640, bus3);
        // assertEquals(1, shunt.getSectionCount());
        // assertEquals(2, shunt2.getSectionCount());
    }

    @Test
    void testLocalVoltageControl2() {
        network = createNetwork();
        // in that test case, we test two shunts connected to the same bus, but with just one in voltage regulation
        shunt.setSectionCount(2);
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();

        parameters.setSimulShunt(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(393.308, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(1, shunt2.getSectionCount());
    }
}
