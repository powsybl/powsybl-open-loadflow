/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
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

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LoadModelTest {

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
        assertActivePowerEquals(146.231, zipLoad.getTerminal());
        assertReactivePowerEquals(87.738, zipLoad.getTerminal());
        assertActivePowerEquals(600, load.getTerminal());
        assertReactivePowerEquals(200, load.getTerminal());
    }

    @Test
    void microGridNlTest() {
        ReadOnlyDataSource dataSource = CgmesConformity1Catalog.microGridBaseCaseNL()
                .dataSource();
        Network network = Network.read(dataSource);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Load l = network.getLoad("b1e03a8f-6a11-4454-af58-4a4a680e857f");
        assertActivePowerEquals(486.712, l.getTerminal());
        assertReactivePowerEquals(230.337, l.getTerminal());
    }
}
