/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.knitroextension;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class LoadModelTest {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setUseLoadModel(true)
                .setAcSolverType(KnitroSolverFactory.NAME);
    }

    @Test
    void zipLoadModelTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Load zipLoad = vlload.newLoad()
                .setId("ZIPLOAD")
                .setBus("NLOAD")
                .setP0(50)
                .setQ0(30)
                .newZipModel()
                    .setC0p(0.5)
                    .setC0q(0.55)
                    .setC1p(0.3)
                    .setC1q(0.35)
                    .setC2p(0.2)
                    .setC2q(0.1)
                    .add()
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertActivePowerEquals(48.786, zipLoad.getTerminal());
        assertReactivePowerEquals(29.425, zipLoad.getTerminal());
        assertActivePowerEquals(600, load.getTerminal());
        assertReactivePowerEquals(200, load.getTerminal());
    }

    @Test
    void expLoadModelTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Load expLoad = vlload.newLoad()
                .setId("EXPLOAD")
                .setBus("NLOAD")
                .setP0(50)
                .setQ0(30)
                .newExponentialModel()
                    .setNp(0.8)
                    .setNq(0.9)
                .add()
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertVoltageEquals(141.692, expLoad.getTerminal().getBusView().getBus());
        assertEquals(150, expLoad.getTerminal().getVoltageLevel().getNominalV(), 0);
        assertActivePowerEquals(47.772, expLoad.getTerminal()); // < 50MW because v < vnom
        assertReactivePowerEquals(28.5, expLoad.getTerminal()); // < 30MW because v < vnom
        assertActivePowerEquals(600, load.getTerminal());
        assertReactivePowerEquals(200, load.getTerminal());
    }

    @Test
    void zipAndExpLoadModelTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Load zipLoad = vlload.newLoad()
                .setId("ZIPLOAD")
                .setBus("NLOAD")
                .setP0(50)
                .setQ0(30)
                .newZipModel()
                    .setC0p(0.5)
                    .setC0q(0.55)
                    .setC1p(0.3)
                    .setC1q(0.35)
                    .setC2p(0.2)
                    .setC2q(0.1)
                    .add()
                .add();
        Load expLoad = vlload.newLoad()
                .setId("EXPLOAD")
                .setBus("NLOAD")
                .setP0(50)
                .setQ0(30)
                .newExponentialModel()
                    .setNp(0.8)
                    .setNq(0.9)
                .add()
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertActivePowerEquals(47.369, zipLoad.getTerminal());
        assertReactivePowerEquals(28.749, zipLoad.getTerminal());
        assertActivePowerEquals(46.902, expLoad.getTerminal());
        assertReactivePowerEquals(27.917, expLoad.getTerminal());
        assertActivePowerEquals(600, load.getTerminal());
        assertReactivePowerEquals(200, load.getTerminal());
    }

    @Test
    void dummyZipLoadModelTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        load.remove();
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Load zipLoad = vlload.newLoad()
                .setId("ZIPLOAD")
                .setBus("NLOAD")
                .setP0(600.0)
                .setQ0(200.0)
                .newZipModel()
                    .setC0p(1)
                    .setC0q(1)
                    .add()
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertActivePowerEquals(600, zipLoad.getTerminal());
        assertReactivePowerEquals(200, zipLoad.getTerminal());
    }

    @Test
    void zipLoadModelAndDistributedSlackOnLoadTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        Load zipLoad = vlload.newLoad()
                .setId("ZIPLOAD")
                .setBus("NLOAD")
                .setP0(50)
                .setQ0(30)
                .newZipModel()
                .setC0p(0.5)
                .setC0q(0.55)
                .setC1p(0.3)
                .setC1q(0.35)
                .setC2p(0.2)
                .setC2q(0.1)
                .add()
                .add();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertActivePowerEquals(45.326, zipLoad.getTerminal());
        assertReactivePowerEquals(29.519, zipLoad.getTerminal());
        assertActivePowerEquals(555.194, load.getTerminal());
        assertReactivePowerEquals(200, load.getTerminal());
    }
}
