/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
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

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
class AreaInterchangeControlTests {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

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
        assertTrue(thrown.getCause().getMessage().equals("Failed to distribute interchange active power mismatch. Remaining mismatches: [a1: -20.00 MW]"));
    }

    @Test
    void busNoArea1() {
        Network network = MultiAreaNetworkFactory.busNoArea1();
        LfNetworkParameters parameters = new LfNetworkParameters().setAreaInterchangeControl(true);
        Throwable e = assertThrows(PowsyblException.class, () -> Networks.load(network, parameters));
        assertEquals("Bus b3_vl_0 is not in any Area, and is not a boundary bus (connected tu buses that are all in Areas that are different from each other). Area interchange control cannot be performed on this network", e.getMessage());
    }

    @Test
    void busNoArea2() {
        Network network = MultiAreaNetworkFactory.busNoArea2();
        LfNetworkParameters parameters = new LfNetworkParameters().setAreaInterchangeControl(true);
        Throwable e = assertThrows(PowsyblException.class, () -> Networks.load(network, parameters));
        assertEquals("Bus b1_vl_0 is not in any Area, and is not a boundary bus (connected tu buses that are all in Areas that are different from each other). Area interchange control cannot be performed on this network", e.getMessage());
    }

    @Test
    void busNoArea3() {
        Network network = MultiAreaNetworkFactory.busNoArea3();
        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters().setAreaInterchangeControl(true);
        List<LfNetwork> lfNetworks = Networks.load(network, lfNetworkParameters);
        assertEquals(1, lfNetworks.size());
        LfNetwork mainNetwork = lfNetworks.get(0);
        LfArea area1 = mainNetwork.getAreaById("a1");
        LfArea area2 = mainNetwork.getAreaById("a2");
        LfArea area3 = mainNetwork.getAreaById("a3");

        assertEquals(1, area1.getExternalBusesSlackParticipationFactors().size());
        assertEquals(1, area2.getExternalBusesSlackParticipationFactors().size());
        assertEquals(1, area3.getExternalBusesSlackParticipationFactors().size());

        assertEquals(0.5, area1.getExternalBusesSlackParticipationFactors().get(mainNetwork.getBusById("bus_vl_0")), 1e-3);
        assertEquals(0, area2.getExternalBusesSlackParticipationFactors().get(mainNetwork.getBusById("bus_vl_0")), 1e-3);
        assertEquals(0.5, area3.getExternalBusesSlackParticipationFactors().get(mainNetwork.getBusById("bus_vl_0")), 1e-3);
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
    void duplicateArea() {
        Network network = MultiAreaNetworkFactory.threeBuses();
        network.newArea()
                .setId("a1")
                .setName("Area 1")
                .setAreaType("ControlArea")
                .addVoltageLevel(network.getVoltageLevel("b1_vl"))
                .addVoltageLevel(network.getVoltageLevel("b2_vl"))
                .setInterchangeTarget(20)
                .add();
        LfNetworkParameters parameters = new LfNetworkParameters()
                .setAreaInterchangeControl(true).setComputeMainConnectedComponentOnly(false);
        Throwable e = assertThrows(PowsyblException.class, () -> Networks.load(network, parameters));
        assertEquals("Areas with ids [a1] are present in more than one LfNetwork. Load flow computation with area interchange control is not supported in this case.", e.getMessage());
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

}

