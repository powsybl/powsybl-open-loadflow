/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
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
 * LCC test case.
 *
 *  g1       ld2               ld3
 *  |         |                 |
 * b1 ------- b2-cs2--------cs3-b3
 *      l12          hvdc23     |
 *                              g3
 *
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class AcLoadFlowLccTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Generator g1;
    private Generator g3;
    private Line l12;
    private LccConverterStation cs2;
    private LccConverterStation cs3;
    private HvdcLine hvdc23;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private Network createNetwork() {
        Network network = Network.create("lcc", "test");

        Substation s1 = network.newSubstation()
                .setId("S1")
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
                .setTargetP(102.56)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(50)
                .setQ0(10)
                .add();
        cs2 = vl2.newLccConverterStation()
                .setId("cs2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setPowerFactor(0.8f)
                .setLossFactor(0.1f)
                .add();

        Substation s3 = network.newSubstation()
                .setId("S3")
                .add();
        VoltageLevel vl3 = s3.newVoltageLevel()
                .setId("vl3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        vl3.newLoad()
                .setId("ld3")
                .setConnectableBus("b3")
                .setBus("b3")
                .setP0(50)
                .setQ0(10)
                .add();
        cs3 = vl3.newLccConverterStation()
                .setId("cs3")
                .setConnectableBus("b3")
                .setBus("b3")
                .setPowerFactor(0.8f)
                .setLossFactor(1.1f)
                .add();
        g3 = vl3.newGenerator()
                .setId("g3")
                .setConnectableBus("b3")
                .setBus("b3")
                .setTargetP(102.56)
                .setTargetV(380)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        l12 = network.newLine()
                .setId("l12")
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

        hvdc23 = network.newHvdcLine()
                .setId("hvdc23")
                .setConverterStationId1("cs2")
                .setConverterStationId2("cs3")
                .setNominalV(400)
                .setR(0.1)
                .setActivePowerSetpoint(50)
                .setConvertersMode(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)
                .setMaxP(500)
                .add();

        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        this.parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(389.3763, bus2);
        assertAngleEquals(-0.095311, bus2);
        assertVoltageEquals(380, bus3);
        assertAngleEquals(0, bus3);

        assertActivePowerEquals(50.05, cs2.getTerminal());
        assertReactivePowerEquals(37.538, cs2.getTerminal());

        assertActivePowerEquals(-49.45, cs3.getTerminal());
        assertReactivePowerEquals(37.087, cs3.getTerminal());
    }
}
