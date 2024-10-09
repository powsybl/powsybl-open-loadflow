/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.BoundaryFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcLoadFlowBoundaryTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private DanglingLine dl1;
    private Generator g1;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = BoundaryFactory.create();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        dl1 = network.getDanglingLine("dl1");
        g1 = network.getGenerator("g1");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

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
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(390.440, bus1);
        assertAngleEquals(0.114371, bus1);
        assertVoltageEquals(390.181, bus2);
        assertAngleEquals(0, bus2);
        assertActivePowerEquals(101.2, dl1.getTerminal());
        assertReactivePowerEquals(-0.202, dl1.getTerminal());

        parameters.setDistributedSlack(true)
                  .setUseReactiveLimits(true);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());

        assertVoltageEquals(390.440, bus1);
        assertAngleEquals(0.114371, bus1);
        assertVoltageEquals(390.181, bus2);
        assertAngleEquals(0, bus2);
        assertActivePowerEquals(101.2, dl1.getTerminal());
        assertReactivePowerEquals(-0.202, dl1.getTerminal());
    }

    @Test
    void testWithXnodeDistributedSlack() {
        parameters.setUseReactiveLimits(true);
        parameters.setDistributedSlack(true);
        testWithXnode();
    }

    @Test
    void testWithXnodeAreaInterchangeControl() {
        parameters.setUseReactiveLimits(true);
        parametersExt.setAreaInterchangeControl(true);
        testWithXnode();
    }

    void testWithXnode() {
        network = BoundaryFactory.createWithXnode();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(400.000, network.getBusBreakerView().getBus("b1"));
        assertVoltageEquals(399.999, network.getBusBreakerView().getBus("xnode"));
        assertVoltageEquals(399.999, network.getBusBreakerView().getBus("b3"));
        assertVoltageEquals(400.000, network.getBusBreakerView().getBus("b4"));
    }

    @Test
    void testWithTieLineDistributedSlack() {
        parameters.setUseReactiveLimits(true);
        parameters.setDistributedSlack(true);
        testWithTieLine();
    }

    @Test
    void testWithTieLineAreaInterchangeControl() {
        parameters.setUseReactiveLimits(true);
        parametersExt.setAreaInterchangeControl(true);
        testWithTieLine();
    }

    void testWithTieLine() {
        network = BoundaryFactory.createWithTieLine();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(400.000, network.getBusBreakerView().getBus("b1"));
        assertVoltageEquals(399.999, network.getBusBreakerView().getBus("b3"));
        assertVoltageEquals(400.000, network.getBusBreakerView().getBus("b4"));
        assertReactivePowerEquals(0.0044, network.getLine("l34").getTerminal2());

        TieLine line = network.getTieLine("t12");
        line.getDanglingLine1().getTerminal().disconnect();
        line.getDanglingLine1().getTerminal().disconnect();
        loadFlowRunner.run(network, parameters);
        assertVoltageEquals(400.0, network.getBusBreakerView().getBus("b3"));
        assertReactivePowerEquals(-0.00125, network.getLine("l34").getTerminal2());
    }

    @Test
    void testEquivalentBranch() {
        network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        network.newLine()
                .setId("LINE_23")
                .setBus1("BUS_2")
                .setBus2("BUS_3")
                .setR(0.0)
                .setX(100)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(135.0, network.getBusBreakerView().getBus("BUS_1"));
        assertVoltageEquals(127.198, network.getBusBreakerView().getBus("BUS_2"));
        assertVoltageEquals(40.19, network.getBusBreakerView().getBus("BUS_3"));
    }

    @Test
    void testWithNonImpedantDanglingLine() {
        dl1.setR(0.0).setX(0.0);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(101.0, dl1.getTerminal());
        assertReactivePowerEquals(150.0, dl1.getTerminal());

        dl1.getGeneration().setVoltageRegulationOn(true);
        dl1.getGeneration().setTargetV(390.0);
        dl1.getGeneration().setMinP(0);
        dl1.getGeneration().setMaxP(10);
        dl1.getGeneration().newMinMaxReactiveLimits()
                .setMinQ(-100)
                .setMaxQ(100)
                .add();
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());
        assertActivePowerEquals(101.0, dl1.getTerminal());
        assertReactivePowerEquals(-33.888, dl1.getTerminal());

        parameters.setDc(true);
        LoadFlowResult result3 = loadFlowRunner.run(network, parameters);
        assertTrue(result3.isFullyConverged());
        assertActivePowerEquals(101.0, dl1.getTerminal());
        assertReactivePowerEquals(Double.NaN, dl1.getTerminal());
    }

    @Test
    void testDanglingLineShuntAdmittance() {
        // verify dangling line shunt admittance is correctly accounted to be completely on network side (and not split with boundary side)

        // setup zero flows flow at dangling line boundary side
        dl1.setP0(0.0).setQ0(0.0).getGeneration().setTargetP(0.0).setTargetQ(0.0).setVoltageRegulationOn(false);

        // set higher B and G shunt values, and also much higher series impedance, so we would get very different results if the shunt admittance were split
        dl1.setB(1e-3).setG(1e-4).setR(3.).setX(30.);

        // set g1 to regulate dl1 terminal at 400.0 kV
        g1.setRegulatingTerminal(dl1.getTerminal()).setTargetV(400.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertVoltageEquals(400.0, bus2);
        assertActivePowerEquals(16., dl1.getTerminal()); // v^2 * B_shunt
        assertReactivePowerEquals(-160., dl1.getTerminal()); // - v^2 * G_shunt
    }
}
