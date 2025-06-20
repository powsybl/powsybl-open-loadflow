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
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
class AreaInterchangeControlTest {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAreaInterchangeControl(true)
                .setSlackBusPMaxMismatch(1e-3)
                .setAreaInterchangePMaxMismatch(1e-1);
    }

    @Test
    void twoAreasWithXnodeTest() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithXNode();
        runLfTwoAreas(network, -40, 40, -30, 2);
        parameters.setDc(true);
        runLfTwoAreas(network, -40, 40, -30, 0);
    }

    @Test
    void twoAreasWithUnpairedDanglingLine() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithDanglingLine();
        double interchangeTarget1 = -60; // area a1 has a boundary that is an unpaired dangling line with P0 = 20MW
        double interchangeTarget2 = 40;
        runLfTwoAreas(network, interchangeTarget1, interchangeTarget2, -10, 4);
        parameters.setDc(true);
        runLfTwoAreas(network, interchangeTarget1, interchangeTarget2, -10, 0);
    }

    @Test
    void zeroImpedanceBoundaryBranchesNetworkConversion() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithDanglingLine();
        network.getLine("l23_A1").setX(0);          // boundary
        network.getDanglingLine("dl1").setX(0);     // boundary
        network.getLine("l12").setX(0);             // not boundary

        LfNetwork lfNetwork = Networks.load(network, new LfNetworkParameters().setAreaInterchangeControl(false)).get(0);
        assertTrue(lfNetwork.getBranchById("dl1").isZeroImpedance(LoadFlowModel.AC));
        assertTrue(lfNetwork.getBranchById("l23_A1").isZeroImpedance(LoadFlowModel.AC));
        assertTrue(lfNetwork.getBranchById("l12").isZeroImpedance(LoadFlowModel.AC));

        lfNetwork = Networks.load(network, new LfNetworkParameters().setAreaInterchangeControl(true)).get(0);
        assertFalse(lfNetwork.getBranchById("dl1").isZeroImpedance(LoadFlowModel.AC));
        assertEquals(LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE, lfNetwork.getBranchById("dl1").getPiModel().getX());
        assertFalse(lfNetwork.getBranchById("l23_A1").isZeroImpedance(LoadFlowModel.AC));
        assertEquals(LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE, lfNetwork.getBranchById("l23_A1").getPiModel().getX());
        assertTrue(lfNetwork.getBranchById("l12").isZeroImpedance(LoadFlowModel.AC));

    }

    @Test
    void twoAreasWithZeroImpedanceBoundaryBranches() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithDanglingLine();
        double interchangeTarget1 = -40;
        double interchangeTarget2 = 20;
        network.getLine("l23_A1").setX(0);
        network.getDanglingLine("dl1").setX(0);
        parametersExt.setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE);
        runLfTwoAreas(network, interchangeTarget1, interchangeTarget2, -10, 2);
        parameters.setDc(true);
        runLfTwoAreas(network, interchangeTarget1, interchangeTarget2, -10, 0);
    }

    @Test
    void twoAreasWithTieLineTest() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTieLine();
        runLfTwoAreas(network, -40, 40, -30, 2);
        parameters.setDc(true);
        runLfTwoAreas(network, -40, 40, -30, 0);
    }

    @Test
    void twoAreasWithUnconsideredTlTest() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithUnconsideredTieLine();
        int expectedIterationCount = 3;
        runLfTwoAreas(network, -40, 40, -35, expectedIterationCount);
        parameters.setDc(true);
        runLfTwoAreas(network, -40, 40, -35, 0);
    }

    static Stream<Arguments> allSlackDistributionFailureBehaviors() {
        return Stream.of(Arguments.of(OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS, -90, -20, -10),
                Arguments.of(OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL, Double.NaN, -30, 0),
                Arguments.of(OpenLoadFlowParameters.SlackDistributionFailureBehavior.DISTRIBUTE_ON_REFERENCE_GENERATOR, Double.NaN, -30, 0),
                Arguments.of(OpenLoadFlowParameters.SlackDistributionFailureBehavior.THROW, Double.NaN, Double.NaN, Double.NaN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allSlackDistributionFailureBehaviors")
    void slackDistributionFailureBehaviorsTest(OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior, double expectedGen1P, double expectedMismatch, double expectedDistributedP) {
        runLfOneAreaSlackDistributionFailure(slackDistributionFailureBehavior, expectedGen1P, expectedMismatch, expectedDistributedP);
        parameters.setDc(true);
        runLfOneAreaSlackDistributionFailure(slackDistributionFailureBehavior, expectedGen1P, expectedMismatch, expectedDistributedP);
    }

    private void runLfOneAreaSlackDistributionFailure(OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior, double expectedGen1P, double expectedMismatch, double expectedDistributedP) {
        parametersExt.setSlackDistributionFailureBehavior(slackDistributionFailureBehavior);
        Network network = MultiAreaNetworkFactory.createOneAreaBase();
        network.getGenerator("g1").setMinP(90); // the generator should go down to 70MW to meet the interchange target

        if (slackDistributionFailureBehavior != OpenLoadFlowParameters.SlackDistributionFailureBehavior.THROW) {
            var result = loadFlowRunner.run(network, parameters);
            var mainComponentResult = result.getComponentResults().get(0);
            assertEquals(expectedGen1P, network.getGenerator("g1").getTerminal().getP(), 1e-3);
            assertEquals(expectedMismatch, mainComponentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
            assertEquals(expectedDistributedP, mainComponentResult.getDistributedActivePower(), 1e-3);
        } else {
            CompletionException thrown = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));
            assertEquals("Failed to distribute interchange active power mismatch", thrown.getCause().getMessage());
        }
    }

    @Test
    void slackBusOnBoundaryBus() {
        // The slack bus is on a boundary bus, and the flow though this boundary bus is considered in the area interchange
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTwoXNodes();
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("bx1_vl_0");
        var result = runLfTwoAreas(network, -15, 15, -30, 6);
        List<LoadFlowResult.SlackBusResult> slackBusResults = result.getComponentResults().get(0).getSlackBusResults();
        assertEquals(1, slackBusResults.size());
        assertEquals("bx1_vl_0", slackBusResults.get(0).getId());

        parameters.setDc(true);
        result = runLfTwoAreas(network, -15, 15, -30, 0);
        slackBusResults = result.getComponentResults().get(0).getSlackBusResults();
        assertEquals(1, slackBusResults.size());
        assertEquals("bx1_vl_0", slackBusResults.get(0).getId());
    }

    @Test
    void slackBusOnIgnoredBoundaryBus() {
        // The slack bus is on a boundary bus, but the flow on this boundary bus is not considered in the area interchange
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTwoXNodes();
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId("bx2_vl_0");
        var result = runLfTwoAreas(network, -15, 15, -30, 6);
        List<LoadFlowResult.SlackBusResult> slackBusResults = result.getComponentResults().get(0).getSlackBusResults();
        assertEquals(1, slackBusResults.size());
        assertEquals("bx2_vl_0", slackBusResults.get(0).getId());

        parameters.setDc(true);
        result = runLfTwoAreas(network, -15, 15, -30, 0);
        slackBusResults = result.getComponentResults().get(0).getSlackBusResults();
        assertEquals(1, slackBusResults.size());
        assertEquals("bx2_vl_0", slackBusResults.get(0).getId());
    }

    @Test
    void networkWithoutAreas() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        parameters.setDistributedSlack(false);
        parametersExt.setAreaInterchangeControl(true);
        var result = loadFlowRunner.run(network, parameters);
        var componentResult = result.getComponentResults().get(0);
        assertEquals(1.998, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);

        parameters.setDc(true);
        result = loadFlowRunner.run(network, parameters);
        componentResult = result.getComponentResults().get(0);
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void halfNetworkWithoutArea() {
        Network network = MultiAreaNetworkFactory.createTwoAreasWithTieLine();
        network.getArea("a2").remove();

        Area area1 = network.getArea("a1");
        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(area1.getInterchangeTarget().orElseThrow(), area1.getInterchange(), 1e-3);
        assertEquals(-30, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(3, componentResult.getIterationCount());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);

        parameters.setDc(true);
        result = loadFlowRunner.run(network, parameters);
        componentResult = result.getComponentResults().get(0);
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
        assertEquals(0, componentResult.getIterationCount());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);

    }

    @Test
    void areaFragmentedBuses() {
        // Network has an area that has buses in two different components but all boundaries are in the same component
        // This Area is considered by area interchange control only in the component where all boundaries are
        Network network = MultiAreaNetworkFactory.createAreaTwoComponents();

        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");

        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(area1.getInterchangeTarget().orElseThrow(), area1.getInterchange(), 1e-3);
        assertEquals(area2.getInterchangeTarget().orElseThrow(), area2.getInterchange(), 1e-3);
        assertEquals(-30, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void areaFragmentedBoundaries() {
        // Network has an area that has buses and boundaries in two different components, this area is ignored by area interchange control
        Network network = MultiAreaNetworkFactory.createAreaTwoComponentsWithBoundaries();
        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");

        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(area1.getInterchangeTarget().orElseThrow(), area1.getInterchange(), 1e-3);
        assertEquals(51.1, area2.getInterchange(), 1e-3); // has been ignored by area interchange control because all boundaries are not in the same component
        assertEquals(-30, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(3, componentResult.getIterationCount());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), 1e-3);
    }

    @Test
    void tenAreasSlackDistribution() {
        Network network = MultiAreaNetworkFactory.createTenAreas();
        parametersExt.setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.Status.FULLY_CONVERGED, result.getStatus());
        assertEquals(0, result.getComponentResults().get(0).getSlackBusResults().get(0).getActivePowerMismatch(), parametersExt.getSlackBusPMaxMismatch());
    }

    private LoadFlowResult runLfTwoAreas(Network network, double interchangeTarget1, double interchangeTarget2, double expectedDistributedP, int expectedIterationCount) {
        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");
        area1.setInterchangeTarget(interchangeTarget1);
        area2.setInterchangeTarget(interchangeTarget2);

        var result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        var componentResult = result.getComponentResults().get(0);
        assertEquals(interchangeTarget1, area1.getInterchange(), parametersExt.getAreaInterchangePMaxMismatch());
        assertEquals(interchangeTarget2, area2.getInterchange(), parametersExt.getAreaInterchangePMaxMismatch());
        assertEquals(0, componentResult.getSlackBusResults().get(0).getActivePowerMismatch(), parametersExt.getSlackBusPMaxMismatch());
        assertEquals(expectedDistributedP, componentResult.getDistributedActivePower(), parametersExt.getSlackBusPMaxMismatch());
        assertEquals(expectedIterationCount, componentResult.getIterationCount());
        return result;
    }

}

