/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.iidm.network.ComponentConstants;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.na.test.CommonTestConfig;
import com.powsybl.na.test.ServiceParameterResolver;
import com.powsybl.na.test.networks.ConnectedComponentNetworkFactory;
import com.powsybl.na.test.networks.HvdcNetworkFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
class LfNetworkLoadFlowTest {

    private final CommonTestConfig commonTestConfig;

    LfNetworkLoadFlowTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    @Test
    void testMultipleConnectedComponentsACMainComponent() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        //Default is only compute load flow on the main component
        assertEquals(1, result.getComponentResults().size());
        assertEquals(ComponentConstants.MAIN_NUM, result.getComponentResults().get(0).getConnectedComponentNum());
    }

    @Test
    void testMultipleConnectedComponentsACAllComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setComponentMode(LoadFlowParameters.ComponentMode.ALL_CONNECTED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertEquals(2, result.getComponentResults().size());
    }

    @Test
    void testMultipleConnectedComponentsDCMainComponent() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        // Default is only compute load flow on the main component
        assertEquals(1, result.getComponentResults().size());
        assertEquals(ComponentConstants.MAIN_NUM, result.getComponentResults().get(0).getConnectedComponentNum());
    }

    @Test
    void testMultipleConnectedComponentsDCAllComponents() {
        Network network = ConnectedComponentNetworkFactory.createTwoUnconnectedCC();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setComponentMode(LoadFlowParameters.ComponentMode.ALL_CONNECTED)
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        parameters.setDc(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertEquals(2, result.getComponentResults().size());
    }

    @Test
    void testMultipleConnectedComponentsMainSynchronousMode() {
        // Network initially has two one connected component and two synchronous components
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();
        // Splitting the connected components in two connected components, it now has 3 synchronous components
        network.getLine("l12").disconnect();
        network.getLine("l13").disconnect();
        network.getGenerator("g2").setMaxP(3); // Increasing MaxP to allow slack bus distribution
        network.getGenerator("g6").setMaxP(3); // Increasing MaxP to allow slack bus distribution

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();

        parameters.setComponentMode(LoadFlowParameters.ComponentMode.ALL_CONNECTED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().size());

        parameters.setComponentMode(LoadFlowParameters.ComponentMode.MAIN_CONNECTED);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(2, result.getComponentResults().size());

        parameters.setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(1, result.getComponentResults().size());
    }
}
