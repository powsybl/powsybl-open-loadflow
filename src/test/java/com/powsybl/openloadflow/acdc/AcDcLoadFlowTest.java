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

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class AcDcLoadFlowTest {

    Network network;

    @Test
    void testVscAsymmetricalMonopole() {
        network = DcDetailedNetworkFactory.createVscAsymmetricalMonopole();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        //TODO: adapt slack distribution for AC subnetworks
        //for now, we just deactivate slack distribution for AC DC load flow
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus busGb150 = network.getBusBreakerView().getBus("BUSDC-GB-xNodeDc1gb-150");
        assertVoltageEquals(149.776011, busGb150);
        assertAngleEquals(-2.550141, busGb150);

        Bus busFr150 = network.getBusBreakerView().getBus("BUSDC-FR-xNodeDc1fr-150");
        assertVoltageEquals(149.819131, busFr150);
        assertAngleEquals(2.543088, busFr150);

        Bus busGb400 = network.getBusBreakerView().getBus("BUSDC-GB-xNodeDc1gb-400");
        assertVoltageEquals(400.155070, busGb400);
        assertAngleEquals(-0.000000, busGb400);

        Bus busFr400 = network.getBusBreakerView().getBus("BUSDC-FR-xNodeDc1fr-400");
        assertVoltageEquals(399.556099, busFr400);
        assertAngleEquals(-0.000000, busFr400);

        Bus busFr = network.getBusBreakerView().getBus("BUS-FR");
        assertVoltageEquals(400.000000, busFr);
        assertAngleEquals(0.427977, busFr);

        Bus busGb = network.getBusBreakerView().getBus("BUS-GB");
        assertVoltageEquals(400.000000, busGb);
        assertAngleEquals(-0.431794, busGb);

        DcNode dcNodeGbNeg = network.getDcNode("dcNodeGbNeg");
        assertVoltageEquals(0.000000, dcNodeGbNeg);

        DcNode dcNodeGbPos = network.getDcNode("dcNodeGbPos");
        assertVoltageEquals(-501.992063, dcNodeGbPos);

        DcNode dcNodeFrNeg = network.getDcNode("dcNodeFrNeg");
        assertVoltageEquals(0.000000, dcNodeFrNeg);

        DcNode dcNodeFrPos = network.getDcNode("dcNodeFrPos");
        assertVoltageEquals(-500.000000, dcNodeFrPos);

        Generator genFr = network.getGenerator("GEN-FR");
        assertActivePowerEquals(-2000.000000, genFr.getTerminal());
        assertReactivePowerEquals(-10.336457, genFr.getTerminal());

        Generator genGb = network.getGenerator("GEN-GB");
        assertActivePowerEquals(-2000.000000, genGb.getTerminal());
        assertReactivePowerEquals(-10.419567, genGb.getTerminal());

        VoltageSourceConverter vscFr = network.getVoltageSourceConverter("VscFr");
        assertActivePowerEquals(199.206337, vscFr.getTerminal1());
        assertReactivePowerEquals(0.000000, vscFr.getTerminal1());
        assertDcPowerEquals(-0.000000, vscFr.getDcTerminal1());
        assertDcPowerEquals(-199.206337, vscFr.getDcTerminal2());

        VoltageSourceConverter vscGb = network.getVoltageSourceConverter("VscGb");
        assertActivePowerEquals(-200.000000, vscGb.getTerminal1());
        assertReactivePowerEquals(0.000000, vscGb.getTerminal1());
        assertDcPowerEquals(0.000000, vscGb.getDcTerminal1());
        assertDcPowerEquals(200.000000, vscGb.getDcTerminal2());

        DcLine dcLinePos = network.getDcLine("dcLinePos");
        assertDcPowerEquals(199.206337, dcLinePos.getDcTerminal1());
        assertDcPowerEquals(-200.000000, dcLinePos.getDcTerminal2());
    }

    @Test
    void testVscSymmetricalMonopole() {
        network = DcDetailedNetworkFactory.createVscSymmetricalMonopole();
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setAcDcNetwork(true);
        //FIXME : loadflow on this network does not converge with DenseMatrixFactory
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isFullyConverged());

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider());
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus busGb150 = network.getBusBreakerView().getBus("BUSDC-GB-xNodeDc1gb-150");
        assertVoltageEquals(149.776011, busGb150);
        assertAngleEquals(-2.550141, busGb150);

        Bus busFr150 = network.getBusBreakerView().getBus("BUSDC-FR-xNodeDc1fr-150");
        assertVoltageEquals(149.820194, busFr150);
        assertAngleEquals(2.533076, busFr150);

        Bus busGb400 = network.getBusBreakerView().getBus("BUSDC-GB-xNodeDc1gb-400");
        assertVoltageEquals(400.155070, busGb400);
        assertAngleEquals(0.000000, busGb400);

        Bus busFr400 = network.getBusBreakerView().getBus("BUSDC-FR-xNodeDc1fr-400");
        assertVoltageEquals(399.557158, busFr400);
        assertAngleEquals(0.000000, busFr400);

        Bus busFr = network.getBusBreakerView().getBus("BUS-FR");
        assertVoltageEquals(400.000000, busFr);
        assertAngleEquals(0.427991, busFr);

        Bus busGb = network.getBusBreakerView().getBus("BUS-GB");
        assertVoltageEquals(400.000000, busGb);
        assertAngleEquals(-0.431794, busGb);

        // FIXME: on OS macos build, the voltage are different, I don't understand why
