/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
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

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class DistributedSlackOnLoadTest {

    private Network network;
    private Load l1;
    private Load l2;
    private Load l3;
    private Load l4;
    private Load l5;
    private Load l6;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    public void setUp() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        l1 = network.getLoad("l1");
        l2 = network.getLoad("l2");
        l3 = network.getLoad("l3");
        l4 = network.getLoad("l4");
        l5 = network.getLoad("l5");
        l6 = network.getLoad("l6");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector())
                .setDistributedSlack(true)
                .setBalanceType(OpenLoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        for (Load load : network.getLoads()) {
            System.out.println(load.getTerminal().getP());
        }
        assertActivePowerEquals(37.5, l1.getTerminal());
        assertActivePowerEquals(75, l2.getTerminal());
        assertActivePowerEquals(62.5, l3.getTerminal());
        assertActivePowerEquals(175, l4.getTerminal());
        assertActivePowerEquals(12.5, l5.getTerminal());
        assertActivePowerEquals(-50, l6.getTerminal()); // same as p0 because p0 < 0
    }
}
