/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SVC test case.
 *
 * g1        ld1
 * |          |
 * b1---------b2
 *      l1    |
 *           svc1
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowSvcTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Line l1;
    private StaticVarCompensator svc1;

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
        svc1 = vl2.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
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
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(388.581824, bus2);
        assertAngleEquals(-0.057845, bus2);
        assertActivePowerEquals(101.216, l1.getTerminal1());
        assertReactivePowerEquals(150.649, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-150, l1.getTerminal2());
        assertTrue(Double.isNaN(svc1.getTerminal().getP()));
        assertTrue(Double.isNaN(svc1.getTerminal().getQ()));

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertActivePowerEquals(103.562, l1.getTerminal1());
        assertReactivePowerEquals(615.582, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-607.897, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(457.896, svc1.getTerminal());
    }

    @Test
    void shouldReachReactiveMaxLimit() {
        svc1.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-svc1.getBmin() * svc1.getVoltageSetpoint() * svc1.getVoltageSetpoint(), svc1.getTerminal()); // min reactive limit has been correctly reached
    }

    @Test
    void testSvcWithSlope() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(387.845, bus2);
        assertAngleEquals(-0.022026, bus2);
        assertActivePowerEquals(101.466, l1.getTerminal1());
        assertReactivePowerEquals(246.252, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-244.853, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(94.853, svc1.getTerminal());
    }

    @Test
    void testSvcWithSlope2() {
        // Test switch PV to PQ
        svc1.setVoltageSetpoint(440)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(398.190, bus2);
        assertAngleEquals(-0.526124, bus2);
        assertActivePowerEquals(109.018, l1.getTerminal1());
        assertReactivePowerEquals(-1098.933, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(1122.987, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(-1268.445, svc1.getTerminal());
    }

    @Test
    void testSvcWithSlope3() {
        Generator gen = network.getVoltageLevel("vl2").newGenerator()
                .setId("gen")
                .setBus("b2")
                .setConnectableBus("b2")
                .setTargetP(0)
                .setTargetV(385)
                .setTargetQ(100)
                .setMaxP(100)
                .setMinP(0)
                .setVoltageRegulatorOn(true)
                .add();

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertReactivePowerEquals(228.948, svc1.getTerminal()); // same behaviour as slope = 0
        assertReactivePowerEquals(228.948, gen.getTerminal()); // same behaviour as slope = 0
    }

    @Test
    void testSvcWithSlope4() {

        StaticVarCompensator svc2 = network.getVoltageLevel("vl2").newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setVoltageSetpoint(385)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc2.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertReactivePowerEquals(228.948, svc1.getTerminal()); // same behaviour as slope = 0
        assertReactivePowerEquals(228.948, svc2.getTerminal()); // same behaviour as slope = 0
    }

    @Test
    void testSvcWithSlope5() {
        // With a generator at bus2 not controlling voltage
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();

        Generator gen = network.getVoltageLevel("vl2").newGenerator()
                .setId("gen")
                .setBus("b2")
                .setConnectableBus("b2")
                .setTargetP(0)
                .setTargetV(385)
                .setTargetQ(100)
                .setMaxP(100)
                .setMinP(0)
                .setVoltageRegulatorOn(false)
                .add();

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(388.462, bus2);
        assertAngleEquals(-0.052034, bus2);
        assertActivePowerEquals(101.249, l1.getTerminal1());
        assertReactivePowerEquals(166.160, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-165.413, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(115.413, svc1.getTerminal());
    }

    @Test
    void testRegulationModeOff() {
        svc1.setReactivePowerSetpoint(100)
                .setRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(100, svc1.getTerminal());
    }

    @Test
    void testStandByAutomaton() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(400)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(385)
                .withHighVoltageSetpoint(395)
                .withB0(-0.001f)
                .withStandbyStatus(true)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertReactivePowerEquals(150.091, svc1.getTerminal());
        assertVoltageEquals(387.415, bus2);
    }

    @Test
    void testStandByAutomaton2() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005)
                .withStandbyStatus(true)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertReactivePowerEquals(584.129, svc1.getTerminal());
        assertVoltageEquals(384.0, bus2);
    }

    @Test
    void testStandByAutomaton3() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        network.getGenerator("g1").setTargetV(405);

        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertReactivePowerEquals(1132.001, svc1.getTerminal());
        assertVoltageEquals(395.0, bus2);
    }

    @Test
    void testStandByAutomaton4() {
        // Test a voltage controller and a voltage monitor connected to the same bus.
        // Voltage monitor is discarded.
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();
        network.getVoltageLevel("vl2").newGenerator()
                .setMinP(-100)
                .setMaxP(100)
                .setTargetP(0.0)
                .setTargetV(392)
                .setVoltageRegulatorOn(true)
                .setId("g3")
                .setBus(bus2.getId())
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertReactivePowerEquals(768.320, svc1.getTerminal());
        assertVoltageEquals(392.0, bus2);
    }

    @Test
    void testStandByAutomaton5() {

        StaticVarCompensator svc2 = network.getVoltageLevel("vl2").newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setVoltageSetpoint(385)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        svc2.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();

        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(397)
                .withLowVoltageThreshold(383)
                .withLowVoltageSetpoint(384)
                .withHighVoltageSetpoint(395)
                .withB0(-0.005f)
                .withStandbyStatus(true)
                .add();

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116346, bus2);
        assertReactivePowerEquals(599.51, svc1.getTerminal()); // same behaviour as classical voltage control.
        assertReactivePowerEquals(599.51, svc2.getTerminal()); // same behaviour as classical voltage control.
    }
}
