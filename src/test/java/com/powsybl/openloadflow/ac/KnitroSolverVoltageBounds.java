/**
 * Copyright (c) 2024, Artelys (https://www.artelys.com/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
//import com.powsybl.openloadflow.ac.solver.AcSolverResult;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
//import com.powsybl.openloadflow.ac.solver.KnitroSolver;
import com.powsybl.openloadflow.ac.solver.KnitroSolverParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolverVoltageBounds {

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
    List<Bus> setUp4Bus() {
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
    void effectVoltageLoAndUpBounds() {
        /*
         * Checks
         * the effect of changing Knitro's voltage bounds
         * First case => feasible solution, Knitro converges
         * Second case => problem infeasible
         */

        List<Bus> busList = setUp4Bus();

        // ============= Model with default voltage bounds =============
        LoadFlowResult knitroResultDefault = loadFlowRunner.run(network, parameters);
        // Default voltage bounds
        double defaultMinVoltage = parametersExt.getMinRealisticVoltageKnitroSolver();
        double defaultMaxVoltage = parametersExt.getMaxRealisticVoltageKnitroSolver();
        Assertions.assertEquals(defaultMinVoltage, KnitroSolverParameters.DEFAULT_MIN_REALISTIC_VOLTAGE, 0.0001);
        Assertions.assertEquals(defaultMaxVoltage, KnitroSolverParameters.DEFAULT_MAX_REALISTIC_VOLTAGE, 0.0001);
        // Results
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

        // ============= Model with more restrictive voltage bounds =============
        parametersExt.setMinRealisticVoltageKnitroSolver(0.99);
        parametersExt.setMaxRealisticVoltageKnitroSolver(1.1);
        LoadFlowResult knitroResultMoreRestrictive = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.FAILED, knitroResultMoreRestrictive.getComponentResults().get(0).getStatus());
    }
}
