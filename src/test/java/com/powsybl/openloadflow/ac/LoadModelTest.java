/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.iidm.network.*;
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

import java.util.Map;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class LoadModelTest {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
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
        assertActivePowerEquals(48.765, zipLoad.getTerminal());
        assertReactivePowerEquals(30.759, zipLoad.getTerminal());
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
        assertActivePowerEquals(47.768, expLoad.getTerminal());
        assertReactivePowerEquals(28.661, expLoad.getTerminal());
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
        assertActivePowerEquals(47.346, zipLoad.getTerminal());
        assertReactivePowerEquals(29.907, zipLoad.getTerminal());
        assertActivePowerEquals(46.873, expLoad.getTerminal());
        assertReactivePowerEquals(28.124, expLoad.getTerminal());
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
    void microGridNlTest() {
        ReadOnlyDataSource dataSource = CgmesConformity1Catalog.microGridBaseCaseNL()
                .dataSource();
        Network network = Network.read(dataSource);
        Map<String, Double> vMap = network.getBusView().getBusStream()
                .collect(Collectors.toMap(Identifiable::getId, Bus::getV));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Load l = network.getLoad("b1e03a8f-6a11-4454-af58-4a4a680e857f");
        assertActivePowerEquals(489.123, l.getTerminal());
        assertReactivePowerEquals(208.478, l.getTerminal());
        for (Bus bus : network.getBusView().getBuses()) {
            double dv = bus.getV() - vMap.get(bus.getId());
//            assertEquals(0, dv, 1e-1);
        }
    }
}
