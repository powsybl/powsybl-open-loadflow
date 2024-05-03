/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class KnitroSolverTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

//    @BeforeEach
//    void setUp() {
//        parameters = new LoadFlowParameters();
//        OpenLoadFlowParameters.create(parameters).setAcSolverType(AcSolverType.KNITRO);
//        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory())); // sparse matrix solver only
//    }
//
//    @Test
//    void knitroSolverTest() {
//        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
//        Bus genBus = network.getBusBreakerView().getBus("NGEN");
//        Bus bus1 = network.getBusBreakerView().getBus("NHV1");
//        Bus bus2 = network.getBusBreakerView().getBus("NHV2");
//        Bus loadBus = network.getBusBreakerView().getBus("NLOAD");
//
//        LoadFlowResult result = loadFlowRunner.run(network, parameters);
//
//        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
//
//        assertVoltageEquals(24.5, genBus);
//        assertAngleEquals(0, genBus);
//        assertVoltageEquals(402.143, bus1);
//        assertAngleEquals(-2.325966, bus1);
//        assertVoltageEquals(389.953, bus2);
//        assertAngleEquals(-5.832323, bus2);
//        assertVoltageEquals(147.578, loadBus);
//        assertAngleEquals(-11.94045, loadBus);
//    }

}
