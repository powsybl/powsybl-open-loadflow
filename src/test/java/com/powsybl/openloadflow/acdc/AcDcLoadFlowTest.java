/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.acdc;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.DcDetailedNetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.network.AcDcNetworkFactory.createAcDcNetwork1;
import static com.powsybl.openloadflow.network.AcDcNetworkFactory.createBaseNetwork;
import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
class AcDcLoadFlowTest {

    Network network;

    @Test
    void testConverterIdleLoss() {
        network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        // Converter with only idle loss
        vl2.newVoltageSourceConverter()
                .setIdleLoss(2)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-50.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        // Converter without losses
        vl5.newVoltageSourceConverter()
                .setIdleLoss(0)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(48.0, conv23.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23.getDcTerminal2());

    }

    @Test
    void testConverterSwitchingLoss() {
        network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        // Converter with only switching losses
        vl2.newVoltageSourceConverter()
                .setIdleLoss(0)
                .setSwitchingLoss(0.01)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-50.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        // Converter without losses
        vl5.newVoltageSourceConverter()
                .setIdleLoss(0)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertCurrentEquals(74.1, conv23.getTerminal1());
        assertDcPowerEquals(50 - 74.1 * 0.01, conv23.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23.getDcTerminal2());
    }

    @Test
    void testConverterResistiveLoss() {
        network = createBaseNetwork();
        VoltageLevel vl2 = network.getVoltageLevel("vl2");
        VoltageLevel vl5 = network.getVoltageLevel("vl5");

        // Converter with only resistive losses
        vl2.newVoltageSourceConverter()
                .setIdleLoss(0)
                .setSwitchingLoss(0)
                .setResistiveLoss(5)
                .setControlMode(AcDcConverter.ControlMode.P_PCC)
                .setTargetP(-50.)
                .setId("conv23")
                .setBus1("b2")
                .setDcNode1("dn3")
                .setDcNode2("dnDummy3")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        // Converter without losses
        vl5.newVoltageSourceConverter()
                .setIdleLoss(0)
                .setSwitchingLoss(0)
                .setResistiveLoss(0)
                .setControlMode(AcDcConverter.ControlMode.V_DC)
                .setTargetVdc(400.)
                .setId("conv45")
                .setBus1("b5")
                .setDcNode1("dn4")
                .setDcNode2("dnDummy4")
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setVoltageRegulatorOn(false)
                .setReactivePowerSetpoint(0.0)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertCurrentEquals(74.1, conv23.getTerminal1());
        assertDcPowerEquals(50 - Math.pow(74.1 / 1000, 2) * 5, conv23.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23.getDcTerminal2());
    }

