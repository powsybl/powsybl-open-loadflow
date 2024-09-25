/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.outerloop.AreaInterchangeControlOuterloop;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
class AreaInterchangeControlTest {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private static final Logger LOGGER = LoggerFactory.getLogger(AreaInterchangeControlOuterloop.class);

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAreaInterchangeControl(true)
                .setSlackBusPMaxMismatch(1e-3);
    }

    @Test
    void twoAreasWithXnodeTest() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithXNode();
        runLfTwoAreas(network, -40, 40, -30, 2);
    }

    @Test
    void twoAreasWithUnpairedDanglingLine() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithDanglingLine();
        double interchangeTarget1 = -60; // area a1 has a boundary that is an unpaired dangling line with P0 = 20MW
        double interchangeTarget2 = 40;
        runLfTwoAreas(network, interchangeTarget1, interchangeTarget2, -10, 3);
    }

    @Test
    void twoAreasWithTieLineTest() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTieLine();
        runLfTwoAreas(network, -40, 40, -30, 2);
    }

    @Test
    void twoAreasWithUnconsideredTlTest() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithUnconsideredTieLine();
        int expectedIterationCount = 3;
        runLfTwoAreas(network, -40, 40, -35, expectedIterationCount);
    }

    @Test
    void remainingMismatchLeaveOneSlackBus() {
        parametersExt.setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS);
        Network network = MultiAreaNetworkFactory.createOneAreaBase();
        network.getGenerator("g1").setMinP(90); // the generator should go down to 70MW to meet the interchange target
        var result = loadFlowRunner.run(network, parameters);
        var mainComponentResult = result.getComponentResults().get(0);

        assertEquals(-90, network.getGenerator("g1").getTerminal().getP(), 1e-3);
        assertEquals(-10, mainComponentResult.getDistributedActivePower(), 1e-3);
        assertEquals(-20, mainComponentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void remainingMismatchFail() {
        parametersExt.setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL);
        Network network = MultiAreaNetworkFactory.createOneAreaBase();
        network.getGenerator("g1").setMinP(90); // the generator should go down to 70MW to meet the interchange target
        var result = loadFlowRunner.run(network, parameters);
        var mainComponentResult = result.getComponentResults().get(0);

        assertEquals(Double.NaN, network.getGenerator("g1").getTerminal().getP(), 1e-3);
        assertEquals(0, mainComponentResult.getDistributedActivePower(), 1e-3);
        assertEquals(-30, mainComponentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void remainingMismatchDistributeOnReferenceGenerator() {
        parametersExt.setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR);
        Network network = MultiAreaNetworkFactory.createOneAreaBase();
        network.getGenerator("g1").setMinP(90); // the generator should go down to 70MW to meet the interchange target
        var result = loadFlowRunner.run(network, parameters);
        var mainComponentResult = result.getComponentResults().get(0);

        // falls back to FAIL
        assertEquals(Double.NaN, network.getGenerator("g1").getTerminal().getP(), 1e-3);
        assertEquals(0, mainComponentResult.getDistributedActivePower(), 1e-3);
        assertEquals(-30, mainComponentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void remainingMismatchThrow() {
        parametersExt.setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.THROW);
        Network network = MultiAreaNetworkFactory.createOneAreaBase();
        network.getGenerator("g1").setMinP(90); // the generator should go down to 70MW to meet the interchange target
        CompletionException thrown = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));
        assertEquals("Failed to distribute interchange active power mismatch. Remaining mismatches (with iterations): [a1: -20.00 MW (1 it.)]", thrown.getCause().getMessage());
    }

    @Test
    void slackBusOnBoundaryBus() {
        // The slack bus is on a boundary bus, and the flow though this boundary bus is considered in the area interchange
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTwoXNodes();
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("bx1_vl_0");
        var result = runLfTwoAreas(network, -15, 15, -30, 13);
        List<LoadFlowResult.SlackBusResult> slackBusResults = result.getComponentResults().get(0).getSlackBusResults();
        assertEquals(1, slackBusResults.size());
        assertEquals("bx1_vl_0", slackBusResults.get(0).getId());
    }

    @Test
    void slackBusOnIgnoredBoundaryBus() {
        // The slack bus is on a boundary bus, but the flow on this boundary bus is not considered in the area interchange
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTwoXNodes();
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("bx2_vl_0");
        var result = runLfTwoAreas(network, -15, 15, -30, 13);
        List<LoadFlowResult.SlackBusResult> slackBusResults = result.getComponentResults().get(0).getSlackBusResults();
        assertEquals(1, slackBusResults.size());
        assertEquals("bx2_vl_0", slackBusResults.get(0).getId());
    }

    @Test
    void networkWithoutAreas() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        parametersExt.setAreaInterchangeControl(false);
        var result = loadFlowRunner.run(network, parameters);
        var componentResult = result.getComponentResults().get(0);
        assertEquals(1.998, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void halfNetworkWithoutArea() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTieLine();
        network.getArea("a2").remove();

        Area area1 = network.getArea("a1");
        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(area1.getInterchangeTarget().getAsDouble(), area1.getInterchange(), 1e-3);
        assertEquals(-30, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(3, componentResult.getIterationCount());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void fragmentedArea() {
        Network network = MultiAreaNetworkFactory.areaTwoComponents();
        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");

        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(area1.getInterchangeTarget().getAsDouble(), area1.getInterchange(), 1e-3);
        assertEquals(51.1, area2.getInterchange(), 1e-3); // has been ignored by area interchange control beacuse all boundaries are not in the same component
        assertEquals(-30, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(3, componentResult.getIterationCount());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    private LoadFlowResult runLfTwoAreas(Network network, double interchangeTarget1, double interchangeTarget2, double expectedDistributedP, int expectedIterationCount) {
        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");
        area1.setInterchangeTarget(interchangeTarget1);
        area2.setInterchangeTarget(interchangeTarget2);

        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(interchangeTarget1, area1.getInterchange(), 1e-3);
        assertEquals(interchangeTarget2, area2.getInterchange(), 1e-3);
        assertEquals(expectedDistributedP, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(expectedIterationCount, componentResult.getIterationCount());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
        return result;
    }

    @Test
    void testData() {
        Network network = Network.read(Path.of("D:\\1_PROJETS\\CoRNet\\Test-data\\AIC tests\\20220227_0030_FO7_UX0_withIcTargets_oneSc_noUa.xiidm"));

        LoadFlowParameters params
                = new LoadFlowParameters()
                .setTransformerVoltageControlOn(false)
                .setPhaseShifterRegulationOn(false)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
                .setReadSlackBus(false)
                .setDistributedSlack(false)
                .setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        OpenLoadFlowParameters.create(params)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setSlackBusPMaxMismatch(0.1)
                .setMaxOuterLoopIterations(15)
                .setAreaInterchangeControl(true);

//        network.getArea("AT").remove();
//        network.getArea("DE").remove();

        var result = LoadFlow.run(network, params);

        assertTrue(result.isFullyConverged());

        for (Area area : network.getAreas()) {
            LOGGER.info("Area {} interchange target: {} MW, actual interchange: {} MW, diff: {} MW ", area.getId(), area.getInterchangeTarget().getAsDouble(), area.getInterchange(), area.getInterchangeTarget().getAsDouble() - area.getInterchange());
            assertEquals(area.getInterchangeTarget().getAsDouble(), area.getInterchange(), 0.1);
        }
        var componentResult = result.getComponentResults().get(0);
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 0.1);

        int a = 2;
    }
}

