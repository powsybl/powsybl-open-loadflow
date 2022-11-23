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
import com.powsybl.openloadflow.NetworkCache;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowWithCachingTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setNetworkCacheEnabled(true);
        NetworkCache.INSTANCE.clear();
    }

    @Test
    void testLoadP() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var load = network.getLoad("LOAD");
        var gen = network.getGenerator("GEN");

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(600, load.getTerminal());
        assertActivePowerEquals(-605.559, gen.getTerminal());

        load.setP0(620);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(620, load.getTerminal());
        assertActivePowerEquals(-625.895, gen.getTerminal());
    }

    @Test
    void testTargetV() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");
        var ngen = network.getBusBreakerView().getBus("NGEN");
        var nload = network.getBusBreakerView().getBus("NLOAD");

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(24.5, ngen);
        assertVoltageEquals(147.578, nload);

        gen.setTargetV(24.1);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(24.1, ngen);
        assertVoltageEquals(144.402, nload);

        result = loadFlowRunner.run(network, parameters);
        // FIXME NO_CALCULATION should be added to API
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, result.getComponentResults().get(0).getStatus());
        assertEquals(0, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    @Disabled("Cannot be enabled by default because not reliable enough (could depend on JVM impl and machine speed)")
    void testCacheEviction() throws InterruptedException {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);

        // check that cache entry could be correctly garbage collected
        network = null;
        int retry = 0;
        do {
            System.gc();
            retry++;
            TimeUnit.MILLISECONDS.sleep(100);
        } while (NetworkCache.INSTANCE.getEntryCount() > 0 && retry < 10);
        assertEquals(0, NetworkCache.INSTANCE.getEntryCount());
    }
}
