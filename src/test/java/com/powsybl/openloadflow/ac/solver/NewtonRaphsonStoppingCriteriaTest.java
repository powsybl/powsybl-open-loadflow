/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
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
 * @author Alexandre Le Jean {@literal <alexandre.le-jean at artelys.com>}
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
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testConvergedUniformCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setNewtonRaphsonConvEpsPerEq(0.1)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.UNIFORM_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(1, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testDefaultPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testVoltageConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxVoltageMismatch(1)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testActivePowerConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxActivePowerMismatch(0.038)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testActivePowerMaxIterationPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxActivePowerMismatch(1E-15)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testReactivePowerConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxReactivePowerMismatch(1E-11)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testReactivePowerMaxIterationPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxReactivePowerMismatch(1E-15)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testAngleConvergedPerEquationCriteria() {
        OpenLoadFlowParameters.create(parameters)
                .setMaxAngleMismatch(1E-22)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());

        OpenLoadFlowParameters.create(parameters)
                .setMaxAngleMismatch(1E-30)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testRatioMaxIterationPerEquationCriteria() {
        network = VoltageControlNetworkFactory.createWithTransformerSharedRemoteControl();
        OpenLoadFlowParameters.create(parameters)
                .setTransformerVoltageControlMode(OpenLoadFlowParameters.TransformerVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        parameters.setTransformerVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testSusceptanceMaxIterationPerEquationCriteria() {
        network = ShuntNetworkFactory.createWithTwoShuntCompensators();
        OpenLoadFlowParameters.create(parameters)
                .setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        parameters.setShuntCompensatorVoltageControlOn(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testReactivePowerConvergedPerEquationCriteria2() {
        network = BoundaryFactory.createWithoutLoads();
        parameters.setUseReactiveLimits(true);
        OpenLoadFlowParameters.create(parameters)
                .setMaxReactivePowerMismatch(1E-2)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        double b1Q = network.getBusBreakerView().getBus("b1")
                .getConnectedTerminalStream()
                .map(Terminal::getQ)
                .filter(d -> !Double.isNaN(d))
                .reduce(0.0, Double::sum);
        double b2Q = network.getBusBreakerView().getBus("b2")
                .getConnectedTerminalStream()
                .map(Terminal::getQ)
                .filter(d -> !Double.isNaN(d))
                .reduce(0.0, Double::sum);

        assertEquals(0.0, b1Q, 1E-2);
        assertEquals(0.0, b2Q, 1E-2);
    }
}
