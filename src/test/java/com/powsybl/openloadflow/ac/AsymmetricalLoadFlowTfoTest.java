package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerFortescueAdder;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetricalAdder;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsymmetricalLoadFlowTfoTest {

    private Network network;
    private Bus bus0;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;
    private Line line1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Test
    void fiveNodeTest() {
        network = fiveNodescreate();
        bus0 = network.getBusBreakerView().getBus("B0");
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
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(10., bus0);
        assertAngleEquals(0, bus0);
        assertVoltageEquals(109.4843854548432, bus1);
        assertAngleEquals(-0.018721076877077133, bus1);
        assertVoltageEquals(109.29944440302664, bus2); // balanced = 99.79736062173895
        assertVoltageEquals(108.99189289657092, bus3); // balanced = 99.54462759204546
        assertVoltageEquals(108.7617902085402, bus4); // balanced = 99.29252809145005
    }

    public static Network fiveNodescreate() {
        // Proposed network to be tested
        // The grid is balanced except l23_fault which has phase C disconnected in parallel to line l23 which stays connected
        // We use a parallel line because in this use case we would like to avoid issues linked to the loss of connexity
        //
        //       0          1         2        3        4
        //       |---(())---|---------|========|--------|
        //  (~)--|---(())---|---------|========|--------|--[X]
        //       |---(())---|---------|==----==|--------|
        //                               \  /
        //

        Network network = Network.create("4n", "test");
        network.setCaseDate(DateTime.parse("2018-03-05T13:30:30.486+01:00"));

        Substation substation01 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();

        // Bus 0
        VoltageLevel vl0 = substation01.newVoltageLevel()
                .setId("VL_0")
                .setNominalV(10.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(30)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus0 = vl0.getBusBreakerView().newBus()
                .setId("B0")
                .add();
        bus0.setV(10.0).setAngle(0.);

        Generator gen0 = vl0.newGenerator()
                .setId("G0")
                .setBus(bus0.getId())
                .setMinP(-10.0)
                .setMaxP(200)
                .setTargetP(10)
                .setTargetV(10.0)
                .setVoltageRegulatorOn(true)
                .add();

        // Bus 1
        VoltageLevel vl1 = substation01.newVoltageLevel()
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

        double ratedU0 = 10.5;
        double ratedU1 = 115;
        double rho01 = ratedU1 * ratedU1 / (ratedU0 * ratedU0);
        double rT01 = 2.046454 / rho01;
        double xT01 = 49.072241 / rho01;
        var t01 = substation01.newTwoWindingsTransformer()
                .setId("T2W_B0_B1")
                .setVoltageLevel1(vl0.getId())
                .setBus1(bus0.getId())
                .setConnectableBus1(bus0.getId())
                .setRatedU1(10.5)
                .setVoltageLevel2(vl1.getId())
                .setBus2(bus1.getId())
                .setConnectableBus2(bus1.getId())
                .setRatedU2(115)
                .setR(rT01)
                .setX(xT01)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(31.5)
                .add();

        // addition of asymmetrical extensions
        line23fault.newExtension(LineAsymmetricalAdder.class)
                .add();

        line23fault.newExtension(LineFortescueAdder.class)
                .withOpenPhaseA(true)
                .withOpenPhaseB(false)
                .withOpenPhaseC(false)
                .withRz(0)
                .withXz(line23fault.getX())
                .add();

        gen0.newExtension(GeneratorFortescueAdder.class)
                .withRz(0.)
                .withXz(0.1)
                .withRn(0.)
                .withXn(0.1)
                .add();

        t01.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(rT01 / 3)
                .withXz(xT01 / 3)
                .withConnectionType1(WindingConnectionType.Y_GROUNDED)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .withGroundingX1(0.001)
                .withGroundingX2(0.002)
                .withFreeFluxes(false)
                .add();

        return network;
    }
}