    @Test
    void testVscAsymmetricalMonopole() {
        network = DcDetailedNetworkFactory.createVscAsymmetricalMonopole();
        // Set the same nominal voltage to nodes connected to the ground. Ground equation will set their voltage to zero
        network.getDcNode("dcNodeFrNeg").setNominalV(500.0F);
        network.getDcNode("dcNodeGbNeg").setNominalV(500.0F);
        // The targetPac of VSC is shifted, we fix it here
        VoltageSourceConverter vscGb = network.getVoltageSourceConverter("VscGb");
        vscGb.setTargetP(vscGb.getTargetP() * -1);

        // TODO: adapt slack distribution for AC subnetworks
        // For now, AC-DC load flow with multiple AC island is not possible. Therefore, we add an AC line to connect the
        // two AC island
        network.newLine()
                .setId("acLine")
                .setBus1("BUS-FR")
                .setConnectableBus1("BUS-FR")
                .setBus2("BUS-GB")
                .setConnectableBus2("BUS-GB")
                .setR(1)
                .setX(5)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("BUS-FR")
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus busGb150 = network.getBusBreakerView().getBus("BUSDC-GB-xNodeDc1gb-150");
        assertVoltageEquals(150.043375, busGb150);
        assertAngleEquals(2.976066, busGb150);

        Bus busFr400 = network.getBusBreakerView().getBus("BUSDC-FR-xNodeDc1fr-400");
        assertVoltageEquals(399.551518, busFr400);
        assertAngleEquals(-0.430388, busFr400);

        Bus busFr = network.getBusBreakerView().getBus("BUS-FR");
        assertVoltageEquals(400.000000, busFr);
        assertAngleEquals(0.0, busFr);

        Bus busGb = network.getBusBreakerView().getBus("BUS-GB");
        assertVoltageEquals(400.000000, busGb);
        assertAngleEquals(-0.00061, busGb);

        DcNode dcNodeGbNeg = network.getDcNode("dcNodeGbNeg");
        assertVoltageEquals(0.000000, dcNodeGbNeg);

        DcNode dcNodeGbPos = network.getDcNode("dcNodeGbPos");
        assertVoltageEquals(-497.991935, dcNodeGbPos);

        DcNode dcNodeFrNeg = network.getDcNode("dcNodeFrNeg");
        assertVoltageEquals(0.000000, dcNodeFrNeg);

        DcNode dcNodeFrPos = network.getDcNode("dcNodeFrPos");
        assertVoltageEquals(-500.000000, dcNodeFrPos);

        Generator genFr = network.getGenerator("GEN-FR");
        assertActivePowerEquals(-2000.732810, genFr.getTerminal());
        assertReactivePowerEquals(-10.470861, genFr.getTerminal());

        VoltageSourceConverter vscFr = network.getVoltageSourceConverter("VscFr");
        assertActivePowerEquals(-200.806464, vscFr.getTerminal1());
        assertReactivePowerEquals(0.000000, vscFr.getTerminal1());
        assertDcPowerEquals(-0.000000, vscFr.getDcTerminal1());
        assertDcPowerEquals(200.806464, vscFr.getDcTerminal2());

        assertActivePowerEquals(200.000000, vscGb.getTerminal1());
        assertReactivePowerEquals(0.000000, vscGb.getTerminal1());
        assertDcPowerEquals(0.000000, vscGb.getDcTerminal1());
        assertDcPowerEquals(-200.000000, vscGb.getDcTerminal2());

        DcLine dcLinePos = network.getDcLine("dcLinePos");
        assertDcPowerEquals(-200.806464, dcLinePos.getDcTerminal1());
        assertDcPowerEquals(200.000000, dcLinePos.getDcTerminal2());

        Line acLine = network.getLine("acLine");
        assertActivePowerEquals(0.327588, acLine.getTerminal1());
        assertActivePowerEquals(-0.327588, acLine.getTerminal2());
        assertReactivePowerEquals(-0.065519, acLine.getTerminal1());
        assertReactivePowerEquals(0.065519, acLine.getTerminal2());
    }

