/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class GeneratorRemoteControlTest extends AbstractLoadFlowNetworkFactory {

    private Network network;
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
    public void setUp() {
        network = Network.create("generator-remote-control-test", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        VoltageLevel vl4 = s.newVoltageLevel()
                .setId("vl4")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b4 = vl4.getBusBreakerView().newBus()
                .setId("b4")
                .add();
        Load l4 = vl4.newLoad()
                .setId("l4")
                .setBus("b4")
                .setConnectableBus("b4")
                .setP0(299.6)
                .setQ0(200)
                .add();
        g1 = b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4) // 22 413.4
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        g2 = b2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        g3 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l4.getTerminal())
                .add();
        tr1 = s.newTwoWindingsTransformer()
                .setId("tr1")
                .setVoltageLevel1(b1.getVoltageLevel().getId())
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .setG(0)
                .setB(0)
                .add();
        tr2 = s.newTwoWindingsTransformer()
                .setId("tr2")
                .setVoltageLevel1(b2.getVoltageLevel().getId())
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.2)
                .setRatedU2(398)
                .setR(1)
                .setX(36)
                .setG(0)
                .setB(0)
                .add();
        tr3 = s.newTwoWindingsTransformer()
                .setId("tr3")
                .setVoltageLevel1(b3.getVoltageLevel().getId())
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(21.3)
                .setRatedU2(397)
                .setR(2)
                .setX(50)
                .setG(0)
                .setB(0)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setNoGeneratorReactiveLimits(true);
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector())
                .setDistributedSlack(false)
                .setVoltageRemoteControl(true);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void testWith3Generators() {
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
    public void testWithLoadConnectedToGeneratorBus() {
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
    public void testWithShuntConnectedToGeneratorBus() {
        // in that case we expect the generation reactive power to be equals for each of the controller buses
        b1.getVoltageLevel().newShuntCompensator()
                .setId("l")
                .setBus(b1.getId())
                .setConnectableBus(b1.getId())
                .setbPerSection(Math.pow(10, -2))
                .setMaximumSectionCount(1)
                .setCurrentSectionCount(1)
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
    public void testWith3GeneratorsAndCoordinatedReactiveControlExtensions() {
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
    public void testWith3GeneratorsAndReactiveLimits() {
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
    public void testErrorWhenDifferentTargetV() {
        g3.setTargetV(413.3);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(413.4, b4); // check target voltage has been fixed to first controller one
    }

    @Test
    public void testWith2Generators() {
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
    public void testWith3GeneratorsAndFirstGeneratorToLimit() {
        parameters.setNoGeneratorReactiveLimits(false);
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
}
