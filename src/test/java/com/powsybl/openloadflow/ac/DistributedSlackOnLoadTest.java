/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertBetterResults;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class DistributedSlackOnLoadTest {

    private Network network;
    private Load l1;
    private Load l2;
    private Load l3;
    private Load l4;
    private Load l5;
    private Load l6;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        l1 = network.getLoad("l1");
        l2 = network.getLoad("l2");
        l3 = network.getLoad("l3");
        l4 = network.getLoad("l4");
        l5 = network.getLoad("l5");
        l6 = network.getLoad("l6");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setDistributedSlack(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(37.5, l1.getTerminal());
        assertActivePowerEquals(75, l2.getTerminal());
        assertActivePowerEquals(62.5, l3.getTerminal());
        assertActivePowerEquals(175, l4.getTerminal());
        assertActivePowerEquals(12.5, l5.getTerminal());
        assertActivePowerEquals(-50, l6.getTerminal()); // same as p0 because p0 < 0
    }

    @Test
    void testWithLoadDetail() {
        l2.newExtension(LoadDetailAdder.class)
                .withVariableActivePower(40)
                .withFixedActivePower(20)
                .add();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(38.182, l1.getTerminal());
        assertActivePowerEquals(70.909, l2.getTerminal());
        assertActivePowerEquals(63.636, l3.getTerminal());
        assertActivePowerEquals(178.182, l4.getTerminal());
        assertActivePowerEquals(12.727, l5.getTerminal());
        assertActivePowerEquals(-50, l6.getTerminal()); // same as p0 because p0 < 0
    }

    @Test
    void testPowerFactorConstant() {
        // given
        Network testNetwork = Importers.loadNetwork(Paths.get("src", "test", "resources", "2.xiidm"));
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);

        // when
        parametersExt.setPowerFactorConstant(false);
        LoadFlowResult loadFlowResult1 = loadFlowRunner.run(testNetwork, parameters);

        parametersExt.setPowerFactorConstant(true);
        LoadFlowResult loadFlowResult2 = loadFlowRunner.run(testNetwork, parameters);

        // then
        assertBetterResults(loadFlowResult1, loadFlowResult2);
    }
}
