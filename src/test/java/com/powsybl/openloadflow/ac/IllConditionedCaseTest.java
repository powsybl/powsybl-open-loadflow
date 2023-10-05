/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.nr.StateVectorScalingMode;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.TwoBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange <damien.jeandemange at artelys.com>
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class IllConditionedCaseTest {

    private Network network;
    private Bus bus2;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        // TODO a better ill-conditioned case for state vector scaling tests
        network = TwoBusNetworkFactory.create();
        bus2 = network.getBusBreakerView().getBus("b2");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
    }

    @Test
    void withHighLoadTest() {
        network.getLoad("l1").setP0(3.902); // 3.9 does not need scaling

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        parametersExt.setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(8, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);

        parametersExt.setStateVectorScalingMode(StateVectorScalingMode.LINE_SEARCH);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);
    }

    @Test
    void convergenceControlParametersTest() {
        network.getLoad("l1").setP0(3.902); // 3.9 does not need scaling

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        parametersExt.setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(8, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);

        parametersExt.setMaxVoltageChangeStateVectorScalingMaxDv(0.05);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(10, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);

        parametersExt.setMaxVoltageChangeStateVectorScalingMaxDphi(Math.toRadians(6));
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(14, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);

        parametersExt.setStateVectorScalingMode(StateVectorScalingMode.LINE_SEARCH);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);

        parametersExt.setLineSearchStateVectorScalingMaxIteration(5);
        result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(16, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);

        parametersExt.setLineSearchStateVectorScalingStepFold(1.7);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(0.6364204826103471, bus2);
    }
}
