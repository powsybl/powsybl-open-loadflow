/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.TwoBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowTwoBusNetworkTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Line line1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = TwoBusNetworkFactory.create();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        line1 = network.getLine("l12");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
    }

    @Test
    void baseCaseTest() {
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
    void voltageInitModeTest() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        // restart loadflow from previous calculated state, it should convergence in zero iteration
        result = loadFlowRunner.run(network, parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES));
        assertTrue(result.isOk());
        assertEquals(1, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void withAnAdditionalBattery() {
        bus2.getVoltageLevel().newBattery()
                .setId("bt2")
                .setBus("b2")
                .setTargetP(-1)
                .setTargetQ(-0.1)
                .setMinP(-1)
                .setMaxP(0)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(0.784, bus2);
        assertAngleEquals(-22.455747, bus2);
        assertActivePowerEquals(2.996, line1.getTerminal1());
        assertReactivePowerEquals(2.750, line1.getTerminal1());
        assertActivePowerEquals(-2.996, line1.getTerminal2());
        assertReactivePowerEquals(-1.096, line1.getTerminal2());
        assertActivePowerEquals(1, network.getBattery("bt2").getTerminal());
    }

    @Test
    void withAnAdditionalBattery2() {
        bus2.getVoltageLevel().newBattery()
                .setId("bt2")
                .setBus("b2")
                .setTargetP(-1)
                .setTargetQ(-0.1)
                .setMinP(-2)
                .setMaxP(2)
                .add();
        network.getBattery("bt2").newExtension(ActivePowerControlAdder.class).withDroop(1).withParticipate(true).add();
        network.getGenerator("g1").setMaxP(3);
        parameters.setDistributedSlack(true);
        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(0.829, bus2);
        assertAngleEquals(-15.902121, bus2);
        assertActivePowerEquals(2.2727, line1.getTerminal1());
        assertReactivePowerEquals(2.0228, line1.getTerminal1());
        assertActivePowerEquals(-2.2727, line1.getTerminal2());
        assertReactivePowerEquals(-1.097, line1.getTerminal2());
        assertActivePowerEquals(0.275, network.getBattery("bt2").getTerminal());
    }

    @Test
    void withAnAdditionalBattery3() {
        bus2.getVoltageLevel().newBattery()
                .setId("bt2")
                .setBus("b2")
                .setTargetP(-1)
                .setTargetQ(-0.1)
                .setMinP(-2)
                .setMaxP(2)
                .add();
        network.getGenerator("g1").setMaxP(3);
        parameters.setDistributedSlack(true);
        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(1, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(0.812, bus2);
        assertAngleEquals(-18.680062, bus2);
        assertActivePowerEquals(2.600, line1.getTerminal1());
        assertReactivePowerEquals(2.309, line1.getTerminal1());
        assertActivePowerEquals(-2.600, line1.getTerminal2());
        assertReactivePowerEquals(-1.0999, line1.getTerminal2());
        assertActivePowerEquals(0.600, network.getBattery("bt2").getTerminal());
    }

    @RepeatedTest(100)
    void zeroImpedanceToShuntCompensator() {
        var network = TwoBusNetworkFactory.createZeroImpedanceToShuntCompensator();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(0.9038788263615884, network.getLine("l23").getTerminal1());
        assertActivePowerEquals(0.0045161454581349, network.getLine("l23").getTerminal1());
    }
}
