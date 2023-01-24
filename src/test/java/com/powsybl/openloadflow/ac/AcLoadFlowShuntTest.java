/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
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

    @BeforeEach
    void setUp() {
        network = ShuntNetworkFactory.create();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        bus3 = network.getBusBreakerView().getBus("b3");
        l1 = network.getLine("l1");
        l2 = network.getLine("l2");
        shunt = network.getShuntCompensator("SHUNT");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
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
        parameters.setShuntCompensatorVoltageControlOn(true);
        shunt.setSectionCount(0);
        shunt.setVoltageRegulatorOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390.930, bus3);
        assertEquals(1, shunt.getSectionCount());
    }

    @Test
    void testRemoteVoltageControl() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        ShuntCompensator shuntCompensator2 = network.getShuntCompensator("SHUNT2");
        shuntCompensator2.setVoltageRegulatorOn(false);
        ShuntCompensator shuntCompensator3 = network.getShuntCompensator("SHUNT3");
        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(399.602, network.getBusBreakerView().getBus("b4"));
        assertEquals(0, shuntCompensator2.getSectionCount());
        assertEquals(27, shuntCompensator3.getSectionCount());
    }

    @Test
    void testLocalSharedVoltageControl() {
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

        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(393.308, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(1, shunt2.getSectionCount());
    }

    @Test
    void testLocalSharedVoltageControl2() {
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

        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(391.640, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(2, shunt2.getSectionCount());
    }

    @Test
    void testLocalVoltageControl2() {
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

        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(393.308, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(1, shunt2.getSectionCount());
    }

    @Test
    void testLocalVoltageControl3() {
        // in that test case, we test two shunts connected to the same bus, but with just one in voltage regulation
        network.getShuntCompensator("SHUNT").remove();
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(10)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(400)
                .setTargetDeadband(5.0)
                .newLinearModel()
                .setMaximumSectionCount(10)
                .setBPerSection(1E-3)
                .setGPerSection(0.0)
                .add()
                .add();

        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(400.600, bus3);
        assertEquals(5, shunt2.getSectionCount());
    }

    @Test
    void testSharedRemoteVoltageControl() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        parameters.setShuntCompensatorVoltageControlOn(true);
        ShuntCompensator shuntCompensator2 = network.getShuntCompensator("SHUNT2");
        ShuntCompensator shuntCompensator3 = network.getShuntCompensator("SHUNT3");
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(399.819, network.getBusBreakerView().getBus("b4"));
        assertEquals(13, shuntCompensator2.getSectionCount());
        assertEquals(13, shuntCompensator3.getSectionCount());
    }

    @Test
    void testNoShuntVoltageControl() {
        parameters.setShuntCompensatorVoltageControlOn(true);
        shunt.setRegulatingTerminal(network.getGenerator("g1").getTerminal());
        shunt.setSectionCount(0);
        shunt.setVoltageRegulatorOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(388.581, bus3);
        assertEquals(0, shunt.getSectionCount());
    }

    @Test
    void testNoShuntVoltageControl2() {
        parameters.setShuntCompensatorVoltageControlOn(true);
        shunt.setSectionCount(0);
        shunt.setVoltageRegulatorOn(true);
        shunt.setRegulatingTerminal(network.getLoad("ld1").getTerminal());
        network.getLoad("ld1").getTerminal().disconnect();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(389.999, bus3);
        assertEquals(0, shunt.getSectionCount());
    }

    @Test
    void testNoShuntVoltageControl3() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        TwoWindingsTransformer twt = network.getTwoWindingsTransformer("tr1");
        twt.newRatioTapChanger()
                .setTargetDeadband(0)
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setTargetV(400)
                .setRegulationTerminal(network.getLoad("l4").getTerminal())
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .add();
        parameters.setShuntCompensatorVoltageControlOn(true);
        parameters.setTransformerVoltageControlOn(true);
        ShuntCompensator shuntCompensator2 = network.getShuntCompensator("SHUNT2");
        ShuntCompensator shuntCompensator3 = network.getShuntCompensator("SHUNT3");
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(407.978, network.getBusBreakerView().getBus("b4"));
        assertEquals(0, shuntCompensator2.getSectionCount());
        assertEquals(0, shuntCompensator3.getSectionCount());
    }

    @Test
    void testUnsupportedSharedVoltageControl() {
        // in that test case, we test two shunts connected to the same bus, both are in voltage regulation
        // but with a different regulating terminal.
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal2())
                .setTargetV(405)
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

        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(391.640, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(2, shunt2.getSectionCount());
    }

    @Test
    void testAdmittanceShift() {
        // Test with G component on shunt
        network.getShuntCompensator("SHUNT").getTerminal().disconnect();
        ShuntCompensator shuntG = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(0)
                .setVoltageRegulatorOn(true)
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(1e-3)
                .setG(1e-5)
                .endSection()
                .beginSection()
                .setB(3e-3)
                .setG(3e-5)
                .endSection()
                .add()
                .add();

        shuntG.setSectionCount(1);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertActivePowerEquals(102.749, l1.getTerminal(Branch.Side.ONE));
        assertActivePowerEquals(-102.679, l1.getTerminal(Branch.Side.TWO));
        assertReactivePowerEquals(-2.154, l1.getTerminal(Branch.Side.ONE));
        assertReactivePowerEquals(2.362, l1.getTerminal(Branch.Side.TWO));
        assertActivePowerEquals(-1.528, l2.getTerminal(Branch.Side.ONE));
        assertActivePowerEquals(1.681, l2.getTerminal(Branch.Side.TWO));
        assertReactivePowerEquals(152.82, l2.getTerminal(Branch.Side.ONE));
        assertReactivePowerEquals(-152.362, l2.getTerminal(Branch.Side.TWO));
        assertActivePowerEquals(1.528, shuntG.getTerminal());
        assertReactivePowerEquals(-152.82, shuntG.getTerminal());

        shuntG.setSectionCount(2);
        LoadFlowResult result3 = loadFlowRunner.run(network, parameters);
        assertTrue(result3.isOk());
        assertActivePowerEquals(4.697, shuntG.getTerminal());
        assertReactivePowerEquals(-469.698, shuntG.getTerminal());
        assertVoltageEquals(395.684, shuntG.getTerminal().getBusView().getBus());

        shuntG.setSectionCount(0);
        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result4 = loadFlowRunner.run(network, parameters);
        assertTrue(result4.isOk());
        assertVoltageEquals(390.93, shuntG.getTerminal().getBusView().getBus());
        assertEquals(1, shuntG.getSectionCount());
        assertActivePowerEquals(1.528, shuntG.getTerminal());
        assertReactivePowerEquals(-152.826, shuntG.getTerminal());
    }

    @Test
    void testIncrementalVoltageControl() {
        parameters.setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        shunt.setSectionCount(0);
        shunt.setVoltageRegulatorOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390.930, bus3);
        assertEquals(1, shunt.getSectionCount());

        shunt.setSectionCount(0);
        shunt.setTargetDeadband(10);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertVoltageEquals(388.581, bus3);
        assertEquals(0, shunt.getSectionCount());
    }

    @Test
    void testIncrementalVoltageRemote() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        ShuntCompensator shuntCompensator2 = network.getShuntCompensator("SHUNT2");
        shuntCompensator2.setVoltageRegulatorOn(false);
        ShuntCompensator shuntCompensator3 = network.getShuntCompensator("SHUNT3");
        parameters.setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(402.011, network.getBusBreakerView().getBus("b4"));
        assertEquals(0, shuntCompensator2.getSectionCount());
        assertEquals(19, shuntCompensator3.getSectionCount());

        shuntCompensator3.setTargetDeadband(0.1);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertVoltageEquals(399.602, network.getBusBreakerView().getBus("b4"));
        assertEquals(0, shuntCompensator2.getSectionCount());
        assertEquals(27, shuntCompensator3.getSectionCount());
    }

    @Test
    void testSharedIncrementalVoltage() {
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

        parameters.setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390.931, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(0, shunt2.getSectionCount());
    }

    @Test
    void testSharedIncrementalVoltageRemote() {
        Network network = VoltageControlNetworkFactory.createWithShuntSharedRemoteControl();
        ShuntCompensator shuntCompensator2 = network.getShuntCompensator("SHUNT2");
        ShuntCompensator shuntCompensator3 = network.getShuntCompensator("SHUNT3");
        parameters.setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(401.967, network.getBusBreakerView().getBus("b4"));
        assertEquals(10, shuntCompensator2.getSectionCount());
        assertEquals(9, shuntCompensator3.getSectionCount());
    }

    @Test
    void testOppositeSignBIncremental() {
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(1)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(-1e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(-3e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();

        parameters.setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390.931, bus3);
        assertEquals(1, shunt.getSectionCount());
        assertEquals(0, shunt2.getSectionCount());
    }

    @Test
    void testNonLinearControlersIncremental() {
        shunt.setVoltageRegulatorOn(false);
        ShuntCompensator shunt2 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT2")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(1)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(-3e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(4e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();
        ShuntCompensator shunt3 = network.getVoltageLevel("vl3").newShuntCompensator()
                .setId("SHUNT3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setSectionCount(1)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal1())
                .setTargetV(393)
                .setTargetDeadband(5.0)
                .newNonLinearModel()
                .beginSection()
                .setB(-3e-3)
                .setG(0.0)
                .endSection()
                .beginSection()
                .setB(4e-3)
                .setG(0.)
                .endSection()
                .add()
                .add();

        parameters.setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(390.930, bus3);
        assertEquals(0, shunt.getSectionCount());
        assertEquals(2, shunt2.getSectionCount());
        assertEquals(1, shunt3.getSectionCount());

        shunt2.setSectionCount(1);
        shunt2.setTargetV(408.0);
        shunt3.setSectionCount(1);
        shunt3.setTargetV(408.0);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertVoltageEquals(408.150, bus3);
        assertEquals(0, shunt.getSectionCount());
        assertEquals(2, shunt2.getSectionCount());
        assertEquals(2, shunt3.getSectionCount());
    }
}
