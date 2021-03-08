/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowDanglingLineTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private DanglingLine dl1;
    private Generator g1;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private Network createNetwork() {
        Network network = Network.create("dl", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("S2")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        g1 = vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        dl1 = vl2.newDanglingLine()
                .setId("dl1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setR(0.7)
                .setX(1)
                .setG(Math.pow(10, -6))
                .setB(3 * Math.pow(10, -6))
                .setP0(101)
                .setQ0(150)
                .newGeneration()
                    .setTargetP(0)
                    .setTargetQ(0)
                    .setTargetV(390)
                    .setVoltageRegulationOn(false)
                    .add()
                .add();
        network.newLine()
                .setId("l1")
                .setVoltageLevel1("vl1")
                .setBus1("b1")
                .setVoltageLevel2("vl2")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0.058104, bus1);
        assertVoltageEquals(388.582864, bus2);
        assertAngleEquals(0, bus2);
        assertActivePowerEquals(101.302, dl1.getTerminal());
        assertReactivePowerEquals(149.764, dl1.getTerminal());
    }

    @Test
    void testWithVoltageRegulationOn() {
        g1.setTargetQ(0);
        g1.setVoltageRegulatorOn(false);
        dl1.getGeneration().setVoltageRegulationOn(true);
        dl1.getGeneration().setMinP(0);
        dl1.getGeneration().setMaxP(10);
        dl1.getGeneration().newMinMaxReactiveLimits()
                .setMinQ(-100)
                .setMaxQ(100)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390.440, bus1);
        assertAngleEquals(0.114371, bus1);
        assertVoltageEquals(390.181, bus2);
        assertAngleEquals(0, bus2);
        assertActivePowerEquals(101.2, dl1.getTerminal());
        assertReactivePowerEquals(-0.202, dl1.getTerminal());

        parameters.setDistributedSlack(true)
                  .setNoGeneratorReactiveLimits(false);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());

        assertVoltageEquals(390.440, bus1);
        assertAngleEquals(0.114371, bus1);
        assertVoltageEquals(390.181, bus2);
        assertAngleEquals(0, bus2);
        assertActivePowerEquals(101.2, dl1.getTerminal());
        assertReactivePowerEquals(-0.202, dl1.getTerminal());
    }
}
