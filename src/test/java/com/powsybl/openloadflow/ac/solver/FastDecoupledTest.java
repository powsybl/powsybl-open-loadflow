/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
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

    private OpenLoadFlowParameters parametersExtFastDecoupled;

    private LoadFlowParameters parametersNewtonRaphson;

    private OpenLoadFlowParameters parametersExtNewtonRaphson;

    private OpenLoadFlowProvider loadFlowProvider;

    private LoadFlow.Runner loadFlowRunner;

    private Double DEFAULT_ERROR_TOLERANCE_VOLTAGES = Math.pow(10, -3);

    private Double DEFAULT_ERROR_TOLERANCE_ANGLES = Math.pow(10, -2);


    @BeforeEach
    void setUp() {
        parametersFastDecoupled = new LoadFlowParameters();
        parametersExtFastDecoupled = OpenLoadFlowParameters.create(parametersFastDecoupled)
                .setAcSolverType(FastDecoupledFactory.NAME)
                .setMaxNewtonRaphsonIterations(30);
        parametersNewtonRaphson = new LoadFlowParameters();
        parametersExtNewtonRaphson = OpenLoadFlowParameters.create(parametersNewtonRaphson)
                .setAcSolverType(NewtonRaphsonFactory.NAME);
        loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
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
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE118() {
        Network network = IeeeCdfNetworkFactory.create118();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE300() {
        Network network = IeeeCdfNetworkFactory.create300();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
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
//        compareResults(voltagesFastDecoupled, voltagesNewtonRaphson, DEFAULT_ERROR_TOLERANCE_VOLTAGES);
//        compareResults(anglesFastDecoupled, anglesNewtonRaphson, DEFAULT_ERROR_TOLERANCE_ANGLES);
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

//    private Network network;
//    private Substation s;
//    private Bus bus1;
//    private Bus bus2;
//    private Bus bus3;
//    private ThreeWindingsTransformer twt;
//
//    private LoadFlow.Runner loadFlowRunner;
//
//    private LoadFlowParameters parameters;
//
//    @BeforeEach
//    void setUp() {
//        network = T3wtFactory.create();
//        s = network.getSubstation("s");
//        bus1 = network.getBusBreakerView().getBus("b1");
//        bus2 = network.getBusBreakerView().getBus("b2");
//        bus3 = network.getBusBreakerView().getBus("b3");
//        twt = network.getThreeWindingsTransformer("3wt");
//        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
//        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
//                .setDistributedSlack(false);
//        OpenLoadFlowParameters.create(parameters)
//                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED).setAcSolverType(FastDecoupledFactory.NAME);
//    }
////
//    @Test
//    void test() {
//
//        network = T3wtFactory.create();
//        s = network.getSubstation("s");
//        bus1 = network.getBusBreakerView().getBus("b1");
//        bus2 = network.getBusBreakerView().getBus("b2");
//        bus3 = network.getBusBreakerView().getBus("b3");
//        twt = network.getThreeWindingsTransformer("3wt");
//        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
//        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
//                .setDistributedSlack(false);
//        OpenLoadFlowParameters.create(parameters)
//                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED).setAcSolverType(FastDecoupledFactory.NAME);
//
//        LoadFlowResult result = loadFlowRunner.run(network, parameters);
//        assertTrue(result.isFullyConverged());
//
//        assertVoltageEquals(405, bus1);
//        LoadFlowAssert.assertAngleEquals(0, bus1);
//        assertVoltageEquals(235.132, bus2);
//        LoadFlowAssert.assertAngleEquals(-2.259241, bus2);
//        assertVoltageEquals(20.834, bus3);
//        LoadFlowAssert.assertAngleEquals(-2.721885, bus3);
//        assertActivePowerEquals(161.095, twt.getLeg1().getTerminal());
//        assertReactivePowerEquals(81.884, twt.getLeg1().getTerminal());
//        assertActivePowerEquals(-161, twt.getLeg2().getTerminal());
//        assertReactivePowerEquals(-74, twt.getLeg2().getTerminal());
//        assertActivePowerEquals(0, twt.getLeg3().getTerminal());
//        assertReactivePowerEquals(0, twt.getLeg3().getTerminal());
//    }

