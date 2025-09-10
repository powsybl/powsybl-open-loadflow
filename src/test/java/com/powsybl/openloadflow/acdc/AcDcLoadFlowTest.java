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
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class AcDcLoadFlowTest {

    @Test
    void testAcDcExample() {
        //2 converters, 1 AC Network, the first converter controls Pac, and the second one Vdc
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcDcNetwork(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        //TODO : compare with testAcDcExampleIthOtherControl
        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(-0.000000, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(389.589563, b2);
        assertAngleEquals(-0.104451, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.257475, b5);
        assertAngleEquals(-0.212879, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-99.132368, g1.getTerminal());
        assertReactivePowerEquals(-20.398041, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(99.128866, l12.getTerminal1());
        assertReactivePowerEquals(20.398041, l12.getTerminal1());
        assertActivePowerEquals(-99.061524, l12.getTerminal2());
        assertReactivePowerEquals(-20.196017, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(99.061524, l25.getTerminal1());
        assertReactivePowerEquals(10.196017, l25.getTerminal1());
        assertActivePowerEquals(-98.996185, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.498901, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497370, dl34.getDcTerminal2());
    }

    @Test
    void testAcDcExampleWithOtherControl() {
        //2 converters, 1 AC Network, the first converter controls Vdc, and the second one Pac
        Network network = AcDcNetworkFactory.createAcDcNetwork2();
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
        assertVoltageEquals(389.589559, b2);
        assertAngleEquals(-0.104453, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.257471, b5);
        assertAngleEquals(-0.212881, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-99.153669, g1.getTerminal());
        assertReactivePowerEquals(-20.398047, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(99.130174, l12.getTerminal1());
        assertReactivePowerEquals(20.398047, l12.getTerminal1());
        assertActivePowerEquals(-99.062831, l12.getTerminal2());
        assertReactivePowerEquals(-20.196018, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(99.061761, l25.getTerminal1());
        assertReactivePowerEquals(10.196018, l25.getTerminal1());
        assertActivePowerEquals(-98.996421, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.499024, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497492, dl34.getDcTerminal2());
    }

    @Test
    void testAcDcExampleGridForming() {
        //2 converters, 1 AC Network, the converter cs45 which controls Vac and Vdc is slack and reference bus for AC Network.
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
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
        assertVoltageEquals(389.589563, b2);
        assertAngleEquals(-0.104451, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.257475, b5);
        assertAngleEquals(-0.212879, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-99.132368, g1.getTerminal());
        assertReactivePowerEquals(-20.398041, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(99.128866, l12.getTerminal1());
        assertReactivePowerEquals(20.398041, l12.getTerminal1());
        assertActivePowerEquals(-99.061524, l12.getTerminal2());
        assertReactivePowerEquals(-20.196017, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(99.061524, l25.getTerminal1());
        assertReactivePowerEquals(10.196017, l25.getTerminal1());
        assertActivePowerEquals(-98.996185, l25.getTerminal2());
        assertReactivePowerEquals(-10.000000, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.498901, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497370, dl34.getDcTerminal2());
    }

    @Test
    void testThreeConverters() {
        //3 converters, 1 AC Network, cs23 controls Vdc, cs45 and cs56 control Pac
        Network network = AcDcNetworkFactory.createAcDcNetworkWithThreeConverters();
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
        assertVoltageEquals(389.694715, b2);
        assertAngleEquals(-0.077468, b2);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(389.527669, b6);
        assertAngleEquals(-0.132198, b6);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(389.527669, b5);
        assertAngleEquals(-0.132198, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.000000, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(399.980874, dn4);

        DcNode dn5 = network.getDcNode("dn5");
        assertVoltageEquals(399.980874, dn5);

        DcNode dnMiddle = network.getDcNode("dnMiddle");
        assertVoltageEquals(399.987249, dnMiddle);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-73.577847, g1.getTerminal());
        assertReactivePowerEquals(-15.211157, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(73.566460, l12.getTerminal1());
        assertReactivePowerEquals(15.211157, l12.getTerminal1());
        assertActivePowerEquals(-73.529356, l12.getTerminal2());
        assertReactivePowerEquals(-15.099847, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(50.016641, l25.getTerminal1());
        assertReactivePowerEquals(5.049924, l25.getTerminal1());
        assertActivePowerEquals(-50.000000, l25.getTerminal2());
        assertReactivePowerEquals(-5.000000, l25.getTerminal2());

        Line l26 = network.getLine("l26");
        assertActivePowerEquals(50.016641, l26.getTerminal1());
        assertReactivePowerEquals(5.049924, l26.getTerminal1());
        assertActivePowerEquals(-50.000000, l26.getTerminal2());
        assertReactivePowerEquals(-5.000000, l26.getTerminal2());

        DcLine dl3 = network.getDcLine("dl3");
        assertDcPowerEquals(-51.003007, dl3.getDcTerminal1());
        assertDcPowerEquals(51.001382, dl3.getDcTerminal2());

        DcLine dl4 = network.getDcLine("dl4");
        assertDcPowerEquals(-25.500691, dl4.getDcTerminal1());
        assertDcPowerEquals(25.500284, dl4.getDcTerminal2());

        DcLine dl5 = network.getDcLine("dl5");
        assertDcPowerEquals(-25.500691, dl5.getDcTerminal1());
        assertDcPowerEquals(25.500284, dl5.getDcTerminal2());
    }

    @Test
    void testAcVoltageControl() {
        //2 converters, 1 AC Network, cs23 controls Pac, cs45 controls Vdc and Vac, but is not a slack
        Network network = AcDcNetworkFactory.createAcDcNetworkWithAcVoltageControl();
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
        assertVoltageEquals(394.953936, b2);
        assertAngleEquals(-0.369419, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(-0.733230, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(400.012374, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-105.093825, g1.getTerminal());
        assertReactivePowerEquals(677.962724, g1.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(105.054833, l12.getTerminal1());
        assertReactivePowerEquals(-677.962724, l12.getTerminal1());
        assertActivePowerEquals(-101.960356, l12.getTerminal2());
        assertReactivePowerEquals(687.246155, l12.getTerminal2());

        Line l25 = network.getLine("l25");
        assertActivePowerEquals(101.960356, l25.getTerminal1());
        assertReactivePowerEquals(-697.246155, l25.getTerminal1());
        assertActivePowerEquals(-98.777123, l25.getTerminal2());
        assertReactivePowerEquals(706.795855, l25.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(-49.498886, dl34.getDcTerminal1());
        assertDcPowerEquals(49.497355, dl34.getDcTerminal2());
    }

    @Test
    void testAcSubNetworks() {
        //2 converters, 2 AC Networks, cs23 controls Pac and Vac, cs45 controls Vdc and Vac, the converters set the slack and reference buses
        Network network = AcDcNetworkFactory.createAcDcNetworkWithAcSubNetworks();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.CONVERTERS)
                .setAcDcNetwork(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        Bus b1 = network.getBusBreakerView().getBus("b1");
        assertVoltageEquals(390.000000, b1);
        assertAngleEquals(0.575985, b1);

        Bus b2 = network.getBusBreakerView().getBus("b2");
        assertVoltageEquals(400.000000, b2);
        assertAngleEquals(0.000000, b2);

        Bus b5 = network.getBusBreakerView().getBus("b5");
        assertVoltageEquals(400.000000, b5);
        assertAngleEquals(0.000000, b5);

        DcNode dn3 = network.getDcNode("dn3");
        assertVoltageEquals(399.987179, dn3);

        DcNode dn4 = network.getDcNode("dn4");
        assertVoltageEquals(400.000000, dn4);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-81.252710, g1.getTerminal());
        assertReactivePowerEquals(1324.456716, g1.getTerminal());

        Generator g5 = network.getGenerator("g5");
        assertActivePowerEquals(-28.692710, g5.getTerminal());
        assertReactivePowerEquals(-0.000000, g5.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(81.252710, l12.getTerminal1());
        assertReactivePowerEquals(-1324.456716, l12.getTerminal1());
        assertActivePowerEquals(-69.676198, l12.getTerminal2());
        assertReactivePowerEquals(1359.186254, l12.getTerminal2());

        DcLine dl34 = network.getDcLine("dl34");
        assertDcPowerEquals(51.284132, dl34.getDcTerminal1());
        assertDcPowerEquals(-51.285776, dl34.getDcTerminal2());
    }

    @Test
    void testDcSubNetworks() {
        //2 converters, 3 AC Network, 2 DC Networks, the converters set Vac and set slack and reference buses

        Network network = AcDcNetworkFactory.createAcDcNetworkDcSubNetworks();
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
        assertVoltageEquals(390.064045, b4);
        assertAngleEquals(0.028248, b4);

        Bus b6 = network.getBusBreakerView().getBus("b6");
        assertVoltageEquals(390.064045, b6);
        assertAngleEquals(0.028248, b6);

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
        assertActivePowerEquals(-38.070756, g5.getTerminal());
        assertReactivePowerEquals(-0.000000, g5.getTerminal());

        Line l45 = network.getLine("l45");
        assertActivePowerEquals(25.000000, l45.getTerminal1());
        assertReactivePowerEquals(0.000000, l45.getTerminal1());
        assertActivePowerEquals(-24.995892, l45.getTerminal2());
        assertReactivePowerEquals(0.012323, l45.getTerminal2());

        Line l56 = network.getLine("l56");
        assertActivePowerEquals(-24.995892, l56.getTerminal1());
        assertReactivePowerEquals(0.012323, l56.getTerminal1());
        assertActivePowerEquals(25.000000, l56.getTerminal2());
        assertReactivePowerEquals(0.000000, l56.getTerminal2());

        DcLine dl23 = network.getDcLine("dl23");
        assertDcPowerEquals(24.499332, dl23.getDcTerminal1());
        assertDcPowerEquals(-24.499707, dl23.getDcTerminal2());

        DcLine dl78 = network.getDcLine("dl78");
        assertDcPowerEquals(-24.499707, dl78.getDcTerminal1());
        assertDcPowerEquals(24.499332, dl78.getDcTerminal2());
    }
}
