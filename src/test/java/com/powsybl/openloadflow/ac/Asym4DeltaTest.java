package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.extensions.iidm.StepWindingConnectionType;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Asym4DeltaTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Test
    void deltaY() {

        Complex zz = new Complex(0.1, 0.01); // 0.0001 , 0.001
        Complex zn = new Complex(0.1, 0.01); // 0.001 , 0.01
        Boolean isLoadBalanced = true;
        WindingConnectionType w1 = WindingConnectionType.DELTA;
        WindingConnectionType w2 = WindingConnectionType.Y_GROUNDED;
        int numDisconnectedPhase = 0;
        StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.STEP_DOWN;

        network = Asym4nodesFeederTest.ieee4Feeder(zz, zn, isLoadBalanced, WindingConnectionType.Y_GROUNDED, w1, w2);

        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setMaxNewtonRaphsonIterations(100)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAsymmetrical(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(12.47, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(12.433526294098483, bus2);
        assertVoltageEquals(2.2804714045919776, bus3);
        assertVoltageEquals(2.0355904463879693, bus4);

        // addition of an extension to have a 3 phase transformer and new load flow:
        Asym4nodesFeederTest.addTfo3PhaseExtension(network, w2, stepWindingConnectionType, numDisconnectedPhase);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(12.47, bus1);
        assertAngleEquals(0., bus1);
        assertVoltageEquals(12.401647497875299, bus2);
        assertVoltageEquals(2.274193122764982, bus3);
        assertVoltageEquals(2.0302309634844096, bus4);
    }

}
