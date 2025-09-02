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

public class AcDcBipolarTest {
    @Test
    void testBipolarModel() {
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
        assertAngleEquals(-0.069366, b2);

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
        assertActivePowerEquals(-68.100735, g1.getTerminal());
        assertReactivePowerEquals(-20.291607, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(68.092969, l12.getTerminal1());
        assertReactivePowerEquals(20.291607, l12.getTerminal1());
        assertActivePowerEquals(-68.059778, l12.getTerminal2());
        assertReactivePowerEquals(-20.192033, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(98.059778, l25.getTerminal1());
        assertReactivePowerEquals(10.192033, l25.getTerminal1());
        assertActivePowerEquals(-97.995767, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499718, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.499718, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelGridForming() {
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
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
        assertVoltageEquals(389.669599, b2);
        assertAngleEquals(-0.069366, b2);

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
        assertActivePowerEquals(-68.100735, g1.getTerminal());
        assertReactivePowerEquals(-20.291607, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(68.092969, l12.getTerminal1());
        assertReactivePowerEquals(20.291607, l12.getTerminal1());
        assertActivePowerEquals(-68.059778, l12.getTerminal2());
        assertReactivePowerEquals(-20.192033, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(98.059778, l25.getTerminal1());
        assertReactivePowerEquals(10.192033, l25.getTerminal1());
        assertActivePowerEquals(-97.995767, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499718, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.499718, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithAcSubNetworks() {
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
        assertActivePowerEquals(-34.005046, g1.getTerminal());
        assertReactivePowerEquals(-10.019724, g1.getTerminal());

        Generator g5 = network.getGenerator("g5");
        assertActivePowerEquals(-34.005046, g5.getTerminal());
        assertReactivePowerEquals(-10.000000, g5.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(-29.993425, l12.getTerminal1());
        assertReactivePowerEquals(10.019724, l12.getTerminal1());
        assertActivePowerEquals(30.000000, l12.getTerminal2());
        assertReactivePowerEquals(-10.000000, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499718, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.499718, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithAcSubNetworksGridForming() {
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
        assertAngleEquals(0.573152, b1);

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
        assertActivePowerEquals(-78.931209, g1.getTerminal());
        assertReactivePowerEquals(1323.708663, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(78.931209, l12.getTerminal1());
        assertReactivePowerEquals(-1323.708663, l12.getTerminal1());
        assertActivePowerEquals(-67.370165, l12.getTerminal2());
        assertReactivePowerEquals(1358.391795, l12.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499654, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498154, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-23.717606, dl34n.getDcTerminal1());
        assertDcPowerEquals(23.716200, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000002, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000002, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelWithOtherControl() {
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModelWithOtherControl();
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
        assertVoltageEquals(389.669532, b2);
        assertAngleEquals(-0.069366, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.334917, b5);
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
        assertActivePowerEquals(-68.101114, g1.getTerminal());
        assertReactivePowerEquals(-20.299479, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(68.095537, l12.getTerminal1());
        assertReactivePowerEquals(20.299479, l12.getTerminal1());
        assertActivePowerEquals(-68.062341, l12.getTerminal2());
        assertReactivePowerEquals(-20.199892, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(100.066631, l25.getTerminal1());
        assertReactivePowerEquals(10.199892, l25.getTerminal1());
        assertActivePowerEquals(-100.000000, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-25.501906, dl34p.getDcTerminal1());
        assertDcPowerEquals(25.500280, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-25.501906, dl34n.getDcTerminal1());
        assertDcPowerEquals(25.500280, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }

    @Test
    void testBipolarModelThreeConverters() {
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
        assertAngleEquals(0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.465706, b2);
        assertAngleEquals(-0.121080, b2);

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
        assertVoltageEquals(0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(199.980498, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-199.980498, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(-0.000000, dn4r);

        DcNode dn6p = network.getDcNode("dn6p");
        assertVoltageEquals(199.980498, dn6p);

        DcNode dn6n = network.getDcNode("dn6n");
        assertVoltageEquals(-199.980498, dn6n);

        DcNode dn6r = network.getDcNode("dn6r");
        assertVoltageEquals(-0.000000, dn6r);

        DcNode dnMp = network.getDcNode("dnMp");
        assertVoltageEquals(199.986999, dnMp);

        DcNode dnMn = network.getDcNode("dnMn");
        assertVoltageEquals(-199.986999, dnMn);

        DcNode dnMr = network.getDcNode("dnMr");
        assertVoltageEquals(0.000000, dnMr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-117.174008, g1.getTerminal());
        assertReactivePowerEquals(-30.515908, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(117.166210, l12.getTerminal1());
        assertReactivePowerEquals(30.515908, l12.getTerminal1());
        assertActivePowerEquals(-117.069831, l12.getTerminal2());
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
        assertDcPowerEquals(-26.002677, dl3Mp.getDcTerminal1());
        assertDcPowerEquals(26.000986, dl3Mp.getDcTerminal2());

        DcLine dl3Mn = network.getDcLine("dl3Mn");
        assertDcPowerEquals(-26.002677, dl3Mn.getDcTerminal1());
        assertDcPowerEquals(26.000986, dl3Mn.getDcTerminal2());

        DcLine dl3Mr = network.getDcLine("dl3Mr");
        assertDcPowerEquals(-0.000000, dl3Mr.getDcTerminal1());

        DcLine dlM4p = network.getDcLine("dlM4p");
        assertDcPowerEquals(-13.000493, dlM4p.getDcTerminal1());
        assertDcPowerEquals(13.000071, dlM4p.getDcTerminal2());

        DcLine dlM4n = network.getDcLine("dlM4n");
        assertDcPowerEquals(-13.000493, dlM4n.getDcTerminal1());
        assertDcPowerEquals(13.000071, dlM4n.getDcTerminal2());

        DcLine dlM4r = network.getDcLine("dlM4r");
        assertDcPowerEquals(-0.000000, dlM4r.getDcTerminal2());

        DcLine dlM6p = network.getDcLine("dlM6p");
        assertDcPowerEquals(-13.000493, dlM6p.getDcTerminal1());
        assertDcPowerEquals(13.000071, dlM6p.getDcTerminal2());

        DcLine dlM6n = network.getDcLine("dlM6n");
        assertDcPowerEquals(-13.000493, dlM6n.getDcTerminal1());
        assertDcPowerEquals(13.000071, dlM6n.getDcTerminal2());

        DcLine dlM6r = network.getDcLine("dlM6r");
        assertDcPowerEquals(-0.000000, dlM6r.getDcTerminal2());
    }

    @Test
    void testBipolarModelAcVoltageControl() {
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
        assertAngleEquals(-0.332746, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.693438, b5);

        DcNode dn3p = network.getDcNode("dn3p");
        assertVoltageEquals(200.012249, dn3p);

        DcNode dn3n = network.getDcNode("dn3n");
        assertVoltageEquals(-200.012249, dn3n);

        DcNode dn3r = network.getDcNode("dn3r");
        assertVoltageEquals(0.000000, dn3r);

        DcNode dn4p = network.getDcNode("dn4p");
        assertVoltageEquals(200.000000, dn4p);

        DcNode dn4n = network.getDcNode("dn4n");
        assertVoltageEquals(-200.000000, dn4n);

        DcNode dn4r = network.getDcNode("dn4r");
        assertVoltageEquals(0.000000, dn4r);

        DcNode dnGr = network.getDcNode("dnGr");
        assertVoltageEquals(0.000000, dnGr);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-73.971640, g1.getTerminal());
        assertReactivePowerEquals(672.834829, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(73.930317, l12.getTerminal1());
        assertReactivePowerEquals(-672.834829, l12.getTerminal1());
        assertActivePowerEquals(-70.918006, l12.getTerminal2());
        assertReactivePowerEquals(681.871760, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(100.918006, l25.getTerminal1());
        assertReactivePowerEquals(-691.871760, l25.getTerminal1());
        assertActivePowerEquals(-97.784605, l25.getTerminal2());
        assertReactivePowerEquals(701.271963, l25.getTerminal2());

        DcLine dl34p = network.getDcLine("dl34p");
        assertDcPowerEquals(-24.499718, dl34p.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34p.getDcTerminal2());

        DcLine dl34n = network.getDcLine("dl34n");
        assertDcPowerEquals(-24.499718, dl34n.getDcTerminal1());
        assertDcPowerEquals(24.498218, dl34n.getDcTerminal2());

        DcLine dl3Gr = network.getDcLine("dl3Gr");
        assertDcPowerEquals(-0.000000, dl3Gr.getDcTerminal1());

        DcLine dlG4r = network.getDcLine("dlG4r");
        assertDcPowerEquals(-0.000000, dlG4r.getDcTerminal2());
    }
}
