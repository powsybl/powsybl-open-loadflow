/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.ac.solver.StateVectorScalingMode;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NewtonKrylovTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcSolverType(AcSolverType.NEWTON_KRYLOV)
                .setMaxNewtonKrylovIterations(20);
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory())); // sparse matrix solver only
    }

    @Test
    void newtonKrylovTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Bus genBus = network.getBusBreakerView().getBus("NGEN");
        Bus bus1 = network.getBusBreakerView().getBus("NHV1");
        Bus bus2 = network.getBusBreakerView().getBus("NHV2");
        Bus loadBus = network.getBusBreakerView().getBus("NLOAD");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(11, result.getComponentResults().get(0).getIterationCount());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(402.143, bus1);
        assertAngleEquals(-2.325966, bus1);
        assertVoltageEquals(389.953, bus2);
        assertAngleEquals(-5.832323, bus2);
        assertVoltageEquals(147.578, loadBus);
        assertAngleEquals(-11.94045, loadBus);
    }

    @Test
    void illConditionnedTest() {
        Network network = Network.read(new ResourceDataSource("two_area_case", new ResourceSet("/illinois/literature-based", "two_area_case.RAW")));
        parametersExt.setAcSolverType(AcSolverType.NEWTON_RAPHSON);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        parametersExt.setStateVectorScalingMode(StateVectorScalingMode.LINE_SEARCH);
        result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());

        parametersExt.setAcSolverType(AcSolverType.NEWTON_KRYLOV);

        result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        parametersExt.setNewtonKrylovLineSearch(true);
        result = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(15, result.getComponentResults().get(0).getIterationCount());
    }
}
