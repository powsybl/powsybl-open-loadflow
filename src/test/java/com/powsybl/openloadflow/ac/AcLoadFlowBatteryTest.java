/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltageRegulationAdder;
import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLoadFlowBatteryTest {

    private Network network;
    private Bus genBus;
    private Bus batBus;
    private Generator generator;
    private Battery battery1;
    private Battery battery2;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = BatteryNetworkFactory.create();
        genBus = network.getBusBreakerView().getBus("NGEN");
        batBus = network.getBusBreakerView().getBus("NBAT");
        generator = network.getGenerator("GEN");
        generator.setMinP(0).setMaxP(1000).setTargetV(401.);
        battery1 = network.getBattery("BAT");
        battery1.setMinP(-1000).setMaxP(1000).setTargetQ(0).setTargetP(0);
        battery2 = network.getBattery("BAT2");
        battery2.setTargetP(-1000).setMaxP(1000);

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(401, genBus);
        LoadFlowAssert.assertAngleEquals(5.916573, genBus);
        assertVoltageEquals(397.660, batBus);
        LoadFlowAssert.assertAngleEquals(0.0, batBus);
    }

    @Test
    void testWithVoltageControl() {
        generator.setVoltageRegulatorOn(false);
        battery2.newExtension(VoltageRegulationAdder.class)
                .withTargetV(401)
                .withVoltageRegulatorOn(true)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(417.328, genBus);
        LoadFlowAssert.assertAngleEquals(5.468356, genBus);
        assertVoltageEquals(401.0, batBus);
        LoadFlowAssert.assertAngleEquals(0.0, batBus);
    }
}
