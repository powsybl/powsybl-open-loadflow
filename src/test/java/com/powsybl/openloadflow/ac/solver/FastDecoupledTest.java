/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.RemoteReactivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class FastDecoupledTest {
    private LoadFlowParameters parametersFastDecoupled;

    private LoadFlowParameters parametersNewtonRaphson;

    private LoadFlow.Runner loadFlowRunner;

    private static final Double DEFAULT_ERROR_TOLERANCE_VOLTAGES = Math.pow(10, -3);

    private static final Double DEFAULT_ERROR_TOLERANCE_ANGLES = Math.pow(10, -2);

    @BeforeEach
    void setUp() {
        parametersFastDecoupled = new LoadFlowParameters().setHvdcAcEmulation(false);
        OpenLoadFlowParameters.create(parametersFastDecoupled)
                .setAcSolverType(FastDecoupledFactory.NAME)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE)
                .setMaxNewtonRaphsonIterations(30);
        parametersNewtonRaphson = new LoadFlowParameters().setHvdcAcEmulation(false);
        OpenLoadFlowParameters.create(parametersNewtonRaphson)
                .setAcSolverType(NewtonRaphsonFactory.NAME)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE)
                .setMaxNewtonRaphsonIterations(30);
        OpenLoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);
    }

    @Test
    void testSolverName() {
        FastDecoupledFactory fastDecoupledFactory = new FastDecoupledFactory();
        assertEquals("FAST_DECOUPLED", fastDecoupledFactory.getName());
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
        // For this one we need better precision parameters to reach the criteria
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setNewtonRaphsonConvEpsPerEq(1e-5);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setNewtonRaphsonConvEpsPerEq(1e-5);
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE9ZeroImpedance() {
        Network network = IeeeCdfNetworkFactory.create9zeroimpedance();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE9WithOpenBranches() {
        Network network = IeeeCdfNetworkFactory.create9();
        network.getLine("L7-5-0").getTerminal1().disconnect();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
        network.getLine("L7-5-0").getTerminal1().connect();
        network.getLine("L7-5-0").getTerminal2().disconnect();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testIEEE9PerEquationStoppingCriteria() {
        Network network = IeeeCdfNetworkFactory.create9();
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testWithSharedVoltageControl() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        parametersFastDecoupled.setUseReactiveLimits(false).setDistributedSlack(false);
        parametersNewtonRaphson.setUseReactiveLimits(false).setDistributedSlack(false);
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltageRemoteControl(true);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltageRemoteControl(true);

        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testWithRemoteReactivePowerControl() {
        // create a basic 4-buses network
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g4 = network.getGenerator("g4");
        Line l34 = network.getLine("l34");

        double targetQ = 1.0;

        // disable voltage control on g4
        g4.setTargetQ(0).setVoltageRegulatorOn(false);
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setGeneratorReactivePowerRemoteControl(true)
                .setNewtonRaphsonConvEpsPerEq(1e-5);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setGeneratorReactivePowerRemoteControl(true)
                .setNewtonRaphsonConvEpsPerEq(1e-5);

        // first test: generator g4 regulates reactive power on line 4->3 (on side of g4)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.TWO))
                .withEnabled(true).add();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);

        // second test: generator g4 regulates reactive power on line 4->3 (on opposite side)
        g4.newExtension(RemoteReactivePowerControlAdder.class)
                .withTargetQ(targetQ)
                .withRegulatingTerminal(l34.getTerminal(TwoSides.ONE))
                .withEnabled(true).add();
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testWithShuntVoltageControl() {
        Network network = ShuntNetworkFactory.create();
        ShuntCompensator shunt = network.getShuntCompensator("SHUNT");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        parametersFastDecoupled.setUseReactiveLimits(true)
                .setDistributedSlack(true);
        parametersNewtonRaphson.setUseReactiveLimits(true)
                .setDistributedSlack(true);
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        parametersFastDecoupled.setShuntCompensatorVoltageControlOn(true);
        parametersNewtonRaphson.setShuntCompensatorVoltageControlOn(true);

        shunt.setSectionCount(0);
        shunt.setVoltageRegulatorOn(true);
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testWithContinuousTransformerVoltageControl() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("T2wT");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        parametersFastDecoupled.setDistributedSlack(true);
        parametersNewtonRaphson.setDistributedSlack(true);
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        parametersFastDecoupled.setTransformerVoltageControlOn(true);
        parametersNewtonRaphson.setTransformerVoltageControlOn(true);

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);

        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testWithContinuousTransformerActivePowerControl() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        Network network = PhaseControlFactory.createNetworkWithT2wt();

        parametersFastDecoupled.setDistributedSlack(false).setUseReactiveLimits(false);
        parametersNewtonRaphson.setDistributedSlack(false).setUseReactiveLimits(false);
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        parametersFastDecoupled.setPhaseShifterRegulationOn(true);
        parametersNewtonRaphson.setPhaseShifterRegulationOn(true);

        // Regulation on side 1
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer("PS1");
        t2wt.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationValue(83);
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);

        // Regulation on side 2
        t2wt.getPhaseTapChanger().setRegulationTerminal(t2wt.getTerminal2());
        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testWithLoadModel() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getLoad("LOAD");
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        vlload.newLoad()
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

        parametersFastDecoupled.setDistributedSlack(false).setUseReactiveLimits(false);
        parametersNewtonRaphson.setDistributedSlack(false).setUseReactiveLimits(false);
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setUseLoadModel(true);
        parametersNewtonRaphson.getExtension(OpenLoadFlowParameters.class).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setUseLoadModel(true);

        compareLoadFlowResultsBetweenSolvers(network, parametersFastDecoupled, parametersNewtonRaphson);
    }

    @Test
    void testReporter() throws IOException {
        Network network = IeeeCdfNetworkFactory.create9();
        ReportNode reporter = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("test")
                .build();
        parametersFastDecoupled.getExtension(OpenLoadFlowParameters.class).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));
        loadFlowRunner.run(network, network.getVariantManager().getWorkingVariantId(),
                LocalComputationManager.getDefault(), parametersFastDecoupled, reporter);

        // Test the report
        String expected = """
                + test
                   + Load flow on network 'ieee9cdf'
                      + Network CC0 SC0
                         + Network info
                            Network has 9 buses and 9 branches
                            Network balance: active generation=319.64102 MW, active load=315 MW, reactive generation=0 MVar, reactive load=115 MVar
                            Angle reference bus: VL1_0
                            Slack bus: VL1_0
                         Slack bus active power (-4.64102 MW) distributed in 1 distribution iteration(s)
                         DC load flow completed (solverSuccess=true, outerloopStatus=STABLE)
                         + Fast Decoupled on Network CC0 SC0
                            No outer loops have been launched
                            + Initial mismatch
                               Fast-Decoupled norm |f(x)|=0.894758
                               + Largest P mismatch: 6.699622 MW
                                  Bus Id: VL2_0 (nominalVoltage=100kV)
                                  Bus V: 1.025 pu, 0.164329 rad
                                  Bus injection: 169.699622 MW, 4.987774 MVar
                               + Largest Q mismatch: 53.066932 MVar
                                  Bus Id: VL5_0 (nominalVoltage=100kV)
                                  Bus V: 1.032939 pu, -0.074048 rad
                                  Bus injection: -127.134274 MW, 3.066932 MVar
                               + Largest V mismatch: 0 p.u.
                                  Bus Id: VL1_0 (nominalVoltage=100kV)
                                  Bus V: 1.04 pu, -0 rad
                                  Bus injection: 75.499923 MW, 8.787543 MVar
                            + Iteration 1 mismatch
                               Step size: 1 (line search)
                               Step size: 1 (line search)
                               Fast-Decoupled norm |f(x)|=0.080661
                               + Largest P mismatch: 4.041306 MW
                                  Bus Id: VL3_1 (nominalVoltage=100kV)
                                  Bus V: 1.032593 pu, 0.037275 rad
                                  Bus injection: 4.041306 MW, 0.407507 MVar
                               + Largest Q mismatch: 0.829685 MVar
                                  Bus Id: VL5_0 (nominalVoltage=100kV)
                                  Bus V: 0.995859 pu, -0.070577 rad
                                  Bus injection: -127.914637 MW, -49.170315 MVar
                               + Largest V mismatch: 0 p.u.
                                  Bus Id: VL1_0 (nominalVoltage=100kV)
                                  Bus V: 1.04 pu, -0 rad
                                  Bus injection: 70.853089 MW, 26.910067 MVar
                            + Iteration 2 mismatch
                               Step size: 1 (line search)
                               Step size: 1 (line search)
                               Fast-Decoupled norm |f(x)|=0.003538
                               + Largest P mismatch: 0.210333 MW
                                  Bus Id: VL6_0 (nominalVoltage=100kV)
                                  Bus V: 1.012699 pu, -0.064317 rad
                                  Bus injection: -89.789667 MW, -30.00569 MVar
                               + Largest Q mismatch: -0.011693 MVar
                                  Bus Id: VL3_1 (nominalVoltage=100kV)
                                  Bus V: 1.032355 pu, 0.034146 rad
                                  Bus injection: -0.180282 MW, -0.011693 MVar
                               + Largest V mismatch: 0 p.u.
                                  Bus Id: VL1_0 (nominalVoltage=100kV)
                                  Bus V: 1.04 pu, -0 rad
                                  Bus injection: 71.700448 MW, 27.020721 MVar
                            + Iteration 3 mismatch
                               Step size: 1 (line search)
                               Step size: 1 (line search)
                               Fast-Decoupled norm |f(x)|=0.000166
                               + Largest P mismatch: -0.009396 MW
                                  Bus Id: VL6_0 (nominalVoltage=100kV)
                                  Bus V: 1.012653 pu, -0.064356 rad
                                  Bus injection: -90.009396 MW, -29.999946 MVar
                               + Largest Q mismatch: 0.000459 MVar
                                  Bus Id: VL3_1 (nominalVoltage=100kV)
                                  Bus V: 1.032353 pu, 0.034337 rad
                                  Bus injection: 0.009166 MW, 0.000459 MVar
                               + Largest V mismatch: 0 p.u.
                                  Bus Id: VL1_0 (nominalVoltage=100kV)
                                  Bus V: 1.04 pu, -0 rad
                                  Bus injection: 71.632631 MW, 27.046074 MVar
                         Outer loop DistributedSlack
                         Outer loop VoltageMonitoring
                         Outer loop ReactiveLimits
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """;

        assertReportEquals(new ByteArrayInputStream(expected.getBytes()), reporter);
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

