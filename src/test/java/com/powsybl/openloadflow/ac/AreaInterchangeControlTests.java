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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class AreaInterchangeControlTests {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAreaInterchangeControl(true);
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
        runLfTwoAreas(network, interchangeTarget1, interchangeTarget2, -9.997, 2);
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
        assertTrue(thrown.getCause().getMessage().startsWith("Failed to distribute interchange active power mismatch in 1 area(s), largest remaining mismatch is "));
    }

    private LoadFlowResult runLfTwoAreas(Network network, double interchangeTarget1, double interchangeTarget2, double expectedDistributedP, int expectedIterationCount) {
        Area area1 = network.getArea("a1");
        Area area2 = network.getArea("a2");
        area1.setInterchangeTarget(interchangeTarget1);
        area2.setInterchangeTarget(interchangeTarget2);

        var result = loadFlowRunner.run(network, parameters);

        var componentResult = result.getComponentResults().get(0);
        assertEquals(expectedIterationCount, componentResult.getIterationCount());
        assertEquals(expectedDistributedP, componentResult.getDistributedActivePower(), 1e-3);
        assertEquals(interchangeTarget1, area1.getInterchange(), 1e-3);
        assertEquals(interchangeTarget2, area2.getInterchange(), 1e-3);
        return result;
    }

}

