/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class MultipleSlackBusesTest {

    private Network network;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setMaxSlackBusCount(2);
    }

    @Test
    void multiSlackTest() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        assertEquals(3, componentResult.getIterationCount());
        assertEquals(-1.432, componentResult.getSlackBusActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);

        parameters.setDistributedSlack(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        componentResult = result.getComponentResults().get(0);
        assertEquals(4, componentResult.getIterationCount());
        assertEquals(-0.011, componentResult.getSlackBusActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void nonImpedantBranchTest() {
        network.getLine("NHV1_NHV2_1")
                .setR(0)
                .setX(0);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        assertEquals(3, componentResult.getIterationCount());
        assertEquals(-5.509, componentResult.getSlackBusActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);

        parameters.setDistributedSlack(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        componentResult = result.getComponentResults().get(0);
        assertEquals(4, componentResult.getIterationCount());
        assertEquals(-0.011, componentResult.getSlackBusActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
    }
}
