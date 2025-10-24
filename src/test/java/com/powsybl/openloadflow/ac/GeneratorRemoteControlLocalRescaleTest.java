/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class GeneratorRemoteControlLocalRescaleTest {

    private Network network;
    private Bus b1;
    private Bus b2;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = Network.create("GeneratorRemoteControlLocalRescaleTest", "code");
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
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        Load l2 = vl2.newLoad()
                .setId("l2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setP0(99.9)
                .setQ0(80)
                .add();
        b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(l2.getTerminal())
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr1")
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(30)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setVoltageRemoteControl(false);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowAssert.assertVoltageEquals(20.67, b1); // check local targetV has been correctly rescaled 20.67=413.4/400*20
        LoadFlowAssert.assertVoltageEquals(395.927, b2);
    }

    @Test
    void testLocalTargetV() {
        network.getGenerator("g1").setTargetV(413.4);
        parameters.getExtension(OpenLoadFlowParameters.class).setVoltageRemoteControl(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        LoadFlowAssert.assertVoltageEquals(413.4, b2);
        LoadFlowAssert.assertVoltageEquals(21.55, b1);

        // Set the backup local target v and run without remote voltage control and check that the same result is obtained
        network.getGenerator("g1").setTargetV(413.4, 21.5535);
        parameters.getExtension(OpenLoadFlowParameters.class).setVoltageRemoteControl(false);

       result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        LoadFlowAssert.assertVoltageEquals(413.4, b2);
        LoadFlowAssert.assertVoltageEquals(21.55, b1);

        // Change a bit the local target V anc verify that it is honnored
        network.getGenerator("g1").setTargetV(413.4, 21.0);

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltageRemoteControl(false);

        result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        LoadFlowAssert.assertVoltageEquals(402.45, b2);
        LoadFlowAssert.assertVoltageEquals(21.0, b1); // The local target V is maintained
    }

    @Test
    void testInconsistentLocalTargetV() {
        Generator g2 = network.getVoltageLevel("vl1")
                .newGenerator()
                .setId("g2")
                .setBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(413.4)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(network.getLoad("l2").getTerminal())
                .add();
        network.getGenerator("g1").setTargetV(413.4, 21.0);
        g2.setTargetV(413.4, 21.0);

        parameters.getExtension(OpenLoadFlowParameters.class).setVoltageRemoteControl(false);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        LoadFlowAssert.assertVoltageEquals(402.45, b2);
        LoadFlowAssert.assertVoltageEquals(21.0, b1); // The local target V is maintained

        // Set inconsistent local targets
        network.getGenerator("g1").setTargetV(413.4, 20.9);
        g2.setTargetV(413.4, 21.1);

        result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        LoadFlowAssert.assertVoltageEquals(400.48, b2);
        LoadFlowAssert.assertVoltageEquals(20.09, b1); // The local target V is maintained

    }
}
