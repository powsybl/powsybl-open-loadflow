/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
class KnitroSolverStoppingCriteriaTest {

    private Network network;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        // Sparse matrix solver only
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        // ============= Setting LoadFlow parameters =============
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAcSolverType(AcSolverType.KNITRO);
        // No OLs
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.setDistributedSlack(false)
                .setUseReactiveLimits(false);
        parameters.getExtension(OpenLoadFlowParameters.class)
                .setSvcVoltageMonitoring(false);
    }

    // Builds 4 bus network with condenser and returns busList
    List<Bus>  setUp4Bus() {
        // ============= Building network =============
        network = FourBusNetworkFactory.createWithCondenser();
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b3 = network.getBusBreakerView().getBus("b3");
        List<Bus> busList = network.getBusView().getBusStream().toList();
        return busList;
    }


    @Test
    void testEffectOfConvEpsPerEq() {
        /*
         * Checks the effect of changing Knitro's parameter convEpsPerEq on precision and values, when running Knitro solver
         */

        List<Bus> busList = setUp4Bus();

        // ============= Model with default precision =============
        LoadFlowResult knitroResultDefault = loadFlowRunner.run(network, parameters);
        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResultDefault.getComponentResults().get(0).getStatus());
        assertTrue(knitroResultDefault.isFullyConverged());
        assertVoltageEquals(1.0, busList.get(0));
        assertAngleEquals(0, busList.get(0));
        assertVoltageEquals(0.983834, busList.get(1));
        assertAngleEquals(-9.490705, busList.get(1));
        assertVoltageEquals(0.983124, busList.get(2));
        assertAngleEquals(-13.178514, busList.get(2));
        assertVoltageEquals(1.0, busList.get(3));
        assertAngleEquals(-6.531907, busList.get(3));

        // ============= Model with smaller precision =============
        parametersExt.setKnitroSolverConvEpsPerEq(Math.pow(10,-2));
        LoadFlowResult knitroResultLessPrecise = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResultLessPrecise.getComponentResults().get(0).getStatus());
        assertTrue(knitroResultLessPrecise.isFullyConverged());
        assertVoltageEquals(1.0, busList.get(0));
        assertAngleEquals(0, busList.get(0));
        assertVoltageEquals(0.983834, busList.get(1));
        assertAngleEquals(-9.485945, busList.get(1));
        assertVoltageEquals(0.983124, busList.get(2));
        assertAngleEquals(-13.170002, busList.get(2));
        assertVoltageEquals(1.0, busList.get(3));
        assertAngleEquals(-6.530383, busList.get(3));

    }
}
