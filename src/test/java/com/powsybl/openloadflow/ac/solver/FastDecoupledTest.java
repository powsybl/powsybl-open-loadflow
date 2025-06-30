/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.stream.Collectors;

class FastDecoupledTest {
    private LoadFlowParameters parametersFastDecoupled;

    private LoadFlowParameters parametersNewtonRaphson;

    private LoadFlow.Runner loadFlowRunner;

    private static final Double DEFAULT_ERROR_TOLERANCE_VOLTAGES = Math.pow(10, -3);

    private static final Double DEFAULT_ERROR_TOLERANCE_ANGLES = Math.pow(10, -2);

    @BeforeEach
    void setUp() {
        parametersFastDecoupled = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parametersFastDecoupled)
                .setAcSolverType(FastDecoupledFactory.NAME)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE)
                .setMaxNewtonRaphsonIterations(30);
        parametersNewtonRaphson = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parametersNewtonRaphson)
                .setAcSolverType(NewtonRaphsonFactory.NAME)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE)
                .setMaxNewtonRaphsonIterations(30);
        OpenLoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);
    }

    @Test
    void testIEEE9() {
        Network network = IeeeCdfNetworkFactory.create9();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE14() {
        Network network = IeeeCdfNetworkFactory.create14();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE30() {
        Network network = IeeeCdfNetworkFactory.create30();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE57() {
        Network network = IeeeCdfNetworkFactory.create57();
        LoadFlowResult resultFastDecoupled = loadFlowRunner.run(network, parametersFastDecoupled);
        // compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE118() {
        Network network = IeeeCdfNetworkFactory.create118();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE300() {
        Network network = IeeeCdfNetworkFactory.create300();
        // compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE9ZeroImpedance() {
        Network network = IeeeCdfNetworkFactory.create9zeroimpedance();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    private void compareLoadFlowResultsBetweenSolvers(Network network, LoadFlowParameters parametersFastDecoupled, LoadFlowParameters parametersNewtonRaphson) {
        LoadFlowResult resultFastDecoupled = loadFlowRunner.run(network, parametersFastDecoupled);
        List<Double> voltagesFastDecoupled = getVoltages(network);
        List<Double> anglesFastDecoupled = getAngles(network);

        LoadFlowResult resultNewtonRaphson = loadFlowRunner.run(network, parametersNewtonRaphson);
        List<Double> voltagesNewtonRaphson = getVoltages(network);
        List<Double> anglesNewtonRaphson = getAngles(network);

        assertEquals(resultFastDecoupled.getComponentResults().get(0).getStatus(), resultNewtonRaphson.getComponentResults().get(0).getStatus());
        compareResults(voltagesFastDecoupled, voltagesNewtonRaphson, DEFAULT_ERROR_TOLERANCE_VOLTAGES);
        compareResults(anglesFastDecoupled, anglesNewtonRaphson, DEFAULT_ERROR_TOLERANCE_ANGLES);
    }

    private List<Double> getVoltages(Network network) {
        return network.getBusBreakerView()
                .getBusStream()
                .map(bus -> bus.getV() / bus.getVoltageLevel().getNominalV())
                .collect(Collectors.toList());
    }

    private List<Double> getAngles(Network network) {
        return network.getBusBreakerView()
                .getBusStream()
                .map(Bus::getAngle)
                .collect(Collectors.toList());
    }

    private void compareResults(List<Double> expected, List<Double> actual, Double errorTolerance) {
        assertEquals(expected.size(), actual.size(), "Different lists sizes");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), errorTolerance, "Error for bus " + i);
        }
    }
}

