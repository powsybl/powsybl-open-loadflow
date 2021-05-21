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
import com.powsybl.openloadflow.network.DanglingLineFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
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

    @BeforeEach
    void setUp() {
        network = DanglingLineFactory.create();
        bus1 = network.getBusBreakerView().getBus("b1");
        bus2 = network.getBusBreakerView().getBus("b2");
        dl1 = network.getDanglingLine("dl1");
        g1 = network.getGenerator("g1");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
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
