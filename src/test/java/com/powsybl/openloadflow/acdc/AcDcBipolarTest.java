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
import com.powsybl.openloadflow.util.WriteTests;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AcDcBipolarTest {
    @Test
    void testBipolarModel() {
        //Bipolar Model with metallic return, cs23p and cs23n control Pac, and cs45p and 45n control Vdc
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
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
        assertVoltageEquals(389.669599, b2);
        assertAngleEquals(-0.069367, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.340166, b5);
        assertAngleEquals(-0.176616, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-68.094922, g1.getTerminal());
        assertReactivePowerEquals(-20.291608, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(68.093022, l12.getTerminal1());
        assertReactivePowerEquals(20.291608, l12.getTerminal1());
        assertActivePowerEquals(-68.059831, l12.getTerminal2());
        assertReactivePowerEquals(-20.192033, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(98.059831, l25.getTerminal1());
        assertReactivePowerEquals(10.192033, l25.getTerminal1());
        assertActivePowerEquals(-97.995820, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

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
    void testBipolarModelWithOtherControl() {
        //Bipolar Model with metallic return, cs23p and cs23n control Vdc, and cs45p and cs45n control Pac
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithOtherControl();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        WriteTests.printTests(network);

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.669532, b2);
        assertAngleEquals(-0.069366, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.334918, b5);
        assertAngleEquals(-0.178888, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.000000, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.000000, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(199.987249, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-199.987249, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(-0.000000, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-68.096729, g1.getTerminal());
        assertReactivePowerEquals(-20.299479, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(68.095377, l12.getTerminal1());
        assertReactivePowerEquals(20.299479, l12.getTerminal1());
        assertActivePowerEquals(-68.062182, l12.getTerminal2());
        assertReactivePowerEquals(-20.199892, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(100.066631, l25.getTerminal1());
        assertReactivePowerEquals(10.199892, l25.getTerminal1());
        assertActivePowerEquals(-100.000000, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-25.501922, dl34p.getDcTerminal1());
        assertDcPowerEquals(25.500296, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-25.501922, dl34n.getDcTerminal1());
        assertDcPowerEquals(25.500296, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelAcVoltageControl() {
        //Bipolar Model with metallic return, cs23p and cs23n control Pac, and cs45p and cs45n control Vdc, and cs45p control Vac
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithAcVoltageControl();
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
        assertVoltageEquals(394.992749, b2);
        assertAngleEquals(-0.332744, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.693435, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-73.935315, g1.getTerminal());
        assertReactivePowerEquals(672.834452, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(73.929139, l12.getTerminal1());
        assertReactivePowerEquals(-672.834452, l12.getTerminal1());
        assertActivePowerEquals(-70.916833, l12.getTerminal2());
        assertReactivePowerEquals(681.871369, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(100.916833, l25.getTerminal1());
        assertReactivePowerEquals(-691.871369, l25.getTerminal1());
        assertActivePowerEquals(-97.783437, l25.getTerminal2());
        assertReactivePowerEquals(701.271558, l25.getTerminal2());

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
    void testBipolarModelGridForming() {
        //Bipolar Model with metallic return, the converters cs4p and cs4n control Vac and are slack buses
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelGridForming();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.689683, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(400.000000, b2);
        assertAngleEquals(0.116357, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(0.000000, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.011504, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012622, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(0.000373, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(199.999627, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000373, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(-0.000373, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-79.074031, g1.getTerminal());
        assertReactivePowerEquals(1323.754688, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(79.074031, l12.getTerminal1());
        assertReactivePowerEquals(-1323.754688, l12.getTerminal1());
        assertActivePowerEquals(-67.512038, l12.getTerminal2());
        assertReactivePowerEquals(1358.440668, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(97.512038, l25.getTerminal1());
        assertReactivePowerEquals(-32.394034, l25.getTerminal1());
        assertActivePowerEquals(-97.446051, l25.getTerminal2());
        assertReactivePowerEquals(32.591996, l25.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-23.754097, dl34p.getDcTerminal1());
        assertDcPowerEquals(23.752686, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.499658, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498157, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000001, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000001, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithAcSubNetworks() {
        //Bipolar Model with metallic return, with 2 AC Networks
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithAcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.999916, b2);
        assertAngleEquals(0.037670, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(390.000000, b5);
        assertAngleEquals(0.000000, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-34.002145, g1.getTerminal());
        assertReactivePowerEquals(-10.019724, g1.getTerminal());

        Generator g5 = network.getGenerator("g5");
        assertActivePowerEquals(-34.002145, g5.getTerminal());
        assertReactivePowerEquals(-10.000000, g5.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(-29.993425, l12.getTerminal1());
        assertReactivePowerEquals(10.019724, l12.getTerminal1());
        assertActivePowerEquals(30.000000, l12.getTerminal2());
        assertReactivePowerEquals(-10.000000, l12.getTerminal2());

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
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithAcSubNetworksAndVoltageControl();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.573161, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(400.000000, b2);
        assertAngleEquals(0.000000, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(0.000000, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012640, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.011467, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000391, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000391, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-199.999609, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000391, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-78.938820, g1.getTerminal());
        assertReactivePowerEquals(1323.711116, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(78.938820, l12.getTerminal1());
        assertReactivePowerEquals(-1323.711116, l12.getTerminal1());
        assertActivePowerEquals(-67.377726, l12.getTerminal2());
        assertReactivePowerEquals(1358.394399, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499656, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498155, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-23.717601, dl34n.getDcTerminal1());
        assertDcPowerEquals(23.716195, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000002, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000002, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelThreeConverters() {
        //Bipolar Model with metallic return, with 3 converters but 1 Ac Network
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithThreeConverters();
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
        assertVoltageEquals(389.465706, b2);
        assertAngleEquals(-0.121079, b2);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(389.195527, b6);
        assertAngleEquals(-0.202348, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.195527, b5);
        assertAngleEquals(-0.202348, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.000000, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.000000, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(-0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(199.980498, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-199.980498, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        DcNode dn6p = network.getDcNode("dn6p");
        assertVoltageEquals(199.980498, dn6p);

        DcNode dn6n = network.getDcNode("dn6n");
        assertVoltageEquals(-199.980498, dn6n);

        DcNode dn6r = network.getDcNode("dn6r");
        assertVoltageEquals(0.000000, dn6r);

        DcNode dnMp = network.getDcNode("dnMp");
        assertVoltageEquals(199.986999, dnMp);

        DcNode dnMn = network.getDcNode("dnMn");
        assertVoltageEquals(-199.986999, dnMn);

        DcNode dnMr = network.getDcNode("dnMr");
        assertVoltageEquals(0.000000, dnMr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-117.168713, g1.getTerminal());
        assertReactivePowerEquals(-30.515907, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(117.165975, l12.getTerminal1());
        assertReactivePowerEquals(30.515907, l12.getTerminal1());
        assertActivePowerEquals(-117.069597, l12.getTerminal2());
        assertReactivePowerEquals(-30.226773, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(75.037795, l25.getTerminal1());
        assertReactivePowerEquals(10.113386, l25.getTerminal1());
        assertActivePowerEquals(-75.000000, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        Line l26 = network.getLine("l26");
        assertActivePowerEquals(75.037795, l26.getTerminal1());
        assertReactivePowerEquals(10.113386, l26.getTerminal1());
        assertActivePowerEquals(-75.000000, l26.getTerminal2());
        assertReactivePowerEquals(-10.000000, l26.getTerminal2());

        DcLine dl3Mp = network.getDcLine("dl3Mp");
        assertDcPowerEquals(-26.002702, dl3Mp.getDcTerminal1());
        assertDcPowerEquals(26.001011, dl3Mp.getDcTerminal2());

        DcLine dl3Mn = network.getDcLine("dl3Mn");
        assertDcPowerEquals(-26.002702, dl3Mn.getDcTerminal1());
        assertDcPowerEquals(26.001011, dl3Mn.getDcTerminal2());

        DcLine dl3Mr = network.getDcLine("dl3Mr");
        assertDcPowerEquals(-0.000000, dl3Mr.getDcTerminal1());

        DcLine dlM4p = network.getDcLine("dlM4p");
        assertDcPowerEquals(-13.000506, dlM4p.getDcTerminal1());
        assertDcPowerEquals(13.000083, dlM4p.getDcTerminal2());

        DcLine dlM4n = network.getDcLine("dlM4n");
        assertDcPowerEquals(-13.000506, dlM4n.getDcTerminal1());
        assertDcPowerEquals(13.000083, dlM4n.getDcTerminal2());

        DcLine dlM4r = network.getDcLine("dlM4r");
        assertDcPowerEquals(-0.000000, dlM4r.getDcTerminal2());

        DcLine dlM6p = network.getDcLine("dlM6p");
        assertDcPowerEquals(-13.000506, dlM6p.getDcTerminal1());
        assertDcPowerEquals(13.000083, dlM6p.getDcTerminal2());

        DcLine dlM6n = network.getDcLine("dlM6n");
        assertDcPowerEquals(-13.000506, dlM6n.getDcTerminal1());
        assertDcPowerEquals(13.000083, dlM6n.getDcTerminal2());

        DcLine dlM6r = network.getDcLine("dlM6r");
        assertDcPowerEquals(-0.000000, dlM6r.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithoutMetallicReturn() {
        //Bipolar Model without metallic return
        Network network = AcDcNetworkFactory.createBipolarModelWithoutMetallicReturn();
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
        assertVoltageEquals(389.685263, b2);
        assertAngleEquals(-0.062575, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.371356, b5);
        assertAngleEquals(-0.163013, b5);

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
        assertActivePowerEquals(-62.078119, g1.getTerminal());
        assertReactivePowerEquals(-20.253266, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(62.078238, l12.getTerminal1());
        assertReactivePowerEquals(20.253266, l12.getTerminal1());
        assertActivePowerEquals(-62.050204, l12.getTerminal2());
        assertReactivePowerEquals(-20.169166, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(92.049970, l25.getTerminal1());
        assertReactivePowerEquals(10.169381, l25.getTerminal1());
        assertActivePowerEquals(-91.993491, l25.getTerminal2());
        assertReactivePowerEquals(-9.999944, l25.getTerminal2());

        DcLine dl3Gp = network.getDcLine("dl3Gp");
        assertDcPowerEquals(-25.000000, dl3Gp.getDcTerminal1());
        assertDcPowerEquals(24.998438, dl3Gp.getDcTerminal2());

        DcLine dlG4p = network.getDcLine("dlG4p");
        assertDcPowerEquals(-20.998018, dlG4p.getDcTerminal1());
        assertDcPowerEquals(20.996916, dlG4p.getDcTerminal2());

        DcLine dl3Gn = network.getDcLine("dl3Gn");
        assertDcPowerEquals(-25.000000, dl3Gn.getDcTerminal1());
        assertDcPowerEquals(24.998438, dl3Gn.getDcTerminal2());

        DcLine dlG4n = network.getDcLine("dlG4n");
        assertDcPowerEquals(-20.998018, dlG4n.getDcTerminal1());
        assertDcPowerEquals(20.996916, dlG4n.getDcTerminal2());

        DcLine dlGpGr = network.getDcLine("dlGpGr");
        assertDcPowerEquals(-4.000420, dlGpGr.getDcTerminal1());

        DcLine dlGnGr = network.getDcLine("dlGnGr");
        assertDcPowerEquals(-4.000420, dlGnGr.getDcTerminal2());
    }
}
