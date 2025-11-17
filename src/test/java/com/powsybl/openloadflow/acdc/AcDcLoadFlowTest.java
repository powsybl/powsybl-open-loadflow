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

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class AcDcLoadFlowTest {

    Network network;

    @Test
    void testNetworkFactory() {
        network = DcDetailedNetworkFactory.createVscAsymmetricalMonopole();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
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
