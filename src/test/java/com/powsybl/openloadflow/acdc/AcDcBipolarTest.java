/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.acdc;

import com.powsybl.iidm.network.*;
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

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
class AcDcBipolarTest {

    Network network;

    @Test
    void testBipolarModel() {
        //Bipolar Model with metallic return, conv23p and conv23n control Pac, and conv45p and 45n control Vdc
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
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

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.578741, b5);
        assertAngleEquals(-0.072397, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-72.039750, g1.getTerminal());
        assertReactivePowerEquals(-20.112401, g1.getTerminal());

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-25.000000, conv23p.getTerminal1());
        assertDcPowerEquals(24.499703, conv23p.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(23.997967, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-24.498242, conv45n.getDcTerminal1());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(72.041571, l12.getTerminal1());
        assertReactivePowerEquals(20.112401, l12.getTerminal1());
        assertActivePowerEquals(-72.004789, l12.getTerminal2());
        assertReactivePowerEquals(-20.002056, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499703, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498203, dl34p.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());
    }

    @Test
    void testBipolarModelWithOtherControl() {
        //Bipolar Model with metallic return, conv23p and conv23n control Vdc, and conv45p and conv45n control Pac
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithOtherControl();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.583885, b5);
        assertAngleEquals(-0.070128, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.000000, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.000000, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-72.040539, g1.getTerminal());
        assertReactivePowerEquals(-20.112323, g1.getTerminal());

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-26.002240, conv23p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23p.getTerminal1());
        assertDcPowerEquals(25.501921, conv23p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(25.000000, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-25.500297, conv45n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45n.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(72.041957, l12.getTerminal1());
        assertReactivePowerEquals(20.112323, l12.getTerminal1());
        assertActivePowerEquals(-72.005175, l12.getTerminal2());
        assertReactivePowerEquals(-20.001977, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-25.501922, dl34p.getDcTerminal1());
        assertDcPowerEquals(25.500297, dl34p.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());
    }

    @Test
    void testBipolarModelAcVoltageControl() {
        //Bipolar Model with metallic return, conv23p and conv23n control Pac, and conv45p and conv45n control Vdc, and conv45p control Vac
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithAcVoltageControl();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.582978, b5);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-78.025643, g1.getTerminal());
        assertReactivePowerEquals(658.012909, g1.getTerminal());

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(24.499703, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(23.794432, conv45p.getTerminal1());
        assertReactivePowerEquals(695.483550, conv45p.getTerminal1());
        assertDcPowerEquals(-24.497213, conv45p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45p.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(78.031186, l12.getTerminal1());
        assertReactivePowerEquals(-658.012909, l12.getTerminal1());
        assertActivePowerEquals(-75.144468, l12.getTerminal2());
        assertReactivePowerEquals(666.673064, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499703, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498203, dl34p.getDcTerminal2());
    }

    @Test
    void testBipolarModelGridForming() {
        //Bipolar Model with metallic return, the converters conv4p and conv4n control Vac and are slack buses
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelGridForming();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(400.000000, b2);
        assertAngleEquals(0.024872, b2);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.011466, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012641, dn3n);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-102.560000, g1.getTerminal());
        assertReactivePowerEquals(1331.316598, g1.getTerminal());

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(24.499656, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000048, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(23.214841, conv45p.getTerminal1());
        assertReactivePowerEquals(16.950006, conv45p.getTerminal1());
        assertDcPowerEquals(-23.715181, conv45p.getDcTerminal1());
        assertDcPowerEquals(-0.000046, conv45p.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(102.560000, l12.getTerminal1());
        assertReactivePowerEquals(-1331.316598, l12.getTerminal1());
        assertActivePowerEquals(-90.837959, l12.getTerminal2());
        assertReactivePowerEquals(1366.482721, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-23.716290, dl34p.getDcTerminal1());
        assertDcPowerEquals(23.714884, dl34p.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithAcSubNetworks() {
        //Bipolar Model with metallic return, with 2 AC Networks
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithAcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.743083, b2);
        assertAngleEquals(-0.075389, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(390.000000, b5);
        assertAngleEquals(0.000000, b5);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-36.017601, g1.getTerminal());
        assertReactivePowerEquals(-10.098749, g1.getTerminal());

        Generator g5 = network.getGenerator("g5");
        assertActivePowerEquals(-36.017601, g5.getTerminal());
        assertReactivePowerEquals(-10.000000, g5.getTerminal());

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(24.499703, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(23.997929, conv45p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45p.getTerminal1());
        assertDcPowerEquals(-24.498204, conv45p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45p.getDcTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499703, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498203, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.499703, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498203, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithAcSubNetworksGridForming() {
        //Bipolar Model with metallic return, with 2 AC Networks, the reference buses and slack buses are set by converters
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithAcSubNetworksAndVoltageControl();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(0.000000, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-25.000000, conv23p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23p.getTerminal1());
        assertDcPowerEquals(24.499703, conv23p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(23.999764, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-24.500038, conv45n.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv45n.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(102.560000, l12.getTerminal1());
        assertReactivePowerEquals(4.913857, l12.getTerminal1());
        assertActivePowerEquals(-102.490686, l12.getTerminal2());
        assertReactivePowerEquals(-4.705914, l12.getTerminal2());
    }

    @Test
    void testBipolarModelThreeConverters() {
        //Bipolar Model with metallic return, with 3 converters but 1 Ac Network
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithThreeConverters();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(400.000000, b6);
        assertAngleEquals(-0.583039, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.583037, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.017246, dn3p);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(199.999249, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-199.998497, dn4n);

        DcNode dn6n = network.getDcNode("dn6n");
        assertVoltageEquals(-199.999249, dn6n);

        DcNode dnMp = network.getDcNode("dnMp");
        assertVoltageEquals(200.004997, dnMp);

        DcNode dnMn = network.getDcNode("dnMn");
        assertVoltageEquals(-200.004997, dnMn);

        DcNode dnMr = network.getDcNode("dnMr");
        assertVoltageEquals(0.000000, dnMr);

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(24.499703, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(10.905907, conv45p.getTerminal1());
        assertReactivePowerEquals(467.014915, conv45p.getTerminal1());
        assertDcPowerEquals(-11.497464, conv45p.getDcTerminal1());
        assertDcPowerEquals(-0.000043, conv45p.getDcTerminal2());

        VoltageSourceConverter conv6p = network.getVoltageSourceConverter("conv6p");
        assertActivePowerEquals(12.500000, conv6p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv6p.getTerminal1());
        assertDcPowerEquals(-13.000132, conv6p.getDcTerminal1());
        assertDcPowerEquals(0.000049, conv6p.getDcTerminal2());
    }

    @Test
    void testBipolarModelThreeConvertersWithAcSubNetworks() {
        //Bipolar Model with metallic return, with 3 converters with 3 Ac SubNetworks
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithThreeConverters();
        network.getLine("l25").remove();
        network.getLine("l26").remove();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.743083, b2);
        assertAngleEquals(-0.075389, b2);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(400.000000, b6);
        assertAngleEquals(0.000000, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(0.000000, b5);

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-25.000000, conv23p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23p.getTerminal1());
        assertDcPowerEquals(24.499703, conv23p.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(10.997248, conv45p.getTerminal1());
        assertReactivePowerEquals(10.000000, conv45p.getTerminal1());
        assertDcPowerEquals(-11.497318, conv45p.getDcTerminal1());
        assertDcPowerEquals(-0.000043, conv45p.getDcTerminal2());

        VoltageSourceConverter conv6p = network.getVoltageSourceConverter("conv6p");
        assertActivePowerEquals(12.500000, conv6p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv6p.getTerminal1());
        assertDcPowerEquals(-13.000132, conv6p.getDcTerminal1());
        assertDcPowerEquals(0.000049, conv6p.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithoutMetallicReturn() {
        //Bipolar Model without metallic return
        network = AcDcNetworkFactory.createBipolarModelWithoutMetallicReturn();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.022997, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.022997, dn3n);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dnGp = network.getDcNode("dnGp");
        assertVoltageEquals(200.010498, dnGp);

        DcNode dnGn = network.getDcNode("dnGn");
        assertVoltageEquals(-200.010498, dnGn);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-78.049837, g1.getTerminal());
        assertReactivePowerEquals(-20.131392, g1.getTerminal());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        assertActivePowerEquals(-50.000000, conv23.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23.getTerminal1());
        assertDcPowerEquals(25.000000, conv23.getDcTerminal1());
        assertDcPowerEquals(25.000000, conv23.getDcTerminal2());

        VoltageSourceConverter conv45 = network.getVoltageSourceConverter("conv45");
        assertActivePowerEquals(41.993831, conv45.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45.getTerminal1());
        assertDcPowerEquals(-20.996916, conv45.getDcTerminal1());
        assertDcPowerEquals(-20.996916, conv45.getDcTerminal2());

        DcLine dl3Gp = network.getDcLine("dl3Gp");
        assertDcPowerEquals(-25.000000, dl3Gp.getDcTerminal1());
        assertDcPowerEquals(24.998438, dl3Gp.getDcTerminal2());

        DcLine dlG4p = network.getDcLine("dlG4p");
        assertDcPowerEquals(-20.998018, dlG4p.getDcTerminal1());
        assertDcPowerEquals(20.996916, dlG4p.getDcTerminal2());

        DcLine dlGpGr = network.getDcLine("dlGpGr");
        assertDcPowerEquals(-4.000420, dlGpGr.getDcTerminal1());

        DcLine dlGnGr = network.getDcLine("dlGnGr");
        assertDcPowerEquals(-4.000420, dlGnGr.getDcTerminal2());
    }
}
