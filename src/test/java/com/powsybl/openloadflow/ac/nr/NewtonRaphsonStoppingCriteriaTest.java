/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Alexandre Le Jean <alexandre.le-jean at artelys.com>
 */
class NewtonRaphsonStoppingCriteriaTest {

    private Network network;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = BoundaryFactory.create();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
    }

    @Test
    void testDefaultUniformCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.UNIFORM_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testConvergedUniformCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setNewtonRaphsonConvEpsPerEq(0.1)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.UNIFORM_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(1, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testDefaultPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testVoltageConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxVoltageMismatch(1)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testVoltageMaxIterationPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxVoltageMismatch(0)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testActivePowerConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxActivePowerMismatch(0.038)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testActivePowerMaxIterationPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxActivePowerMismatch(0)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testReactivePowerConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxReactivePowerMismatch(1E-11)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testReactivePowerMaxIterationPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxReactivePowerMismatch(0)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testAngleConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxAngleMismatch(1E-22)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testAngleMaxIterationPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxAngleMismatch(0)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testRatioMaxIterationPerEquationCriteria() {
        network = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();
        OpenLoadFlowParameters.create(parameters)
                .setTransformerVoltageControlMode(OpenLoadFlowParameters.TransformerVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL)
                .setMaxRatioMismatch(0)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        parameters.setTransformerVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testSusceptanceMaxIterationPerEquationCriteria() {
        network = ShuntNetworkFactory.createWithTwoShuntCompensators();
        OpenLoadFlowParameters.create(parameters)
                .setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL)
                .setMaxSusceptanceMismatch(0)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isOk());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }
}
