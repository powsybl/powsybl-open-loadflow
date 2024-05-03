/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
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
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SVC test case.
 *<pre>
 * g1        ld1
 * |          |
 * b1---------b2
 *      l1    |
 *           svc1
 *</pre>
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcLoadFlowSvcTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Line l1;
    private StaticVarCompensator svc1;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private Network createNetwork() {
        Network network = VoltageControlNetworkFactory.createWithStaticVarCompensator();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        svc1 = network.getStaticVarCompensator("svc1");
        l1 = network.getLine("l1");
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltagePerReactivePowerControl(false)
                .setSvcVoltageMonitoring(false);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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
        assertTrue(result.isFullyConverged());

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
        assertTrue(result.isFullyConverged());
        Bus bus = svc1.getTerminal().getBusView().getBus();
        assertVoltageEquals(386.256, bus);
        assertReactivePowerEquals(-svc1.getBmin() * bus.getV() * bus.getV(), svc1.getTerminal()); // min reactive limit has been correctly reached
    }

    @Test
    void testSvcWithSlope() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(398.155, bus2);
        assertAngleEquals(-0.524413, bus2);
        assertActivePowerEquals(108.952, l1.getTerminal1());
        assertReactivePowerEquals(-1094.367, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(1118.223, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(-1268.223, svc1.getTerminal());
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

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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

        network.getVoltageLevel("vl2").newGenerator()
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

        parametersExt.setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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
        assertTrue(result.isFullyConverged());
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

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(150.091, svc1.getTerminal());
        assertVoltageEquals(387.415, bus2);
    }

    @Test
    void testStandByAutomatonAndSlope() {
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.03).add();
        svc1.newExtension(StandbyAutomatonAdder.class)
                .withHighVoltageThreshold(400)
                .withLowVoltageThreshold(380)
                .withLowVoltageSetpoint(385)
                .withHighVoltageSetpoint(395)
                .withB0(-0.001f)
                .withStandbyStatus(true)
                .add();

        parametersExt
                .setSvcVoltageMonitoring(true)
                .setVoltagePerReactivePowerControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertReactivePowerEquals(150.091, svc1.getTerminal()); // same as testStandByAutomaton
        assertVoltageEquals(387.415, bus2); // same as testStandByAutomaton
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

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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

        parametersExt.setSvcVoltageMonitoring(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116346, bus2);
        assertReactivePowerEquals(599.51, svc1.getTerminal()); // same behaviour as classical voltage control.
        assertReactivePowerEquals(599.51, svc2.getTerminal()); // same behaviour as classical voltage control.
    }
}
