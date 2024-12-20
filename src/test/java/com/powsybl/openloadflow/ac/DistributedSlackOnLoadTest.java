/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonStoppingCriteriaType;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.ReferenceBusSelectionMode;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowResultBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
class DistributedSlackOnLoadTest {

    private Network network;
    private Load l1;
    private Load l2;
    private Load l3;
    private Load l4;
    private Load l5;
    private Load l6;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    public static final double DELTA_MISMATCH = 1E-4d;

    @BeforeEach
    void setUp() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        l1 = network.getLoad("l1");
        l2 = network.getLoad("l2");
        l3 = network.getLoad("l3");
        l4 = network.getLoad("l4");
        l5 = network.getLoad("l5");
        l6 = network.getLoad("l6");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setDistributedSlack(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(35.294, l1.getTerminal());
        assertActivePowerEquals(70.588, l2.getTerminal());
        assertActivePowerEquals(58.824, l3.getTerminal());
        assertActivePowerEquals(164.705, l4.getTerminal());
        assertActivePowerEquals(11.765, l5.getTerminal());
        assertActivePowerEquals(-41.176, l6.getTerminal());
        LoadFlowResult loadFlowResultExpected = new LoadFlowResultBuilder(true)
                .addMetrics("3", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "b4_vl_0", 1.6895598253796607E-7)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected, result);
    }

    @Test
    void testWithLoadDetail() {
        l2.newExtension(LoadDetailAdder.class)
                .withVariableActivePower(40)
                .withFixedActivePower(20)
                .add();
        l6.newExtension(LoadDetailAdder.class)
                .withVariableActivePower(-25)
                .withFixedActivePower(-25)
                .add();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(30.0, l1.getTerminal());
        assertActivePowerEquals(96.923, l2.getTerminal());
        assertActivePowerEquals(50.0, l3.getTerminal());
        assertActivePowerEquals(140, l4.getTerminal());
        assertActivePowerEquals(10.0, l5.getTerminal());
        assertActivePowerEquals(-26.923, l6.getTerminal());
        LoadFlowResult loadFlowResultExpected = new LoadFlowResultBuilder(true)
                .addMetrics("3", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "b4_vl_0", 9.726437433243973E-8)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected, result);
    }

    private void assertPowerFactor(Network network) {
        switch (parameters.getBalanceType()) {
            case PROPORTIONAL_TO_CONFORM_LOAD, PROPORTIONAL_TO_LOAD:
                for (Load load : network.getLoads()) {
                    assertEquals(load.getP0() / load.getQ0(),
                            load.getTerminal().getP() / load.getTerminal().getQ(),
                            DELTA_MISMATCH, "Power factor should be a constant value for load " + load.getId());
                }
                break;
            default:
                break;
        }
    }

    @Test
    void testPowerFactorConstant() {
        // PROPORTIONAL_TO_LOAD and power factor constant for loads
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setLoadPowerFactorConstant(true);
        Network network1 = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        LoadFlowResult loadFlowResult1 = loadFlowRunner.run(network1, parameters);

        assertPowerFactor(network1);
        LoadFlowResult loadFlowResultExpected1 = new LoadFlowResultBuilder(true)
                .addMetrics("4", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 4, "VLHV1_0", 0.026900149770181514)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected1, loadFlowResult1);

        // PROPORTIONAL_TO_CONFORM_LOAD and power factor constant for loads
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        parametersExt.setLoadPowerFactorConstant(true);
        Network network2 = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        // fixedActivePower and FixedReactivePower are unbalanced
        network2.getLoad("LOAD").newExtension(LoadDetailAdder.class)
                .withFixedActivePower(500).withVariableActivePower(100)
                .withFixedReactivePower(150).withVariableReactivePower(50)
                .add();

        //when
        LoadFlowResult loadFlowResult2 = loadFlowRunner.run(network2, parameters);

        // then
        assertPowerFactor(network2);
        LoadFlowResult loadFlowResultExpected2 = new LoadFlowResultBuilder(true).addMetrics("4", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 4, "VLHV1_0", 0.026900149770181514)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected2, loadFlowResult2);
        assertActivePowerEquals(601.440, network1.getLoad("LOAD").getTerminal());

        // PROPORTIONAL_TO_CONFORM_LOAD and power factor constant for loads
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setLoadPowerFactorConstant(true);
        Network network3 = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network3.getVoltageLevel("VLLOAD").newLoad().setId("LOAD1").setP0(-10).setQ0(1).setBus("NLOAD").setConnectableBus("NLOAD").add();

        //when
        LoadFlowResult loadFlowResult3 = loadFlowRunner.run(network3, parameters);

        // then
        assertPowerFactor(network3);
        LoadFlowResult loadFlowResultExpected3 = new LoadFlowResultBuilder(true).addMetrics("5", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "VLHV1_0", 0.2263232679029059)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected3, loadFlowResult3);
        assertActivePowerEquals(611.405, network3.getLoad("LOAD").getTerminal());
        assertActivePowerEquals(-9.809, network3.getLoad("LOAD1").getTerminal());
    }

    @Test
    void testNetworkWithoutConformingLoad() {
        parameters
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        parametersExt
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS);
        ReportNode reportNode = ReportNode.newRootReportNode().withMessageTemplate("test", "test").build();
        LoadFlowResult result = loadFlowRunner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), parameters, reportNode);
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, componentResult.getStatus());
        assertEquals(-60., componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-6);
        assertReportContains("Failed to distribute slack bus active power mismatch, [-+]?\\d*\\.\\d* MW remains", reportNode);

        parametersExt
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL);
        reportNode = ReportNode.newRootReportNode().withMessageTemplate("test", "test").build();
        result = loadFlowRunner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), parameters, reportNode);
        componentResult = result.getComponentResults().get(0);
        assertFalse(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, componentResult.getStatus());
        assertEquals(-60., componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-6);
        assertReportContains("Failed to distribute slack bus active power mismatch, [-+]?\\d*\\.\\d* MW remains", reportNode);

        parametersExt
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.THROW);
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));

        parametersExt
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR)
                .setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
        ReferencePriority.set(network.getGenerator("g1"), 1);
        reportNode = ReportNode.newRootReportNode().withMessageTemplate("test", "test").build();
        result = loadFlowRunner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), parameters, reportNode);
        componentResult = result.getComponentResults().get(0);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, componentResult.getStatus());
        assertEquals(-60., componentResult.getDistributedActivePower(), 1e-6);
        assertActivePowerEquals(-40., network.getGenerator("g1").getTerminal());
        assertReportContains("Slack bus active power \\([-+]?\\d*\\.\\d* MW\\) distributed in 1 distribution iteration\\(s\\)", reportNode);
    }

    @Test
    void testNetworkWithPqPvTypeSwitch() {
        // network has no conforming load, everything goes to reference generator
        network = DistributedSlackNetworkFactory.createWithLossesAndPvPqTypeSwitch();
        parameters
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        parametersExt
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR)
                .setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
        ReferencePriority.set(network.getGenerator("g1"), 1);
        var result = loadFlowRunner.run(network, parameters);
        var componentResult = result.getComponentResults().get(0);
        assertTrue(result.isFullyConverged());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, componentResult.getStatus());
        assertEquals(120.193, componentResult.getDistributedActivePower(), 1e-3);
        assertActivePowerEquals(-220.193, network.getGenerator("g1").getTerminal());
    }

    @Test
    void testPowerFactorConstant2() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads2();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
        parametersExt.setLoadPowerFactorConstant(true).setNewtonRaphsonConvEpsPerEq(1e-6);
        network.getLoad("l4").newExtension(LoadDetailAdder.class)
                .withVariableActivePower(100)
                .withFixedActivePower(0)
                .add();
        network.getLoad("l5").newExtension(LoadDetailAdder.class)
                .withVariableActivePower(200)
                .withFixedActivePower(100)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertBusBalance(network, "b4", 10E-6, 10E-6);
        assertPowerFactor(network);
    }

    @Test
    void testPowerFactorConstant3() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads2();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setLoadPowerFactorConstant(true);
        network.getGenerator("g1").setTargetP(-208);
        l4 = network.getLoad("l4");
        l4.setP0(0.0).setQ0(-50);
        l4.newExtension(LoadDetailAdder.class)
                .withFixedActivePower(0)
                .withFixedReactivePower(0)
                .withVariableActivePower(0)
                .withVariableReactivePower(-50)
                .add();
        l5 = network.getLoad("l5");
        l5.setP0(-10.0).setQ0(0.0);
        l5.newExtension(LoadDetailAdder.class)
                .withFixedActivePower(-10)
                .withFixedReactivePower(0)
                .withVariableActivePower(0)
                .withVariableReactivePower(0.0)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertBusBalance(network, "b4", 10E-3, 10E-3);
        assertPowerFactor(network);
    }

    @Test
    void testPowerFactorConstant4() {
        network = DistributedSlackNetworkFactory.createNetworkWithLoads2();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parametersExt.setLoadPowerFactorConstant(true)
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA)
                .setMaxActivePowerMismatch(1e-2)
                .setMaxReactivePowerMismatch(1e-2);
        // network has 300 MW generation, we set 400MW total P0 load
        l4 = network.getLoad("l4");
        l4.setP0(0.0).setQ0(50.0); // 0MW -> 0% participation factor
        l5 = network.getLoad("l5");
        l5.setP0(400.0).setQ0(50.0); // only non-zero load -> 100% participation factor

        // test with l4 being reactive only load
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertBusBalance(network, "b4", parametersExt.getMaxActivePowerMismatch(), parametersExt.getMaxReactivePowerMismatch());
        assertPowerFactor(network);
        assertActivePowerEquals(0.0, l4.getTerminal());
        assertReactivePowerEquals(50.0, l4.getTerminal());
        assertActivePowerEquals(300.0, l5.getTerminal());
        assertReactivePowerEquals(37.5, l5.getTerminal());

        // test also with l4 being zero load
        l4.setP0(0.0).setQ0(0.0);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertBusBalance(network, "b4", parametersExt.getMaxActivePowerMismatch(), parametersExt.getMaxReactivePowerMismatch());
        assertPowerFactor(network);
        assertActivePowerEquals(0.0, l4.getTerminal());
        assertReactivePowerEquals(0.0, l4.getTerminal());
        assertActivePowerEquals(300.0, l5.getTerminal());
        assertReactivePowerEquals(37.5, l5.getTerminal());
    }

    private void assertBusBalance(Network network, String busId, double pTol, double qTol) {
        double sumP = 0.0;
        double sumQ = 0.0;
        for (Terminal t : network.getBusBreakerView().getBus(busId).getConnectedTerminals()) {
            sumP += t.getP();
            sumQ += t.getQ();
        }
        assertEquals(0.0, sumP, pTol);
        assertEquals(0.0, sumQ, qTol);
    }

    @Test
    void testFictitiousLoadBoolean() {
        l1.setFictitious(true);
        l2.setFictitious(true);
        l3.setFictitious(true);
        l4.setFictitious(false);
        l5.setFictitious(true);
        l6.setFictitious(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(l1.getP0(), l1.getTerminal());
        assertActivePowerEquals(l2.getP0(), l2.getTerminal());
        assertActivePowerEquals(l3.getP0(), l3.getTerminal());
        assertActivePowerEquals(l4.getP0() + 60, l4.getTerminal());
        assertActivePowerEquals(l5.getP0(), l5.getTerminal());
        assertActivePowerEquals(l6.getP0(), l6.getTerminal());
        LoadFlowResult loadFlowResultExpected = new LoadFlowResultBuilder(true)
                .addMetrics("3", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "b4_vl_0", 4.0392134081912445E-9)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected, result);
    }

    @Test
    void testFictitiousLoadType() {
        l1.setLoadType(LoadType.AUXILIARY); // 30 MW
        l2.setLoadType(LoadType.UNDEFINED); // 60 MW
        l3.setLoadType(LoadType.FICTITIOUS); // 50 MW
        l4.setLoadType(LoadType.AUXILIARY); // 140 MW
        l5.setLoadType(LoadType.UNDEFINED); // 10 MW
        l6.setLoadType(LoadType.FICTITIOUS); // -50 MW
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(l1.getP0() + 7.5, l1.getTerminal());
        assertActivePowerEquals(l2.getP0() + 15, l2.getTerminal());
        assertActivePowerEquals(l3.getP0(), l3.getTerminal());
        assertActivePowerEquals(l4.getP0() + 35, l4.getTerminal());
        assertActivePowerEquals(l5.getP0() + 2.5, l5.getTerminal());
        assertActivePowerEquals(l6.getP0(), l6.getTerminal());
        LoadFlowResult loadFlowResultExpected = new LoadFlowResultBuilder(true)
                .addMetrics("3", "CONVERGED")
                .addComponentResult(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "b4_vl_0", 4.0392134081912445E-9)
                .build();
        assertLoadFlowResultsEquals(loadFlowResultExpected, result);
    }
}
