/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NonImpedantBranchTest extends AbstractLoadFlowNetworkFactory {

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    @Test
    public void threeBusesTest() {
        Network network = Network.create("3-buses-with-non-impedant-branch", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b3, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.1);
        createLine(network, b2, b3, "l23", 0); // non impedant branch

        LoadFlowResult result = loadFlowRunner.run(network);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.858, b2);
        assertVoltageEquals(0.858, b3);
        assertAngleEquals(13.36967, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
    }

    @Test
    public void fourBusesTest() {
        Network network = Network.create("4 buses-with-non-impedant-branch", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        createGenerator(b1, "g1", 2, 1);
        createLoad(b4, "l1", 1.99, 1);
        createLine(network, b1, b2, "l12", 0.05);
        createLine(network, b2, b3, "l23", 0); // non impedant branch
        createLine(network, b3, b4, "l34", 0.05);

        LoadFlowResult result = loadFlowRunner.run(network);
        assertTrue(result.isOk());
        assertVoltageEquals(1, b1);
        assertVoltageEquals(0.921, b2);
        assertVoltageEquals(0.921, b3);
        assertVoltageEquals(0.855, b4);
        assertAngleEquals(6.2301, b1);
        assertAngleEquals(0, b2);
        assertAngleEquals(0, b3);
        assertAngleEquals(-7.248787, b4);
    }
}
