/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.iidm.network.extensions.SlackTerminalAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcLoadFlowEurostagTutorialExample1Test {

    private Network network;
    private Bus genBus;
    private Bus bus1;
    private Bus bus2;
    private Bus loadBus;
    private Line line1;
    private Line line2;
    private Generator gen;
    private VoltageLevel vlgen;
    private VoltageLevel vlload;
    private VoltageLevel vlhv1;
    private VoltageLevel vlhv2;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        genBus = network.getBusBreakerView().getBus("NGEN");
        bus1 = network.getBusBreakerView().getBus("NHV1");
        bus2 = network.getBusBreakerView().getBus("NHV2");
        loadBus = network.getBusBreakerView().getBus("NLOAD");
        line1 = network.getLine("NHV1_NHV2_1");
        line2 = network.getLine("NHV1_NHV2_2");
        gen = network.getGenerator("GEN");
        vlgen = network.getVoltageLevel("VLGEN");
        vlload = network.getVoltageLevel("VLLOAD");
        vlhv1 = network.getVoltageLevel("VLHV1");
        vlhv2 = network.getVoltageLevel("VLHV2");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
    }

    @Test
    void baseCaseTest() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(402.143, bus1);
        assertAngleEquals(-2.325965, bus1);
        assertVoltageEquals(389.953, bus2);
        assertAngleEquals(-5.832329, bus2);
        assertVoltageEquals(147.578, loadBus);
        assertAngleEquals(-11.940451, loadBus);
        assertActivePowerEquals(302.444, line1.getTerminal1());
        assertReactivePowerEquals(98.74, line1.getTerminal1());
        assertActivePowerEquals(-300.434, line1.getTerminal2());
        assertReactivePowerEquals(-137.188, line1.getTerminal2());
        assertActivePowerEquals(302.444, line2.getTerminal1());
        assertReactivePowerEquals(98.74, line2.getTerminal1());
        assertActivePowerEquals(-300.434, line2.getTerminal2());
        assertReactivePowerEquals(-137.188, line2.getTerminal2());

        // check pv bus reactive power update
        assertReactivePowerEquals(-225.279, gen.getTerminal());
    }

    @Test
    void dcLfVoltageInitTest() {
        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void line1Side1DeconnectionTest() {
        line1.getTerminal1().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(400.277, bus1);
        assertAngleEquals(-2.348788, bus1);
        assertVoltageEquals(374.537, bus2);
        assertAngleEquals(-9.719157, bus2);
        assertVoltageEquals(141.103, loadBus);
        assertAngleEquals(-16.372920, loadBus);
        assertActivePowerEquals(0, line1.getTerminal1());
        assertReactivePowerEquals(0, line1.getTerminal1());
        assertActivePowerEquals(0.016, line1.getTerminal2());
        assertReactivePowerEquals(-54.321, line1.getTerminal2());
        assertActivePowerEquals(609.544, line2.getTerminal1());
        assertReactivePowerEquals(263.412, line2.getTerminal1());
        assertActivePowerEquals(-600.965, line2.getTerminal2());
        assertReactivePowerEquals(-227.04, line2.getTerminal2());
    }

    @Test
    void line1Side2DeconnectionTest() {
        line1.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(400.120, bus1);
        assertAngleEquals(-2.352669, bus1);
        assertVoltageEquals(368.797, bus2);
        assertAngleEquals(-9.773427, bus2);
        assertVoltageEquals(138.678, loadBus);
        assertAngleEquals(-16.649943, loadBus);
        assertActivePowerEquals(0.01812, line1.getTerminal1());
        assertReactivePowerEquals(-61.995296, line1.getTerminal1());
        assertActivePowerEquals(0, line1.getTerminal2());
        assertReactivePowerEquals(0, line1.getTerminal2());
        assertActivePowerEquals(610.417, line2.getTerminal1());
        assertReactivePowerEquals(330.862, line2.getTerminal1());
        assertActivePowerEquals(-600.983, line2.getTerminal2());
        assertReactivePowerEquals(-284.230, line2.getTerminal2());
    }

    @Test
    void line1DeconnectionTest() {
        line1.getTerminal1().disconnect();
        line1.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(398.265, bus1);
        assertAngleEquals(-2.358007, bus1);
        assertVoltageEquals(366.585, bus2);
        assertAngleEquals(-9.857221, bus2);
        assertVoltageEquals(137.742, loadBus);
        assertAngleEquals(-16.822678, loadBus);
        assertUndefinedActivePower(line1.getTerminal1());
        assertUndefinedReactivePower(line1.getTerminal2());
        assertUndefinedActivePower(line1.getTerminal1());
        assertUndefinedReactivePower(line1.getTerminal2());
        assertActivePowerEquals(610.562, line2.getTerminal1());
        assertReactivePowerEquals(334.056, line2.getTerminal1());
        assertActivePowerEquals(-600.996, line2.getTerminal2());
        assertReactivePowerEquals(-285.379, line2.getTerminal2());
    }

    @Test
    void shuntCompensatorTest() {
        loadBus.getVoltageLevel().newShuntCompensator()
                .setId("SC")
                .setBus(loadBus.getId())
                .setConnectableBus(loadBus.getId())
                .setSectionCount(1)
                .newLinearModel()
                    .setBPerSection(3.25 * Math.pow(10, -3))
                    .setMaximumSectionCount(1)
                    .add()
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(152.327, loadBus);
        assertReactivePowerEquals(52.987, line1.getTerminal1());
        assertReactivePowerEquals(-95.063, line1.getTerminal2());
        assertReactivePowerEquals(52.987, line2.getTerminal1());
        assertReactivePowerEquals(-95.063, line2.getTerminal2());
    }

    @Test
    void invalidTargetQIssueTest() {
        // create a generator with a targetP to 0 and a minP > 0 so that the generator will be discarded from voltage
        // regulation
        // targetQ is not defined so value is NaN
        Generator g1 = loadBus.getVoltageLevel().newGenerator()
                .setId("g1")
                .setBus(loadBus.getId())
                .setConnectableBus(loadBus.getId())
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(10)
                .setMaxP(200)
                .setTargetP(0)
                .setTargetV(150)
                .setVoltageRegulatorOn(true)
                .add();
        // check that the issue that add an undefined targetQ (NaN) to bus generation sum is solved
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
    }

    @Test
    void slackBusLoadTest() {
        parameters.setReadSlackBus(true);
        parameters.setWriteSlackBus(true);
        Load load = network.getLoad("LOAD");
        vlload.newExtension(SlackTerminalAdder.class)
                .withTerminal(load.getTerminal())
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(vlload.getExtension(SlackTerminal.class));
        assertNull(vlgen.getExtension(SlackTerminal.class));
        assertNull(vlhv1.getExtension(SlackTerminal.class));
        assertNull(vlhv2.getExtension(SlackTerminal.class));
    }

    @Test
    void slackBusWriteTest() {
        parameters.setWriteSlackBus(true);
        assertNull(vlgen.getExtension(SlackTerminal.class));
        assertNull(vlload.getExtension(SlackTerminal.class));
        assertNull(vlhv1.getExtension(SlackTerminal.class));
        assertNull(vlhv2.getExtension(SlackTerminal.class));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(vlgen.getExtension(SlackTerminal.class));
        assertNull(vlload.getExtension(SlackTerminal.class));
        assertNull(vlhv1.getExtension(SlackTerminal.class));
        assertNull(vlhv2.getExtension(SlackTerminal.class));
    }

    @Test
    void lineWithDifferentNominalVoltageTest() {
        network.getVoltageLevel("VLHV2").setNominalV(420);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());

        assertVoltageEquals(24.5, genBus);
        assertVoltageEquals(402.143, bus1);
        assertVoltageEquals(389.953, bus2);
        assertVoltageEquals(147.578, loadBus);
    }

    @Test
    void noGeneratorPvTest() {
        // GEN is only generator with voltage control, disable it
        network.getGenerator("GEN").setVoltageRegulatorOn(false);

        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("unitTest", "")
                .build();
        LoadFlowResult result = loadFlowRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), parameters, reportNode);
        assertFalse(result.isFullyConverged());
        assertEquals(1, result.getComponentResults().size());
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, result.getComponentResults().get(0).getStatus());

        // also check there is a report added for this error
        assertEquals(1, reportNode.getChildren().size());
        ReportNode lfReportNode = reportNode.getChildren().get(0);
        assertEquals(1, lfReportNode.getChildren().size());
        ReportNode networkReportNode = lfReportNode.getChildren().get(0);
        assertEquals("lfNetwork", networkReportNode.getMessageKey());
        ReportNode networkInfoReportNode = networkReportNode.getChildren().get(0);
        assertEquals("networkInfo", networkInfoReportNode.getMessageKey());
        assertEquals(1, networkInfoReportNode.getChildren().size());
        assertEquals("Network must have at least one bus with generator voltage control enabled",
                networkInfoReportNode.getChildren().get(0).getMessage());
    }

    @Test
    void noGeneratorBecauseOfReactiveRangeTest() {
        Generator gen = network.getGenerator("GEN");
        gen.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(0)
                .add();

        parameters.setUseReactiveLimits(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isFullyConverged());
        assertEquals(1, result.getComponentResults().size());
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, result.getComponentResults().get(0).getStatus());

        // but if we do not take into account reactive limits in parameters, calculation should be ok
        parameters.setUseReactiveLimits(false);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(1, result.getComponentResults().size());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testSeveralShunts() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getVoltageLevel("VLLOAD").newShuntCompensator()
                .setId("SC")
                .setBus("NLOAD")
                .setConnectableBus("NLOAD")
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(3.25 * Math.pow(10, -3))
                .setMaximumSectionCount(1)
                .add()
                .add();
        network.getVoltageLevel("VLLOAD").newShuntCompensator()
                .setId("SC2")
                .setBus("NLOAD")
                .setConnectableBus("NLOAD")
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(-3.25 * Math.pow(10, -3))
                .setMaximumSectionCount(1)
                .add()
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL)
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(-70.783, network.getShuntCompensator("SC").getTerminal());
        assertReactivePowerEquals(70.783, network.getShuntCompensator("SC2").getTerminal());
    }

    @Test
    void testSeveralShunts2() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getVoltageLevel("VLLOAD").newShuntCompensator()
                .setId("SC")
                .setBus("NLOAD")
                .setConnectableBus("NLOAD")
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(0.0)
                .setMaximumSectionCount(1)
                .add()
                .add();
        network.getVoltageLevel("VLLOAD").newShuntCompensator()
                .setId("SC2")
                .setBus("NLOAD")
                .setConnectableBus("NLOAD")
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(0.0)
                .setMaximumSectionCount(1)
                .add()
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL)
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(0, network.getShuntCompensator("SC").getTerminal());
        assertReactivePowerEquals(0, network.getShuntCompensator("SC2").getTerminal());
    }

    @Test
    void testEmptyNetwork() {
        Network network = Network.create("empty", "");
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.getComponentResults().isEmpty());
    }

    @Test
    void testWithDisconnectedGenerator() {
        loadFlowRunner.run(network, parameters);
        gen.getTerminal().disconnect();
        loadBus.getVoltageLevel().newGenerator()
                .setId("g1")
                .setBus(loadBus.getId())
                .setConnectableBus(loadBus.getId())
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(1)
                .setMaxP(200)
                .setTargetP(1)
                .setTargetV(150)
                .setVoltageRegulatorOn(true)
                .add();
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());
        assertActivePowerEquals(Double.NaN, gen.getTerminal());
        assertReactivePowerEquals(Double.NaN, gen.getTerminal());
    }

    @Test
    void testGeneratorReactiveLimits() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getGenerator("GEN").newMinMaxReactiveLimits().setMinQ(0).setMaxQ(150).add();
        network.getVoltageLevel("VLGEN").newGenerator().setId("GEN1")
                .setBus("NGEN").setConnectableBus("NGEN")
                .setMinP(-9999.99D).setMaxP(9999.99D)
                .setVoltageRegulatorOn(true).setTargetV(24.5D)
                .setTargetP(607.0D).setTargetQ(301.0D).add();
        // GEN1 reactive limits are not plausible => fallback into split Q equally
        network.getGenerator("GEN1").newMinMaxReactiveLimits().setMinQ(-10000).setMaxQ(10000).add();
        LoadFlowParameters parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(false)
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        loadFlowRunner.run(network, parameters);
        network.getGenerators().forEach(gen -> {
            if (gen.getReactiveLimits() instanceof MinMaxReactiveLimits) {
                assertTrue(-gen.getTerminal().getQ() <= ((MinMaxReactiveLimits) gen.getReactiveLimits()).getMaxQ());
                assertTrue(-gen.getTerminal().getQ() >= ((MinMaxReactiveLimits) gen.getReactiveLimits()).getMinQ());
            }
        });
        assertEquals(-150, network.getGenerator("GEN").getTerminal().getQ(), 0.01);
        assertEquals(-153.86, network.getGenerator("GEN1").getTerminal().getQ(), 0.01);
    }

    @Test
    void testGeneratorsConnectedToSameBusNotControllingSameBus() throws IOException {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getVoltageLevel("VLGEN").newGenerator()
                .setId("GEN2")
                .setConnectableBus("NGEN")
                .setBus("NGEN")
                .setMinP(0)
                .setMaxP(100)
                .setTargetP(1)
                .setVoltageRegulatorOn(true)
                .setTargetV(148)
                .setRegulatingTerminal(network.getLoad("LOAD").getTerminal())
                .add();
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        loadFlowRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), parameters, reportNode);
        assertVoltageEquals(24.5, network.getBusBreakerView().getBus("NGEN"));
        assertVoltageEquals(147.57, network.getBusBreakerView().getBus("NLOAD"));
        LoadFlowAssert.assertReportEquals("/generatorsConnectedToSameBusNotControllingSameBusReport.txt", reportNode);
    }

    @Test
    void maxOuterLoopIterationTest() throws IOException {
        ReportNode report = ReportNode.newRootReportNode()
                .withMessageTemplate("test", "test")
                .build();
        gen.setTargetP(1000);
        parameters.setDistributedSlack(true);
        parametersExt.setMaxOuterLoopIterations(1);

        LoadFlowResult result = loadFlowRunner.run(network,
                network.getVariantManager().getWorkingVariantId(),
                LocalComputationManager.getDefault(),
                parameters,
                report
                );

        assertFalse(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
        assertEquals("Reached outer loop max iterations limit. Last outer loop name: DistributedSlack", result.getComponentResults().get(0).getStatusText());

        String expected = """
                + test
                   + Load flow on network 'sim1'
                      + Network CC0 SC0
                         + Network info
                            Network has 4 buses and 4 branches
                            Network balance: active generation=1000.0 MW, active load=600.0 MW, reactive generation=0.0 MVar, reactive load=200.0 MVar
                            Angle reference bus: VLGEN_0
                            Slack bus: VLGEN_0
                         + Outer loop DistributedSlack
                            + Outer loop iteration 1
                               Slack bus active power (-394.4445228221647 MW) distributed in 1 distribution iteration(s)
                            Outer loop unsuccessful with status: UNSTABLE
                         Maximum number of outerloop iterations reached: 1
                         AC load flow completed with error (solverStatus=CONVERGED, outerloopStatus=UNSTABLE)
                """;

        assertReportEquals(new ByteArrayInputStream(expected.getBytes()), report);
    }

    @Test
    void testWriteReadSlackBus() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), "newVariant");
        LoadFlowParameters parameters = new LoadFlowParameters().setWriteSlackBus(true);
        OpenLoadFlowParameters openLoadFlowParameters =
                OpenLoadFlowParameters.create(parameters).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        LoadFlowResult result = LoadFlow.run(network, parameters);
        assertTrue(result.isFullyConverged());
        SlackTerminal slackTerminal = network.getVoltageLevel("VLGEN").getExtension(SlackTerminal.class);
        assertNotNull(slackTerminal);
        assertNotNull(slackTerminal.getTerminal());
        openLoadFlowParameters.setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        LoadFlowResult result2 = LoadFlow.run(network, "newVariant", LocalComputationManager.getDefault(), parameters);
        assertTrue(result2.isFullyConverged());
        assertEquals("VLHV1_0", result2.getComponentResults().get(0).getSlackBusResults().get(0).getId());
        network.getVariantManager().setWorkingVariant("newVariant");
        assertNull(slackTerminal.getTerminal());
        LoadFlowResult result3 = LoadFlow.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), parameters);
        assertTrue(result3.isFullyConverged());
        assertEquals("VLGEN_0", result3.getComponentResults().get(0).getSlackBusResults().get(0).getId());
    }
}