//        DcNode dcNodeGbNeg = network.getDcNode("dcNodeGbNeg");
//        assertVoltageEquals(243.330843, dcNodeGbNeg);
//
//        DcNode dcNodeGbPos = network.getDcNode("dcNodeGbPos");
//        assertVoltageEquals(-260.637659, dcNodeGbPos);
//
//        DcNode dcNodeFrNeg = network.getDcNode("dcNodeFrNeg");
//        assertVoltageEquals(241.346592, dcNodeFrNeg);
//
//        DcNode dcNodeFrPos = network.getDcNode("dcNodeFrPos");
//        assertVoltageEquals(-258.653408, dcNodeFrPos);

        Generator genFr = network.getGenerator("GEN-FR");
        assertActivePowerEquals(-2000.000000, genFr.getTerminal());
        assertReactivePowerEquals(-10.265940, genFr.getTerminal());

        Generator genGb = network.getGenerator("GEN-GB");
        assertActivePowerEquals(-2000.000000, genGb.getTerminal());
        assertReactivePowerEquals(-10.419567, genGb.getTerminal());

        VoltageSourceConverter vscFr = network.getVoltageSourceConverter("VscFr");
        assertActivePowerEquals(198.425099, vscFr.getTerminal1());
        assertReactivePowerEquals(0.000000, vscFr.getTerminal1());
        assertDcPowerEquals(-95.778443, vscFr.getDcTerminal1());
        assertDcPowerEquals(-102.646656, vscFr.getDcTerminal2());

        VoltageSourceConverter vscGb = network.getVoltageSourceConverter("VscGb");
        assertActivePowerEquals(-200.000000, vscGb.getTerminal1());
        assertReactivePowerEquals(0.000000, vscGb.getTerminal1());
        assertDcPowerEquals(96.565893, vscGb.getDcTerminal1());
        assertDcPowerEquals(103.434107, vscGb.getDcTerminal2());

        DcLine dcLineNeg = network.getDcLine("dcLineNeg");
        assertDcPowerEquals(95.778443, dcLineNeg.getDcTerminal1());
        assertDcPowerEquals(-96.565893, dcLineNeg.getDcTerminal2());

        DcLine dcLinePos = network.getDcLine("dcLinePos");
        assertDcPowerEquals(102.646656, dcLinePos.getDcTerminal1());
        assertDcPowerEquals(-103.434107, dcLinePos.getDcTerminal2());
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

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.586038, b2);
        assertAngleEquals(-0.106723, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.506441, b5);
        assertAngleEquals(-0.104084, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-101.070832, g1.getTerminal());
        assertReactivePowerEquals(-20.211553, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.498886, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.996422, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-49.497493, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(101.074096, l12.getTerminal1());
        assertReactivePowerEquals(20.211553, l12.getTerminal1());
        assertActivePowerEquals(-101.004244, l12.getTerminal2());
        assertReactivePowerEquals(-20.001997, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(1.004244, l25.getTerminal1());
        assertReactivePowerEquals(10.001997, l25.getTerminal1());
        assertActivePowerEquals(-1.003578, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.498886, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497355, dl34.getDcTerminal2());
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

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.586035, b2);
        assertAngleEquals(-0.106724, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.506437, b5);
        assertAngleEquals(-0.104085, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-101.049523, g1.getTerminal());
        assertReactivePowerEquals(-20.211558, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.001248, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.500134, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.996421, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-49.497492, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(101.075345, l12.getTerminal1());
        assertReactivePowerEquals(20.211558, l12.getTerminal1());
        assertActivePowerEquals(-101.005492, l12.getTerminal2());
        assertReactivePowerEquals(-20.001997, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(1.004244, l25.getTerminal1());
        assertReactivePowerEquals(10.001997, l25.getTerminal1());
        assertActivePowerEquals(-1.003579, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.499024, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497492, dl34.getDcTerminal2());
    }

    @Test
    void testAcDcExampleGridForming() {
        //2 converters, 1 AC Network, the converter cs45 which controls Vac and Vdc is slack and reference bus for AC Network.
        network = AcDcNetworkFactory.createAcDcNetwork1();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.586038, b2);
        assertAngleEquals(-0.106723, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.506441, b5);
        assertAngleEquals(-0.104084, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-101.070832, g1.getTerminal());
        assertReactivePowerEquals(-20.211553, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.498886, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.996422, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-49.497493, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(101.074096, l12.getTerminal1());
        assertReactivePowerEquals(20.211553, l12.getTerminal1());
        assertActivePowerEquals(-101.004244, l12.getTerminal2());
        assertReactivePowerEquals(-20.001997, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(1.004244, l25.getTerminal1());
        assertReactivePowerEquals(10.001997, l25.getTerminal1());
        assertActivePowerEquals(-1.003578, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.498886, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497355, dl34.getDcTerminal2());
    }

    @Test
    void testThreeConverters() {
        //3 converters, 1 AC Network, cs23 controls Vdc, cs45 and cs56 control Pac
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
        assertVoltageEquals(389.687803, b2);
        assertAngleEquals(-0.080872, b2);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(389.649306, b6);
        assertAngleEquals(-0.078985, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.649306, b5);
        assertAngleEquals(-0.078985, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.000000, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(399.980874, dn4);

        DcNode dn5 = network.getDcNode("dn5");
        assertVoltageEquals(399.980874, dn5);

        DcNode dnMiddle = network.getDcNode("dnMiddle");
        assertVoltageEquals(399.987249, dnMiddle);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-76.532796, g1.getTerminal());
        assertReactivePowerEquals(-15.121063, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-51.504756, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(51.003577, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(25.000000, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-25.500297, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        VoltageSourceConverter conv56 = network.getVoltageSourceConverter("conv56");
        assertActivePowerEquals(25.000000, conv56.getTerminal1());
        assertReactivePowerEquals(0.000000, conv56.getTerminal1());
        assertDcPowerEquals(-25.500297, conv56.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv56.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(76.545111, l12.getTerminal1());
        assertReactivePowerEquals(15.121063, l12.getTerminal1());
        assertActivePowerEquals(-76.505086, l12.getTerminal2());
        assertReactivePowerEquals(-15.000988, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(0.000165, l25.getTerminal1());
        assertReactivePowerEquals(5.000494, l25.getTerminal1());
        assertActivePowerEquals(-0.000000, l25.getTerminal2());
        assertReactivePowerEquals(-5.000000, l25.getTerminal2());

        Line l26 = network.getLine("l26");
        assertActivePowerEquals(0.000165, l26.getTerminal1());
        assertReactivePowerEquals(5.000494, l26.getTerminal1());
        assertActivePowerEquals(-0.000000, l26.getTerminal2());
        assertReactivePowerEquals(-5.000000, l26.getTerminal2());

        DcLine dl3 = network.getDcLine("dl3");
        assertDcPowerEquals(-51.003032, dl3.getDcTerminal1());
        assertDcPowerEquals(51.001406, dl3.getDcTerminal2());

        DcLine dl4 = network.getDcLine("dl4");
        assertDcPowerEquals(-25.500703, dl4.getDcTerminal1());
        assertDcPowerEquals(25.500297, dl4.getDcTerminal2());

        DcLine dl5 = network.getDcLine("dl5");
        assertDcPowerEquals(-25.500703, dl5.getDcTerminal1());
        assertDcPowerEquals(25.500297, dl5.getDcTerminal2());
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
        assertAngleEquals(-0.366112, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.617861, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-107.175739, g1.getTerminal());
        assertReactivePowerEquals(662.496414, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(49.498886, conv23.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(48.796176, conv45.getTerminal1());
        assertReactivePowerEquals(700.314719, conv45.getTerminal1());
        assertDcPowerEquals(-49.502532, conv45.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(107.143259, l12.getTerminal1());
        assertReactivePowerEquals(-662.496414, l12.getTerminal1());
        assertActivePowerEquals(-104.182173, l12.getTerminal2());
        assertReactivePowerEquals(671.379672, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(4.182173, l25.getTerminal1());
        assertReactivePowerEquals(-681.379672, l25.getTerminal1());
        assertActivePowerEquals(-1.203824, l25.getTerminal2());
        assertReactivePowerEquals(690.314719, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.498886, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497355, dl34.getDcTerminal2());
    }

    @Test
    void testAcSubNetworks() {
        //2 converters, 2 AC Networks, cs23 controls Pac and Vac, cs45 controls Vdc and Vac, the converters set the slack and reference buses
        network = AcDcNetworkFactory.createAcDcNetworkWithAcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.601983, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(400.000000, b2);
        assertAngleEquals(0.000000, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(0.000000, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(399.987176, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-64.127190, g1.getTerminal());
        assertReactivePowerEquals(1331.316598, g1.getTerminal());

        Generator g5 = network.getGenerator("g5");
        assertActivePowerEquals(-11.567190, g5.getTerminal());
        assertReactivePowerEquals(-0.000000, g5.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(1376.482721, conv23.getTerminal1());
        assertDcPowerEquals(-51.292490, conv23.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(-51.795370, conv45.getTerminal1());
        assertReactivePowerEquals(10.000000, conv45.getTerminal1());
        assertDcPowerEquals(51.294134, conv45.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv45.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(102.560000, l12.getTerminal1());
        assertReactivePowerEquals(-1331.316598, l12.getTerminal1());
        assertActivePowerEquals(-90.837959, l12.getTerminal2());
        assertReactivePowerEquals(1366.482721, l12.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(51.292490, dl34.getDcTerminal1());
        assertDcPowerEquals(-51.294134, dl34.getDcTerminal2());
    }

    @Test
    void testDcSubNetworks() {
        //2 converters, 3 AC Network, 2 DC Networks, the converters set Vac and set slack and reference buses

        network = AcDcNetworkFactory.createAcDcNetworkDcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(400.000000, b1);
        assertAngleEquals(0.000000, b1);

        Bus b4 = network.getBusBreakerView().getBus("b4");
        assertVoltageEquals(389.935839, b4);
        assertAngleEquals(-0.028257, b4);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(389.935839, b6);
        assertAngleEquals(-0.028257, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(390.000000, b5);
        assertAngleEquals(0.000000, b5);

        Bus b9 = network.getBusBreakerView().getBus("b9");
        assertVoltageEquals(400.000000, b9);
        assertAngleEquals(0.000000, b9);

        DcNode dn2 = network.getDcNode("dn2");
        assertVoltageEquals(400.000000, dn2);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.006125, dn3);

        DcNode dn7 = network.getDcNode("dn7");
        assertVoltageEquals(400.006125, dn7);

        DcNode dn8 = network.getDcNode("dn8");
        assertVoltageEquals(400.000000, dn8);

        Generator g5 = network.getGenerator("g5");
        assertActivePowerEquals(-41.950606, g5.getTerminal());
        assertReactivePowerEquals(-0.024662, g5.getTerminal());

        VoltageSourceConverter conv12 = network.getVoltageSourceConverter("conv12");
        assertActivePowerEquals(23.999632, conv12.getTerminal1());
        assertReactivePowerEquals(10.000000, conv12.getTerminal1());
        assertDcPowerEquals(-24.499951, conv12.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv12.getDcTerminal2());

        VoltageSourceConverter conv34 = network.getVoltageSourceConverter("conv34");
        assertActivePowerEquals(-25.000000, conv34.getTerminal1());
        assertReactivePowerEquals(0.000000, conv34.getTerminal1());
        assertDcPowerEquals(24.499703, conv34.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv34.getDcTerminal2());

        VoltageSourceConverter conv67 = network.getVoltageSourceConverter("conv67");
        assertActivePowerEquals(-25.000000, conv67.getTerminal1());
        assertReactivePowerEquals(0.000000, conv67.getTerminal1());
        assertDcPowerEquals(24.499703, conv67.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv67.getDcTerminal2());

        VoltageSourceConverter conv89 = network.getVoltageSourceConverter("conv89");
        assertActivePowerEquals(23.999632, conv89.getTerminal1());
        assertReactivePowerEquals(10.000000, conv89.getTerminal1());
        assertDcPowerEquals(-24.499951, conv89.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv89.getDcTerminal2());

        Line l45 = network.getLine("l45");
        assertActivePowerEquals(-25.000000, l45.getTerminal1());
        assertReactivePowerEquals(0.000000, l45.getTerminal1());
        assertActivePowerEquals(25.004110, l45.getTerminal2());
        assertReactivePowerEquals(0.012331, l45.getTerminal2());

        Line l56 = network.getLine("l56");
        assertActivePowerEquals(25.004110, l56.getTerminal1());
        assertReactivePowerEquals(0.012331, l56.getTerminal1());
        assertActivePowerEquals(-25.000000, l56.getTerminal2());
        assertReactivePowerEquals(0.000000, l56.getTerminal2());

        DcLine dl23 = network.getDcLine("dl23");
        assertDcPowerEquals(24.499328, dl23.getDcTerminal1());
        assertDcPowerEquals(-24.499703, dl23.getDcTerminal2());

        DcLine dl78 = network.getDcLine("dl78");
        assertDcPowerEquals(-24.499703, dl78.getDcTerminal1());
        assertDcPowerEquals(24.499328, dl78.getDcTerminal2());
    }
}
