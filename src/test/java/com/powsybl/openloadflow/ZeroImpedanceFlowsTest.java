/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;

import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Luma Zamarreño <zamarrenolm at aia.es>
 * @author José Antonio Marqués <marquesja at aia.es>
 */
class ZeroImpedanceFlowsTest extends AbstractLoadFlowNetworkFactory {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void threeBusesZeroImpedanceLineTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.00, 0.5);
        createLoad(b3, "l2", 0.99, 0.5);
        createLine(network, b1, b2, "l12", 0.1);
        Line l23 = createLine(network, b2, b3, "l23", 0.0);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.99, 1, l23.getTerminal1(), -1.99, -1, l23.getTerminal2());
    }

    @Test
    void threeBusesZeroImpedanceParallelLinesTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceParallelLines", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        Line l23 = createLine(network, b2, b3, "l23", 0.0);
        Line l23p = createLine(network, b2, b3, "l23p", 0.0);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.99, 1, l23.getTerminal1(), -1.99, -1, l23.getTerminal2());
        checkFlows(0.0, 0.0, l23p.getTerminal1(), 0.0, 0.0, l23p.getTerminal2());
    }

    /**
     *   g1             --- b4
     *   |            /     |
     *  b1 --- b2--- b3     |
     *                \     |
     *                  --- b5
     */
    @Test
    void fiveBusesZeroImpedanceLineTest() {
        Network network = Network.create("FiveBusesWithZeroImpedanceLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.0, 1);
        createLoad(b4, "l2", 1.4, 0.4);
        createLoad(b5, "l3", 1.5, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        createLine(network, b2, b3, "l23", 0.01);
        Line l34 = createLine(network, b3, b4, "l34", 0.0);
        Line l35 = createLine(network, b3, b5, "l35", 0.0);
        Line l45 = createLine(network, b4, b5, "l45", 0.0);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.4, 0.4, l34.getTerminal1(), -1.4, -0.4, l34.getTerminal2());
        checkFlows(1.5, 0.5, l35.getTerminal1(), -1.5, -0.5, l35.getTerminal2());
        checkFlows(0.0, 0.0, l45.getTerminal1(), 0.0, 0.0, l45.getTerminal2());
    }

    @Test
    void threeBusesZeroImpedanceTwoWindingsTransformerTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceTwoWindingsTransformer", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        TwoWindingsTransformer t23 = createTransformer(network, "s", b2, b3, "t23", 0, 1.1);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.99, 1, t23.getTerminal1(), -1.99, -1, t23.getTerminal2());
    }

    @Test
    void fiveBusesZeroImpedanceTwoWindingsTransforemrTest() {
        Network network = Network.create("FiveBusesWithZeroImpedanceTwoWindingsTransformer", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "s", "b3");
        Bus b4 = createBus(network, "s", "b4");
        Bus b5 = createBus(network, "s", "b5");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.5, 1);
        createLoad(b4, "l2", 1.4, 0.4);
        createLoad(b5, "l3", 1.5, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        createLine(network, b2, b3, "l23", 0.01);
        TwoWindingsTransformer t34 = createTransformer(network, "s", b3, b4, "t34", 0, 1.1);
        TwoWindingsTransformer t35 = createTransformer(network, "s", b3, b5, "t35", 0, 1.1);
        TwoWindingsTransformer t45 = createTransformer(network, "s", b4, b5, "t45", 0, 1.1);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.4, 0.4, t34.getTerminal1(), -1.4, -0.4, t34.getTerminal2());
        checkFlows(1.5, 0.5, t35.getTerminal1(), -1.5, -0.5, t35.getTerminal2());
        checkFlows(0.0, 0.0, t45.getTerminal1(), 0.0, 0.0, t45.getTerminal2());
    }

    @Test
    void fourBusesZeroImpedanceThreeWindingsTransformerTest() {
        Network network = Network.create("FourBusesWithZeroImpedanceThreeWindingsTransformer", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        Bus b4 = createBus(network, "s", "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.9, 1);
        createLoad(b4, "l2", 1.0, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        ThreeWindingsTransformer t234 = createThreeWindingsTransformer(network, "s", b2, b3, b4, "t234", 0.0, 1.1, 0.0, 1.02, 0.0, 1.01);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(2.9, 1.5, t234.getLeg1().getTerminal(), -1.9, -1, t234.getLeg2().getTerminal(), -1.0, -0.5, t234.getLeg3().getTerminal());
    }

    @Test
    void fourBusesZeroImpedanceLeg23ThreeWindingsTransformerTest() {
        Network network = Network.create("FourBusesWithZeroImpedanceLeg23ThreeWindingsTransformer", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        Bus b4 = createBus(network, "s", "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.9, 1);
        createLoad(b4, "l2", 1.0, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        ThreeWindingsTransformer t234 = createThreeWindingsTransformer(network, "s", b2, b3, b4, "t234", 0.01, 1.1, 0.0, 1.02, 0.0, 1.01);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(2.9, 1.639, t234.getLeg1().getTerminal(), -1.9, -1, t234.getLeg2().getTerminal(), -1.0, -0.5, t234.getLeg3().getTerminal());
    }

    @Test
    void fourBusesZeroImpedanceLeg3ThreeWindingsTransformerTest() {
        Network network = Network.create("FourBusesWithZeroImpedanceLeg3ThreeWindingsTransformer", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        Bus b4 = createBus(network, "s", "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.9, 1);
        createLoad(b4, "l2", 1.0, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        ThreeWindingsTransformer t234 = createThreeWindingsTransformer(network, "s", b2, b3, b4, "t234", 0.01, 1.1, 0.02, 1.02, 0.0, 1.01);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(2.9, 1.7732, t234.getLeg1().getTerminal(), -1.9, -1, t234.getLeg2().getTerminal(), -1.0, -0.5, t234.getLeg3().getTerminal());
    }

    @Test
    void threeBusesZeroImpedanceDanglingLineTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceDanglingLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.9, 1);
        createLine(network, b1, b2, "l12", 0.01);
        createLine(network, b2, b3, "l23", 0.01);
        DanglingLine dl3 = createDanglingLine(b3, "d3", 0.0, 1.5, 0.5);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertActivePowerEquals(1.5, dl3.getTerminal());
        assertReactivePowerEquals(0.5, dl3.getTerminal());
    }

    @Test
    void nineBusesZeroImpedanceLineTest() {
        Network network = Network.create("NineBusesWithZeroImpedanceLine", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        Bus b8 = createBus(network, "b8");
        Bus b9 = createBus(network, "b9");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 0.5, 0.1);
        createLoad(b4, "l2", 0.6, 0.2);
        createLoad(b7, "l3", 0.7, 0.3);
        createLoad(b8, "l4", 0.8, 0.4);
        createLoad(b9, "l5", 0.9, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        createLine(network, b2, b3, "l23", 0.01);
        Line l34 = createLine(network, b3, b4, "l34", 0.0);
        Line l35 = createLine(network, b3, b5, "l35", 0.0);
        Line l45 = createLine(network, b4, b5, "l45", 0.0);
        Line l46 = createLine(network, b4, b6, "l46", 0.0);
        Line l47 = createLine(network, b4, b7, "l47", 0.0);
        Line l67 = createLine(network, b6, b7, "l67", 0.0);
        Line l68 = createLine(network, b6, b8, "l68", 0.0);
        Line l69 = createLine(network, b6, b9, "l69", 0.0);
        Line l89 = createLine(network, b8, b9, "l89", 0.0);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(3.0, 1.4, l34.getTerminal1(), -3.0, -1.4, l34.getTerminal2());
        checkFlows(0.0, 0.0, l35.getTerminal1(), 0.0, 0.0, l35.getTerminal2());
        checkFlows(0.0, 0.0, l45.getTerminal1(), 0.0, 0.0, l45.getTerminal2());

        checkFlows(1.7, 0.9, l46.getTerminal1(), -1.7, -0.9, l46.getTerminal2());
        checkFlows(0.7, 0.3, l47.getTerminal1(), -0.7, -0.3, l47.getTerminal2());
        checkFlows(0.0, 0.0, l67.getTerminal1(), 0.0, 0.0, l67.getTerminal2());

        checkFlows(0.8, 0.4, l68.getTerminal1(), -0.8, -0.4, l68.getTerminal2());
        checkFlows(0.9, 0.5, l69.getTerminal1(), -0.9, -0.5, l69.getTerminal2());
        checkFlows(0.0, 0.0, l89.getTerminal1(), 0.0, 0.0, l89.getTerminal2());
    }

    @Test
    void threeBusesZeroImpedanceLineDcTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceLineDc", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        Line l23 = createLine(network, b2, b3, "l23", 0.0);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.99, l23.getTerminal1(), -1.99, l23.getTerminal2());
    }

    @Test
    void threeBusesZeroImpedanceTwoWindingsTransformerDcTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceTwoWindingsTransformerDc", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        TwoWindingsTransformer t23 = createTransformer(network, "s", b2, b3, "t23", 0, 1.1);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(1.99, t23.getTerminal1(), -1.99, t23.getTerminal2());
    }

    @Test
    void fourBusesZeroImpedanceThreeWindingsTransformerDcTest() {
        Network network = Network.create("FourBusesWithZeroImpedanceThreeWindingsTransformerDc", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "s", "b2");
        Bus b3 = createBus(network, "s", "b3");
        Bus b4 = createBus(network, "s", "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.9, 1);
        createLoad(b4, "l2", 1.0, 0.5);
        createLine(network, b1, b2, "l12", 0.01);
        ThreeWindingsTransformer t234 = createThreeWindingsTransformer(network, "s", b2, b3, b4, "t234", 0.0, 1.1, 0.0, 1.02, 0.0, 1.01);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        checkFlows(2.9, t234.getLeg1().getTerminal(), -1.9, t234.getLeg2().getTerminal(), -1.0, t234.getLeg3().getTerminal());
    }

    @Test
    void threeBusesZeroImpedanceDanglingLineDcTest() {
        Network network = Network.create("ThreeBusesWithZeroImpedanceDanglingLineDc", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.9, 1);
        createLine(network, b1, b2, "l12", 0.01);
        createLine(network, b2, b3, "l23", 0.01);
        DanglingLine dl3 = createDanglingLine(b3, "d3", 0.0, 1.5, 0.5);

        parametersExt.setSlackBusId("b1_vl_0")
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setNewtonRaphsonConvEpsPerEq(0.000001);
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertActivePowerEquals(1.5, dl3.getTerminal());
        assertTrue(Double.isNaN(dl3.getTerminal().getQ()));
    }

    @Test
    void testUpdateVoltageControlStatus() {
        Network network = Network.create("updateVoltageControlStatus", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b2, "l2", 2, 1);
        createGenerator(b4, "g4", 1, 1);
        createLine(network, b1, b2, "l12", 0.01);
        createLine(network, b2, b3, "l23", 0.0);
        createLine(network, b3, b4, "l34", 0.01);
        createLine(network, b1, b4, "l14", 0.01);
        network.getGenerator("g1").setRegulatingTerminal(network.getLine("l12").getTerminal2()); // remote control g1 -> b2
        network.getGenerator("g4").setRegulatingTerminal(network.getLine("l34").getTerminal1()); // remote control g4 -> b3
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Collections.emptySet(), Collections.emptySet(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            System.out.println(lfNetwork.getBusById("b1").getVoltageControls().get(0).getMergeStatus());
            System.out.println(lfNetwork.getBusById("b3").getVoltageControls().get(0).getMergeStatus());
            lfNetwork.getBranchById("l23").setDisabled(true);
            System.out.println(lfNetwork.getBusById("b1").getVoltageControls().get(0).getMergeStatus());
            System.out.println(lfNetwork.getBusById("b3").getVoltageControls().get(0).getMergeStatus());
            lfNetwork.getBranchById("l23").setDisabled(false);
            System.out.println(lfNetwork.getBusById("b1").getVoltageControls().get(0).getMergeStatus());
            System.out.println(lfNetwork.getBusById("b3").getVoltageControls().get(0).getMergeStatus());
        }
    }

    private static void checkFlows(double p1, double q1, Terminal t1, double p2, double q2, Terminal t2) {
        assertActivePowerEquals(p1, t1);
        assertReactivePowerEquals(q1, t1);
        assertActivePowerEquals(p2, t2);
        assertReactivePowerEquals(q2, t2);
    }

    private static void checkFlows(double p1, Terminal t1, double p2, Terminal t2) {
        assertActivePowerEquals(p1, t1);
        assertTrue(Double.isNaN(t1.getQ()));
        assertActivePowerEquals(p2, t2);
        assertTrue(Double.isNaN(t2.getQ()));
    }

    private static void checkFlows(double p1, double q1, Terminal t1, double p2, double q2, Terminal t2, double p3, double q3, Terminal t3) {
        assertActivePowerEquals(p1, t1);
        assertReactivePowerEquals(q1, t1);
        assertActivePowerEquals(p2, t2);
        assertReactivePowerEquals(q2, t2);
        assertActivePowerEquals(p3, t3);
        assertReactivePowerEquals(q3, t3);
    }

    private static void checkFlows(double p1, Terminal t1, double p2, Terminal t2, double p3, Terminal t3) {
        assertActivePowerEquals(p1, t1);
        assertTrue(Double.isNaN(t1.getQ()));
        assertActivePowerEquals(p2, t2);
        assertTrue(Double.isNaN(t2.getQ()));
        assertActivePowerEquals(p3, t3);
        assertTrue(Double.isNaN(t3.getQ()));
    }
}