    @Test
    void testVscSymmetricalMonopole() {
        network = DcDetailedNetworkFactory.createVscSymmetricalMonopole();
        Network subnetwork = network.getSubnetwork("VscSymmetricalMonopole");
        // A DcGround is needed in the network to set voltage reference
        subnetwork.newDcNode().setId("dnGround").setNominalV(250.0).add();
        subnetwork.newDcLine().setId("dlGroundNeg").setR(1e10).setDcNode1("dcNodeGbNeg").setDcNode2("dnGround").add();
        subnetwork.newDcLine().setId("dlGroundPos").setR(1e10).setDcNode1("dcNodeGbPos").setDcNode2("dnGround").add();
        subnetwork.newDcGround().setDcNode("dnGround").setId("dcGround").add();

        // The targetPac of VSC is shifted, we fix it here
        VoltageSourceConverter vscGb = network.getVoltageSourceConverter("VscGb");
        vscGb.setTargetP(vscGb.getTargetP() * -1);

        // TODO: adapt slack distribution for AC subnetworks
        // For now, AC-DC load flow with multiple AC island is not possible. Therefore, we add an AC line to connect the
        // two AC island
        network.newLine()
                .setId("acLine")
                .setBus1("BUS-FR")
                .setConnectableBus1("BUS-FR")
                .setBus2("BUS-GB")
                .setConnectableBus2("BUS-GB")
                .setR(1)
                .setX(5)
                .add();

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("BUS-FR")
                .setAcDcNetwork(true);
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        Bus busGb150 = network.getBusBreakerView().getBus("BUSDC-GB-xNodeDc1gb-150");
        assertVoltageEquals(150.043375, busGb150);
        assertAngleEquals(2.976065, busGb150);

        Bus busFr400 = network.getBusBreakerView().getBus("BUSDC-FR-xNodeDc1fr-400");
        assertVoltageEquals(399.557158, busFr400);
        assertAngleEquals(-0.432141, busFr400);

        Bus busFr = network.getBusBreakerView().getBus("BUS-FR");
        assertVoltageEquals(400.000000, busFr);
        assertAngleEquals(0.00000, busFr);

        DcNode dcNodeFrNeg = network.getDcNode("dcNodeFrNeg");
        assertVoltageEquals(250.000000, dcNodeFrNeg);

        DcNode dcNodeFrPos = network.getDcNode("dcNodeFrPos");
        assertVoltageEquals(-250.000000, dcNodeFrPos);

        Generator genGb = network.getGenerator("GEN-GB");
        assertActivePowerEquals(-2001.716598, genGb.getTerminal());
        assertReactivePowerEquals(-10.448402, genGb.getTerminal());

        VoltageSourceConverter vscFr = network.getVoltageSourceConverter("VscFr");
        assertActivePowerEquals(-201.626136, vscFr.getTerminal1());
        assertReactivePowerEquals(0.000000, vscFr.getTerminal1());
        assertDcPowerEquals(100.813068, vscFr.getDcTerminal1());
        assertDcPowerEquals(100.813068, vscFr.getDcTerminal2());

        assertActivePowerEquals(200.000000, vscGb.getTerminal1());
        assertReactivePowerEquals(0.000000, vscGb.getTerminal1());
        assertDcPowerEquals(-100.000000, vscGb.getDcTerminal1());
        assertDcPowerEquals(-100.000000, vscGb.getDcTerminal2());

        DcLine dcLineNeg = network.getDcLine("dcLineNeg");
        assertDcPowerEquals(-100.813068, dcLineNeg.getDcTerminal1());
        assertDcPowerEquals(100.000000, dcLineNeg.getDcTerminal2());

        DcLine dcLinePos = network.getDcLine("dcLinePos");
        assertDcPowerEquals(-100.813068, dcLinePos.getDcTerminal1());
        assertDcPowerEquals(100.000000, dcLinePos.getDcTerminal2());

        DcLine dlGroundNeg = network.getDcLine("dlGroundNeg");
        assertDcPowerEquals(-0.000013, dlGroundNeg.getDcTerminal1());

        DcLine dlGroundPos = network.getDcLine("dlGroundPos");
        assertDcPowerEquals(-0.000013, dlGroundPos.getDcTerminal1());

        Line acLine = network.getLine("acLine");
        assertActivePowerEquals(0.327588, acLine.getTerminal1());
        assertActivePowerEquals(-0.327588, acLine.getTerminal2());
        assertReactivePowerEquals(-0.065519, acLine.getTerminal1());
        assertReactivePowerEquals(0.065519, acLine.getTerminal2());
    }

    @Test
    void testAcDcExample() {
        //2 converters, 1 AC Network, the first converter controls Pac, and the second one Vdc
        network = AcDcNetworkFactory.createAcDcNetwork1();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.505640, b5);
        assertAngleEquals(-0.104435, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-101.229244, g1.getTerminal());
        assertReactivePowerEquals(-20.212180, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.420411, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.841248, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-49.418885, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(101.229478, l12.getTerminal1());
        assertReactivePowerEquals(20.212180, l12.getTerminal1());
        assertActivePowerEquals(-101.159419, l12.getTerminal2());
        assertReactivePowerEquals(-20.002003, l12.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.420411, dl34.getDcTerminal1());
        assertDcPowerEquals(49.418885, dl34.getDcTerminal2());
    }

