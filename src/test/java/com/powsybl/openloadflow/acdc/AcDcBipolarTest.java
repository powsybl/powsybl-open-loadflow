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
        assertAngleEquals(-0.072734, b5);

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
        assertActivePowerEquals(-72.1904457, g1.getTerminal());
        assertReactivePowerEquals(-20.112838, g1.getTerminal());

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-25.000000, conv23p.getTerminal1());
        assertDcPowerEquals(24.461586, conv23p.getDcTerminal1());
        assertDcPowerEquals(-0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(23.923379, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-24.460090, conv45n.getDcTerminal1());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(72.190854, l12.getTerminal1());
        assertReactivePowerEquals(20.112838, l12.getTerminal1());
        assertActivePowerEquals(-72.153931, l12.getTerminal2());
        assertReactivePowerEquals(-20.002068, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.461586, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.4600902, dl34p.getDcTerminal2());

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
        assertAngleEquals(-0.070304, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.000000, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.000000, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-72.197895, g1.getTerminal());
        assertReactivePowerEquals(-20.112766, g1.getTerminal());

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-26.080188, conv23p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23p.getTerminal1());
        assertDcPowerEquals(25.540052, conv23p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(25.000000, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-25.538421, conv45n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45n.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(72.197965, l12.getTerminal1());
        assertReactivePowerEquals(20.112766, l12.getTerminal1());
        assertActivePowerEquals(-72.161035, l12.getTerminal2());
        assertReactivePowerEquals(-20.001976, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-25.540052, dl34p.getDcTerminal1());
        assertDcPowerEquals(25.538421, dl34p.getDcTerminal2());

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
        assertAngleEquals(-0.582474, b5);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-77.82506, g1.getTerminal());
        assertReactivePowerEquals(657.946699, g1.getTerminal());

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(24.499703, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(23.998499, conv45p.getTerminal1());
        assertReactivePowerEquals(695.41316, conv45p.getTerminal1());
        assertDcPowerEquals(-24.498499, conv45p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45p.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(77.825155, l12.getTerminal1());
        assertReactivePowerEquals(-657.946699, l12.getTerminal1());
        assertActivePowerEquals(-74.939220, l12.getTerminal2());
        assertReactivePowerEquals(666.604502, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499703, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498203, dl34p.getDcTerminal2());
    }

    @Test
    void testBipolarModelGridForming() {
        //Bipolar Model with metallic return, the converters conv45p and conv45n control Vac
        network = AcDcNetworkFactory.createAcDcNetworkBipolarModelGridForming();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(400.000000, b2);
        assertAngleEquals(-0.578843, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.581233, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-83.59521, g1.getTerminal());
        assertReactivePowerEquals(1325.211411, g1.getTerminal());

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-25.000000, conv23p.getTerminal1());
        assertReactivePowerEquals(1369.320377, conv23p.getTerminal1());
        assertDcPowerEquals(24.5, conv23p.getDcTerminal1());
        assertDcPowerEquals(0, conv23p.getDcTerminal2());

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(24.5, conv23n.getDcTerminal1());
        assertDcPowerEquals(0, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(23.998494, conv45p.getTerminal1());
        assertReactivePowerEquals(10.667713, conv45p.getTerminal1());
        assertDcPowerEquals(-24.498494, conv45p.getDcTerminal1());
        assertDcPowerEquals(-0.000046, conv45p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(23.998494, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-24.498494, conv45n.getDcTerminal1());
        assertDcPowerEquals(-0.000046, conv45n.getDcTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.5, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498494, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.5, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498494, dl34n.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(83.59521, l12.getTerminal1());
        assertReactivePowerEquals(-1325.211411, l12.getTerminal1());
        assertActivePowerEquals(-72.003028, l12.getTerminal2());
        assertReactivePowerEquals(1359.988007, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(2.003028, l25.getTerminal1());
        assertReactivePowerEquals(-0.667629, l25.getTerminal1());
        assertActivePowerEquals(-2.003000, l25.getTerminal2());
        assertReactivePowerEquals(0.667713, l25.getTerminal2());

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
        assertAngleEquals(-0.582696, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.582696, b5);

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
        assertDcPowerEquals(24.5, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(10.997740, conv45p.getTerminal1());
        assertReactivePowerEquals(466.983299, conv45p.getTerminal1());
        assertDcPowerEquals(-11.497464, conv45p.getDcTerminal1());
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

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.666051, b2);
        assertAngleEquals(-0.071637, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.589038, b5);
        assertAngleEquals(-0.067866, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012499, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012499, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-70.038374, g1.getTerminal());
        assertReactivePowerEquals(-20.106704, g1.getTerminal());

        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        assertActivePowerEquals(-25.000000, conv23p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23p.getTerminal1());
        assertDcPowerEquals(25.000000, conv23p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23p.getDcTerminal2());

        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        assertActivePowerEquals(-25.000000, conv23n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv23n.getTerminal1());
        assertDcPowerEquals(25.000000, conv23n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv23n.getDcTerminal2());

        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertActivePowerEquals(24.998437, conv45p.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45p.getTerminal1());
        assertDcPowerEquals(-24.998437, conv45p.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45p.getDcTerminal2());

        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertActivePowerEquals(24.998437, conv45n.getTerminal1());
        assertReactivePowerEquals(0.000000, conv45n.getTerminal1());
        assertDcPowerEquals(-24.998437, conv45n.getDcTerminal1());
        assertDcPowerEquals(0.000000, conv45n.getDcTerminal2());

        DcLine dl3Gp = network.getDcLine("dl34p");
        assertDcPowerEquals(-25.000000, dl3Gp.getDcTerminal1());
        assertDcPowerEquals(24.998437, dl3Gp.getDcTerminal2());

        DcLine dlG4p = network.getDcLine("dl34p");
        assertDcPowerEquals(-25.000000, dlG4p.getDcTerminal1());
        assertDcPowerEquals(24.998437, dlG4p.getDcTerminal2());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(70.038374, l12.getTerminal1());
        assertReactivePowerEquals(20.106704, l12.getTerminal1());
        assertActivePowerEquals(-70.003783, l12.getTerminal2());
        assertReactivePowerEquals(-20.001976, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(0.003783, l25.getTerminal1());
        assertReactivePowerEquals(10.001976, l25.getTerminal1());
        assertActivePowerEquals(-0.003124, l25.getTerminal2());
        assertReactivePowerEquals(-10.00000, l25.getTerminal2());
    }
}
