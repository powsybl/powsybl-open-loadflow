/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LoadModelTest {

    @Test
    @Disabled
    void test() {
        var network = EurostagTutorialExample1Factory.create();
        var loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        var parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setDefaultLoadAlpha(0.5)
                .setDefaultLoadBeta(0.5);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());

        var genBus = network.getBusBreakerView().getBus("NGEN");
        var bus1 = network.getBusBreakerView().getBus("NHV1");
        var bus2 = network.getBusBreakerView().getBus("NHV2");
        var loadBus = network.getBusBreakerView().getBus("NLOAD");
        var load = network.getLoad("LOAD");
        assertVoltageEquals(24.5, genBus);
        assertVoltageEquals(402.018, bus1);
        assertVoltageEquals(389.64, bus2);
        assertVoltageEquals(147.384, loadBus);
        assertActivePowerEquals(600, load.getTerminal()); // FIXME
        assertReactivePowerEquals(200, load.getTerminal()); // FIXME
    }
}