    @Test
    void testAcDcExampleWithOtherControl() {
        //2 converters, 1 AC Network, the first converter controls Vdc, and the second one Pac
        network = AcDcNetworkFactory.createAcDcNetwork2();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.50603, b5);
        assertAngleEquals(-0.104260, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-101.229780, g1.getTerminal());
        assertReactivePowerEquals(-20.212175, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.155711, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.5758575, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.996421, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-49.574321, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(101.230014, l12.getTerminal1());
        assertReactivePowerEquals(20.212175, l12.getTerminal1());
        assertActivePowerEquals(-101.159955, l12.getTerminal2());
        assertReactivePowerEquals(-20.001997, l12.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.575857, dl34.getDcTerminal1());
        assertDcPowerEquals(49.574321, dl34.getDcTerminal2());
    }

    @Test
    void testAcDcExampleGridForming() {
        //2 converters, 1 AC Network, the converter cs45 which controls Vac and Vdc is slack and reference bus for AC Network.
        network = createAcDcNetwork1();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.505640, b5);
        assertAngleEquals(-0.1044353, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.420411, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.841248, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-49.418885, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(101.229478, l12.getTerminal1());
        assertReactivePowerEquals(20.212180, l12.getTerminal1());
        assertActivePowerEquals(-101.159419, l12.getTerminal2());
        assertReactivePowerEquals(-20.002003, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(1.159419, l25.getTerminal1());
        assertReactivePowerEquals(10.002003, l25.getTerminal1());
        assertActivePowerEquals(-1.1587516, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());
    }

    @Test
    void testThreeConverters() {
        //3 converters, 1 AC Network, cs23 controls Vdc, cs45 and cs67 control Pac
        network = AcDcNetworkFactory.createAcDcNetworkWithThreeConverters();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.687398, b2);
        assertAngleEquals(-0.081049, b2);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(389.648901, b6);
        assertAngleEquals(-0.0791627, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.648901, b5);
        assertAngleEquals(-0.0791627, b5);

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-51.661675, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(51.079276, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(25.000000, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-25.538415, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        VoltageSourceConverter conv56 = network.getVoltageSourceConverter("conv67");
        assertActivePowerEquals(25.000000, conv56.getTerminal1());
        assertReactivePowerEquals(0.000000, conv56.getTerminal1());
        assertDcPowerEquals(-25.538415, conv56.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv56.getDcTerminal2());

        DcLine dl3 = network.getDcLine("dl3");
        assertDcPowerEquals(-51.079276, dl3.getDcTerminal1());
        assertDcPowerEquals(51.077645, dl3.getDcTerminal2());

        DcLine dl4 = network.getDcLine("dl4");
        assertDcPowerEquals(-25.538822, dl4.getDcTerminal1());
        assertDcPowerEquals(25.538415, dl4.getDcTerminal2());
    }

    @Test
    void testAcVoltageControl() {
        //2 converters, 1 AC Network, cs23 controls Pac, cs45 controls Vdc and Vac, but is not a slack
        network = AcDcNetworkFactory.createAcDcNetworkWithAcVoltageControl();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(394.829460, b2);
        assertAngleEquals(-0.365860, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.617363, b5);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-106.938695, g1.getTerminal());
        assertReactivePowerEquals(662.431138, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(106.939507, l12.getTerminal1());
        assertReactivePowerEquals(-662.431138, l12.getTerminal1());
        assertActivePowerEquals(-103.979276, l12.getTerminal2());
        assertReactivePowerEquals(671.311829, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(3.979276, l25.getTerminal1());
        assertReactivePowerEquals(-681.311829, l25.getTerminal1());
        assertActivePowerEquals(-1.001531, l25.getTerminal2());
        assertReactivePowerEquals(690.245065, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.5, dl34.getDcTerminal1());
        assertDcPowerEquals(49.498468, dl34.getDcTerminal2());
    }

    @Test
    void testAcSubNetworks() {
        // Network with 2 AC Island. An exception should be thrown
        network = AcDcNetworkFactory.createAcDcNetworkWithAcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcDcNetwork(true);

        CompletionException e = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));
        assertEquals("AC-DC load flow does not support multiple AC island for the moment", e.getCause().getMessage());
    }

    @Test
    void testDcSubNetworks() {
        //2 converters, 3 AC Network, 2 DC Networks, the converters set Vac and set slack and reference buses
        network = AcDcNetworkFactory.createAcDcNetworkTwoDcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(399.248274, b1);
        assertAngleEquals(-0.071754, b1);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(400.000000, b6);
        assertAngleEquals(0.000000, b6);

        DcNode dn2 = network.getDcNode("dn2");
        assertVoltageEquals(499.498496, dn2);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(500.000000, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(299.330171, dn4);

        DcNode dn5 = network.getDcNode("dn5");
        assertVoltageEquals(300.000000, dn5);

        VoltageSourceConverter conv12 = network.getVoltageSourceConverter("conv12");
        assertActivePowerEquals(250.000, conv12.getTerminal1());
        assertReactivePowerEquals(0.000000, conv12.getTerminal1());
        assertDcPowerEquals(-250.50000, conv12.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv12.getDcTerminal2());

        VoltageSourceConverter conv36 = network.getVoltageSourceConverter("conv36");
        assertActivePowerEquals(-251.251505, conv36.getTerminal1());
        assertReactivePowerEquals(0.000000, conv36.getTerminal1());
        assertDcPowerEquals(250.751505, conv36.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv36.getDcTerminal2());

        VoltageSourceConverter conv14 = network.getVoltageSourceConverter("conv14");
        assertActivePowerEquals(200.000000, conv14.getTerminal1());
        assertReactivePowerEquals(0.000000, conv14.getTerminal1());
        assertDcPowerEquals(-200.50000, conv14.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv14.getDcTerminal2());

        VoltageSourceConverter conv56 = network.getVoltageSourceConverter("conv56");
        assertActivePowerEquals(-201.448670, conv56.getTerminal1());
        assertReactivePowerEquals(0.000000, conv56.getTerminal1());
        assertDcPowerEquals(200.948670, conv56.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv56.getDcTerminal2());

        DcLine dl23 = network.getDcLine("dl23");
        assertDcPowerEquals(250.50000, dl23.getDcTerminal1());
        assertDcPowerEquals(-250.751505, dl23.getDcTerminal2());

        DcLine dl45 = network.getDcLine("dl45");
        assertDcPowerEquals(200.50000, dl45.getDcTerminal1());
        assertDcPowerEquals(-200.948670, dl45.getDcTerminal2());

        Line l16 = network.getLine("l16");
        assertActivePowerEquals(-50.000000, l16.getTerminal1());
        assertReactivePowerEquals(-50.00000, l16.getTerminal1());
        assertActivePowerEquals(50.031367, l16.getTerminal2());
        assertReactivePowerEquals(50.156838, l16.getTerminal2());
    }

    @Test
    void testVoltageInitializer() {
        network = AcDcNetworkFactory.createAcDcNetwork1();

        // Uniform values initializer
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters1 = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES);
        OpenLoadFlowParameters.create(parameters1)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result1 = loadFlowRunner.run(network, parameters1);
        assertTrue(result1.isFullyConverged());

        // Previous values initializer
        LoadFlowParameters parameters2 = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        OpenLoadFlowParameters.create(parameters2)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result2 = loadFlowRunner.run(network, parameters2);
        assertTrue(result2.isFullyConverged());

        // DC initializer (should throw exception)
        LoadFlowParameters parameters3 = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        OpenLoadFlowParameters.create(parameters3)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        CompletionException e3 = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters3));
        assertEquals("DC initialization is not yet supported with AcDcNetwork", e3.getCause().getMessage());

        // Voltage magnitude override (should throw exception)
        LoadFlowParameters parameters4 = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters4)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.VOLTAGE_MAGNITUDE)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        CompletionException e4 = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters4));
        assertEquals("Voltage magnitude initialization is not yet supported with AcDcNetwork", e4.getCause().getMessage());

        // Full voltage override (should throw exception)
        LoadFlowParameters parameters5 = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters5)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        CompletionException e5 = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters5));
        assertEquals("Full voltage initialization is not yet supported with AcDcNetwork", e5.getCause().getMessage());
    }
}
