/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class GeneratorConnectedToSameBusNotControllingSameBusTest {

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    @Test
    void test() {
        var network = EurostagTutorialExample1Factory.create();
        network.getVoltageLevel("VLGEN").newGenerator()
                .setId("GEN2")
                .setConnectableBus("NGEN")
                .setBus("NGEN")
                .setMinP(0)
                .setMaxP(100)
                .setTargetP(1)
                .setVoltageRegulatorOn(true)
                .setTargetV(148)
                .setRegulatingTerminal(network.getLoad("LOAD").getTerminal())
                .add();
        loadFlowRunner.run(network);
        assertVoltageEquals(24.5, network.getBusBreakerView().getBus("NGEN"));
        assertVoltageEquals(148, network.getBusBreakerView().getBus("NLOAD"));
    }
}
