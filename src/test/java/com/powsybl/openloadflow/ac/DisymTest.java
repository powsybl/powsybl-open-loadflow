package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.Extensions.iidm.LineAsymmetrical;
import com.powsybl.openloadflow.network.Extensions.iidm.LineAsymmetricalAdder;
import com.powsybl.openloadflow.network.Extensions.iidm.LoadUnbalancedAdder;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.TwoBusNetworkFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DisymTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;
    private Line line1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Test
    void baseCaseTest() {

        network = TwoBusNetworkFactory.create();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        line1 = network.getLine("l12");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(1, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(0.855, bus2);
        assertAngleEquals(-13.520904, bus2);
        assertActivePowerEquals(2, line1.getTerminal1());
        assertReactivePowerEquals(1.683, line1.getTerminal1());
        assertActivePowerEquals(-2, line1.getTerminal2());
        assertReactivePowerEquals(-1, line1.getTerminal2());
    }

    @Test
    void fourNodesBalancedTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setDisym(false);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.79736062173895, bus2);
        assertAngleEquals(-0.11482430885268813, bus2);
        assertVoltageEquals(99.54462759204546, bus3);
        assertAngleEquals(-0.2590112700040258, bus3);
        assertVoltageEquals(99.29252809145005, bus4);
        assertAngleEquals(-0.40393118155914964, bus4);
        assertActivePowerEquals(9.999999463827846, line1.getTerminal1());
        assertReactivePowerEquals(10.141989227123105, line1.getTerminal1());
        assertActivePowerEquals(-9.999999463827846, line1.getTerminal2());
        assertReactivePowerEquals(-10.101417240171545, line1.getTerminal2());
    }

    @Test
    void fourNodesDissymTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        Line line23 = network.getLine("B2_B3");
        double coeff = 1.; //0.50001; // TODO : singular matrix when coef = 0.5 ????
        line23.setX(coeff * 1 / 0.2);

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setDisym(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.7971047825933, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.45937102112217, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(99.2070528211056, bus4); // balanced = 99.29252809145005
    }

    @Test
    void fourNodesDissymUnbalancedLoadLineTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        Line line23 = network.getLine("B2_B3");
        double coeff = 1.; //0.50001; // TODO : singular matrix when coef = 0.5 ????
        line23.setX(coeff * 1 / 0.2);

        Load load4 = network.getLoad("LOAD_4");

        load4.newExtension(LoadUnbalancedAdder.class)
                .withPa(0.)
                .withQa(10.)
                .withPb(0.)
                .withQb(0.)
                .withPc(0.)
                .withQc(0.)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setDisym(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.72834946229246, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.2189311203843, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(98.88078638550749, bus4); // balanced = 99.29252809145005
    }

    @Test
    void fourNodesDissymUnbalancedLoadTest() {

        network = fourNodescreate();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");
        line1 = network.getLine("B1_B2");

        Line line23 = network.getLine("B2_B3");
        double coeff = 1.;
        line23.setX(coeff * 1 / 0.2);

        Line line23fault = network.getLine("B2_B3_fault");
        var extension = line23fault.getExtension(LineAsymmetrical.class);
        extension.setOpenPhaseA(false);

        Load load4 = network.getLoad("LOAD_4");

        load4.newExtension(LoadUnbalancedAdder.class)
                .withPa(20.)
                .withQa(0.)
                .withPb(40.)
                .withQb(0.)
                .withPc(21)
                .withQc(0.)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setDisym(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(100., bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(99.78067026758131, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(99.5142639108648, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(99.2565397779297, bus4); // balanced = 99.29252809145005
    }

    public static Network fourNodescreate() {
        // Proposed network to be tested
        // The grid is balanced except l23_fault which has phase C disconnected in parallel to line l23 which stays connected
        // We use a parallel line because in this use case we would like to avoid issues linked to the loss of connexity
        //
        //       1         2        3        4
        //       |---------|========|--------|
        //  (~)--|---------|========|--------|--[X]
        //       |---------|==----==|--------|
        //                    \  /
        //

        Network network = Network.create("4n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        // Bus 1
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);

        Generator gen1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(200)
                .setTargetP(10)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        // Bus 2
        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);

        // Bus 3
        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(100.0).setAngle(0.);

        // Bus 4
        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(100.0).setAngle(0.);
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus(bus4.getId())
                .setP0(10.0)
                .setQ0(10.)
                .add();

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line line23 = network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line line23fault = network.newLine()
                .setId("B2_B3_fault")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // addition of asymmetrical extensions
        line23fault.newExtension(LineAsymmetricalAdder.class)
                .withIsOpenA(true) // TODO : activate this to have unbalanced grid
                .withIsOpenB(false)
                .withIsOpenC(false)
                .add();

        line23fault.newExtension(LineFortescueAdder.class)
                .withRz(0)
                .withXz(line23fault.getX())
                .add();

        // addition of asymmetrical extensions
        gen1.newExtension(GeneratorFortescueAdder.class)
                .withRz(0.)
                .withXz(0.1)
                .withRn(0.)
                .withXn(0.1)
                .add();

        return network;
    }

}
