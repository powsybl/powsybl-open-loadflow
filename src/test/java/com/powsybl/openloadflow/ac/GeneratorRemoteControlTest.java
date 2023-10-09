/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControlAdder;
import com.powsybl.iidm.network.extensions.RemoteReactivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class GeneratorRemoteControlTest extends AbstractLoadFlowNetworkFactory {

    private Network network;
    Substation s;
    private Bus b1;
    private Bus b2;
    private Bus b3;
    private Bus b4;
    private Generator g1;
    private Generator g2;
    private Generator g3;
    private TwoWindingsTransformer tr1;
    private TwoWindingsTransformer tr2;
    private TwoWindingsTransformer tr3;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        s = network.getSubstation("s");
        b1 = network.getBusBreakerView().getBus("b1");
        b2 = network.getBusBreakerView().getBus("b2");
        b3 = network.getBusBreakerView().getBus("b3");
        b4 = network.getBusBreakerView().getBus("b4");
        g1 = network.getGenerator("g1");
        g2 = network.getGenerator("g2");
        g3 = network.getGenerator("g3");
        tr1 = network.getTwoWindingsTransformer("tr1");
        tr2 = network.getTwoWindingsTransformer("tr2");
        tr3 = network.getTwoWindingsTransformer("tr3");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                  .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltageRemoteControl(true);
    }

    @Test
    void testWith3Generators() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(21.506559, b1);
        assertVoltageEquals(21.293879, b2);
        assertVoltageEquals(22.641227, b3);
        assertVoltageEquals(413.4, b4);
        assertReactivePowerEquals(-69.925, g1.getTerminal());
        assertReactivePowerEquals(-69.925, g2.getTerminal());
        assertReactivePowerEquals(-69.925, g3.getTerminal());
    }

    @Test
    void testWith3GeneratorsAndNonImpedantBranch() {
        // add a non impedant branch going to a load at generator 3 connection bus.
        VoltageLevel vl5 = s.newVoltageLevel()
                .setId("vl5")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("b5")
                .add();
        vl5.newLoad()
                .setId("ld5")
                .setConnectableBus("b5")
                .setBus("b5")
                .setP0(0)
                .setQ0(30)
                .add();
        network.newLine()
                .setId("ln1")
                .setConnectableBus1("b3")
                .setBus1("b3")
                .setConnectableBus2("b5")
                .setBus2("b5")
                .setR(0)
                .setX(0)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-79.892, g1.getTerminal());
        assertReactivePowerEquals(-79.892, g2.getTerminal());
        assertReactivePowerEquals(-79.892, g3.getTerminal());
    }

    @Test
    void testWithLoadConnectedToGeneratorBus() {
        // in that case we expect the generation reactive power to be equals for each of the controller buses
        b1.getVoltageLevel().newLoad()
                .setId("l")
                .setBus(b1.getId())
                .setConnectableBus(b1.getId())
                .setP0(0)
                .setQ0(10)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-73.283, g1.getTerminal());
        assertReactivePowerEquals(-73.283, g2.getTerminal());
        assertReactivePowerEquals(-73.283, g3.getTerminal());
        assertReactivePowerEquals(63.283, tr1.getTerminal1());
        assertReactivePowerEquals(73.283, tr2.getTerminal1());
        assertReactivePowerEquals(73.283, tr3.getTerminal1());

        // same test but with a more complex distribution
        g1.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(60).add();
        g2.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(30).add();
        g3.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(10).add();
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-132.094, g1.getTerminal());
        assertReactivePowerEquals(-66.047, g2.getTerminal());
        assertReactivePowerEquals(-22.015, g3.getTerminal());
        assertReactivePowerEquals(122.094, tr1.getTerminal1());
        assertReactivePowerEquals(66.047, tr2.getTerminal1());
        assertReactivePowerEquals(22.015, tr3.getTerminal1());
    }

    @Test
    void testWithShuntConnectedToGeneratorBus() {
        // in that case we expect the generation reactive power to be equals for each of the controller buses
        b1.getVoltageLevel().newShuntCompensator()
                .setId("l")
                .setBus(b1.getId())
                .setConnectableBus(b1.getId())
                .setSectionCount(1)
                .newLinearModel()
                    .setBPerSection(Math.pow(10, -2))
                    .setMaximumSectionCount(1)
                    .add()
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-68.372, g1.getTerminal());
        assertReactivePowerEquals(-68.372, g2.getTerminal());
        assertReactivePowerEquals(-68.372, g3.getTerminal());
        assertReactivePowerEquals(73.003, tr1.getTerminal1());
        assertReactivePowerEquals(68.372, tr2.getTerminal1());
        assertReactivePowerEquals(68.372, tr3.getTerminal1());
    }

    @Test
    void testWith3GeneratorsAndCoordinatedReactiveControlExtensions() {
        g1.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(60).add();
        g2.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(30).add();
        g3.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(10).add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(21.709276, b1);
        assertVoltageEquals(21.264396, b2);
        assertVoltageEquals(22.331965, b3);
        assertVoltageEquals(413.4, b4);
        assertReactivePowerEquals(-126.14, g1.getTerminal());
        assertReactivePowerEquals(-63.07, g2.getTerminal());
        assertReactivePowerEquals(-21.023, g3.getTerminal());
    }

    @Test
    void testWith3GeneratorsAndReactiveLimits() {
        // as there is no CoordinatedReactiveControl extension, reactive limit range will be used to create reactive
        // keys
        g1.newMinMaxReactiveLimits().setMinQ(0).setMaxQ(60).add();
        g2.newMinMaxReactiveLimits().setMinQ(0).setMaxQ(30).add();
        g3.newMinMaxReactiveLimits().setMinQ(0).setMaxQ(10).add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(21.709276, b1);
        assertVoltageEquals(21.264396, b2);
        assertVoltageEquals(22.331965, b3);
        assertVoltageEquals(413.4, b4);
        assertReactivePowerEquals(-126.14, g1.getTerminal());
        assertReactivePowerEquals(-63.07, g2.getTerminal());
        assertReactivePowerEquals(-21.023, g3.getTerminal());
    }

    @Test
    void testErrorWhenDifferentTargetV() {
        g3.setTargetV(413.3);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(413.4, b4); // check target voltage has been fixed to first controller one
    }

    @Test
    void testWith2Generators() {
        g3.setTargetQ(10).setVoltageRegulatorOn(false);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(21.616159, b1);
        assertVoltageEquals(21.423099, b2);
        assertVoltageEquals(22.261066, b3);
        assertVoltageEquals(413.4, b4);
        assertReactivePowerEquals(-100.189, g1.getTerminal());
        assertReactivePowerEquals(-100.189, g2.getTerminal());
        assertReactivePowerEquals(-10, g3.getTerminal());
    }

    @Test
    void testWith3GeneratorsAndFirstGeneratorToLimit() {
        parameters.setUseReactiveLimits(true);
        g1.newMinMaxReactiveLimits()
                .setMinQ(-50)
                .setMaxQ(50)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(21.433794, b1);
        assertVoltageEquals(21.337233, b2);
        assertVoltageEquals(22.704157, b3);
        assertVoltageEquals(413.4, b4);
        assertReactivePowerEquals(-50, g1.getTerminal()); // generator 1 has been correctly limited to -50 MVar
        assertReactivePowerEquals(-80.038, g2.getTerminal());
        assertReactivePowerEquals(-80.038, g3.getTerminal());
    }

    @Test
    void testWith3GeneratorsAndAnAdditionalWithLocalRegulation() {
        // create a generator on controlled bus to have "mixed" 1 local plus 3 remote generators controlling voltage
        // at bus 4
        Generator g4 = b4.getVoltageLevel()
                .newGenerator()
                .setId("g4")
                .setBus("b4")
                .setConnectableBus("b4")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-52.103, g1.getTerminal());
        assertReactivePowerEquals(-52.103, g2.getTerminal());
        assertReactivePowerEquals(-52.103, g3.getTerminal());
        assertReactivePowerEquals(-52.103, g4.getTerminal()); // local generator has the same reactive power that remote ones

        // check that distribution is correct even with 2 generators connected to local bus
        Generator g4bis = b4.getVoltageLevel()
                .newGenerator()
                .setId("g4bis")
                .setBus("b4")
                .setConnectableBus("b4")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .add();
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-41.559, g1.getTerminal());
        assertReactivePowerEquals(-41.559, g2.getTerminal());
        assertReactivePowerEquals(-41.559, g3.getTerminal());
        assertReactivePowerEquals(-41.559, g4.getTerminal());
        assertReactivePowerEquals(-41.559, g4bis.getTerminal());

        // check that of we switch g4 PQ with Q=+-10MVar, generators that still regulate voltage already have a correct
        // amount of reactive power
        g4.setTargetQ(-10)
                .setVoltageRegulatorOn(false);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-54.646, g1.getTerminal());
        assertReactivePowerEquals(-54.646, g2.getTerminal());
        assertReactivePowerEquals(-54.646, g3.getTerminal());
        assertReactivePowerEquals(10, g4.getTerminal());
        assertReactivePowerEquals(-54.646, g4bis.getTerminal());

        g4.setTargetQ(10);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-49.563, g1.getTerminal());
        assertReactivePowerEquals(-49.563, g2.getTerminal());
        assertReactivePowerEquals(-49.563, g3.getTerminal());
        assertReactivePowerEquals(-10, g4.getTerminal());
        assertReactivePowerEquals(-49.563, g4bis.getTerminal());

        // same test but with one of the remote generator switched PQ
        g4.setVoltageRegulatorOn(true);
        g2.setTargetQ(-10)
                .setVoltageRegulatorOn(false);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-54.51, g1.getTerminal());
        assertReactivePowerEquals(10, g2.getTerminal());
        assertReactivePowerEquals(-54.51, g3.getTerminal());
        assertReactivePowerEquals(-54.51, g4.getTerminal());
        assertReactivePowerEquals(-54.51, g4bis.getTerminal());

        g2.setTargetQ(10);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-49.449, g1.getTerminal());
        assertReactivePowerEquals(-10, g2.getTerminal());
        assertReactivePowerEquals(-49.449, g3.getTerminal());
        assertReactivePowerEquals(-49.449, g4.getTerminal());
        assertReactivePowerEquals(-49.449, g4bis.getTerminal());

        // try to switch off regulation of the 3 remote generators
        g1.setTargetQ(10).setVoltageRegulatorOn(false);
        g2.setTargetQ(10).setVoltageRegulatorOn(false);
        g3.setTargetQ(10).setVoltageRegulatorOn(false);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-10, g1.getTerminal());
        assertReactivePowerEquals(-10, g2.getTerminal());
        assertReactivePowerEquals(-10, g3.getTerminal());
        assertReactivePowerEquals(-88.407, g4.getTerminal());
        assertReactivePowerEquals(-88.407, g4bis.getTerminal());
    }

    @Test
    void testRemoteReactivePowerControl() {
        // create a basic 4-buses network
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g4 = network.getGenerator("g4");
        Generator g1 = network.getGenerator("g1");
        Line l34 = network.getLine("l34");
        Line l12 = network.getLine("l12");
        Line l13 = network.getLine("l13");

        double targetQ = 1.0;

        // disable voltage control on g4
        g4.setTargetQ(0).setVoltageRegulatorOn(false);

        // first test: generator g4 regulates reactive power on line 4->3 (on side of g4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
          .withTargetQ(targetQ)
          .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
          .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(targetQ, l34.getTerminal(Branch.Side.TWO));

        // second test: generator g4 regulates reactive power on line 3->4 (on the opposite side of the line)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
          .withTargetQ(targetQ)
          .withRegulatingTerminal(l34.getTerminal(Branch.Side.ONE))
          .withEnabled(true).add();

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(targetQ, l34.getTerminal(Branch.Side.ONE));

        // third test: generator g4 regulates reactive power on line 1->2 (line which is not linked to bus 4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
          .withTargetQ(targetQ)
          .withRegulatingTerminal(l12.getTerminal(Branch.Side.ONE))
          .withEnabled(true).add();

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(targetQ, l12.getTerminal(Branch.Side.ONE));
    }

    @Test
    void testDiscardedRemoteReactivePowerControls() {
        // create a basic 4-buses network
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g4 = network.getGenerator("g4");
        Line l34 = network.getLine("l34");
        l34.getTerminal1().disconnect();

        double targetQ = 1.0;

        // disable voltage control on g4
        g4.setTargetQ(0).setVoltageRegulatorOn(false);

        // first test: generator g4 regulates reactive power on line 4->3 (on side of g4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(0.0, l34.getTerminal(Branch.Side.TWO));

        l34.getTerminal2().disconnect();
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertReactivePowerEquals(Double.NaN, l34.getTerminal(Branch.Side.TWO));
    }

    @Test
    void testSharedRemoteReactivePowerControl() {
        // we create a basic 4-buses network
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Generator g4 = network.getGenerator("g4");
        Generator g1 = network.getGenerator("g1");
        Line l34 = network.getLine("l34");
        createGenerator(b2, "g2", 0);

        double targetQ = 1.0;

        // we disable the voltage control of g1 and g4
        g1.setTargetQ(0).setVoltageRegulatorOn(false);
        g4.setTargetQ(0).setVoltageRegulatorOn(false);

        // generators g1 and g4 both regulate reactive power on line 4->3
        g1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(1, l34.getTerminal(Branch.Side.TWO));

        // generators g1 and g4 both regulate reactive power on line 4->3
        g1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.ONE))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertReactivePowerEquals(1, l34.getTerminal(Branch.Side.TWO));
    }

    @Test
    void testSharedRemoteReactivePowerControl2() {
        double targetQ = 2.0;

        // generators g1 and g4 both regulate reactive power on line 4->3
        Network network1 = FourBusNetworkFactory.createWithReactiveController();
        Generator g4n1 = network1.getGenerator("g4");
        Generator g1n1 = network1.getGenerator("g1");
        Line l34n1 = network1.getLine("l34");

        g1n1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34n1.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();
        g4n1.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34n1.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);
        LoadFlowResult result1 = loadFlowRunner.run(network1, parameters);
        assertTrue(result1.isOk());
        assertReactivePowerEquals(targetQ, l34n1.getTerminal(Branch.Side.TWO));

        // only generator g1 regulates reactive power on line 4->3
        Network network2 = FourBusNetworkFactory.createWithReactiveController();
        Generator g1n2 = network2.getGenerator("g1");
        Line l34n2 = network2.getLine("l34");

        g1n2.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34n2.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        LoadFlowResult result2 = loadFlowRunner.run(network2, parameters);
        assertTrue(result2.isOk());
        assertEquals(targetQ, l34n2.getTerminal(Branch.Side.TWO).getQ(), 1E-2); // lower tolerance

        // compare runs
        // TODO: how?
    }

    @Test
    void testNotSupportedRemoteReactivePowerControl() {
        // Create a basic 4-buses network
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g4 = network.getGenerator("g4");
        Line l34 = network.getLine("l34");

        double targetQ = 1.0;

        // first test: generator g4 regulates reactive power on line 4->3 (on side of g4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(0.274417, l34.getTerminal(Branch.Side.TWO));
    }

    @Test
    void testNotSupportedRemoteReactivePowerControl2() {
        Network network = FourBusNetworkFactory.createWithTwoGeneratorsAtBus2();
        Generator g2 = network.getGenerator("g2");
        Line l34 = network.getLine("l34");

        double targetQ = 1.0;

        // generator g2 regulates reactive power on line 4->3
        // generator g5 regulates voltage
        // they are both connected to the same bus
        g2.setTargetQ(0).setVoltageRegulatorOn(false);
        g2.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(0.162232, l34.getTerminal(Branch.Side.TWO));
    }

    @Test
    void testNotSupportedRemoteReactivePowerControl3() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g4 = network.getGenerator("g4");
        Load l = network.getLoad("d2");

        double targetQ = 1.0;

        g4.setTargetQ(0).setVoltageRegulatorOn(false);
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l.getTerminal()) // not supported.
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
    }

    @Test
    void testRemoteReactivePowerControl2() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Bus nload = vlload.getBusBreakerView().getBus("NLOAD");
        vlload.newGenerator()
                .setId("GEN2")
                .setBus(nload.getId())
                .setConnectableBus(nload.getId())
                .setMinP(-9999.99D)
                .setMaxP(9999.99D)
                .setVoltageRegulatorOn(true)
                .setTargetV(150D)
                .setTargetP(0.0D)
                .setTargetQ(301.0D)
                .add();
        Generator generator2 = network.getGenerator("GEN2");
        generator2.newReactiveCapabilityCurve()
                .beginPoint()
                .setP(3.0D)
                .setMaxQ(5.0D)
                .setMinQ(4.0D)
                .endPoint()
                .beginPoint()
                .setP(0.0D)
                .setMaxQ(7.0D)
                .setMinQ(6.0D)
                .endPoint()
                .beginPoint()
                .setP(1.0D)
                .setMaxQ(5.0D)
                .setMinQ(4.0D)
                .endPoint()
                .add();

        Generator gen = network.getGenerator("GEN");
        TwoWindingsTransformer twt = network.getTwoWindingsTransformer("NGEN_NHV1");

        double targetQ = 1.0;

        gen.setTargetQ(0).setVoltageRegulatorOn(false);

        gen.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(twt.getTerminal(Branch.Side.TWO))
                .withEnabled(true).add();

        parameters.getExtension(OpenLoadFlowParameters.class).setReactivePowerRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(targetQ, twt.getTerminal(Branch.Side.TWO));
    }

    @Test
    void testReactiveRangeCheckMode() {
        parameters.setUseReactiveLimits(true);
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Bus nload = vlload.getBusBreakerView().getBus("NLOAD");
        vlload.newGenerator()
                .setId("GEN2")
                .setBus(nload.getId())
                .setConnectableBus(nload.getId())
                .setMinP(-9999.99D)
                .setMaxP(9999.99D)
                .setVoltageRegulatorOn(true)
                .setTargetV(150D)
                .setTargetP(0.0D)
                .setTargetQ(301.0D)
                .add();
        Generator generator2 = network.getGenerator("GEN2");
        generator2.newReactiveCapabilityCurve()
                .beginPoint()
                .setP(3.0D)
                .setMaxQ(100)
                .setMinQ(-100)
                .endPoint()
                .beginPoint()
                .setP(0.0D)
                .setMaxQ(100)
                .setMinQ(-100)
                .endPoint()
                .beginPoint()
                .setP(1.0D)
                .setMaxQ(5.0D)
                .setMinQ(5.0D)
                .endPoint()
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(150.0, nload);

        generator2.setTargetP(1.0);
        parameters.getExtension(OpenLoadFlowParameters.class).setReactiveRangeCheckMode(OpenLoadFlowParameters.ReactiveRangeCheckMode.TARGET_P);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());
        assertVoltageEquals(164.88, nload);

        generator2.setTargetP(0.0);
        parameters.getExtension(OpenLoadFlowParameters.class).setReactiveRangeCheckMode(OpenLoadFlowParameters.ReactiveRangeCheckMode.MIN_MAX);
        LoadFlowResult result3 = loadFlowRunner.run(network, parameters);
        assertTrue(result3.isOk());
        assertVoltageEquals(164.88, nload);
    }

    @Test
    void testWithZeroReactiveKey() {
        g1.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(0).add();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters()).get(0);
        assertTrue(lfNetwork.getGeneratorById(g1.getId()).getRemoteControlReactiveKey().isEmpty()); // zero is fixed to empty
    }
}
