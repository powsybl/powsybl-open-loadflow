package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.powsybl.openloadflow.util.LoadFlowAssert.*;

public class GeneratorRemoteControlPQSwitchTest {

    private Network network;
    private Bus b1;
    private Generator g1;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    /**
     * (401kV)                            (402kv)
     *  b2 ----------------l23-------------b3---l3 (100mW)
     *  |                                   |
     *  t12                                g3
     *  |                               (pMax = 10)
     *  b1
     *  |
     *  g1
     *
     * @return
     */
    @BeforeEach
    void setUp() {
        network = NetworkFactory.findDefault().createNetwork("test", "java");

        Substation s12 = network.newSubstation()
                .setId("s12")
                .add();

        VoltageLevel vl1 = s12.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        VoltageLevel vl2 = s12.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        VoltageLevel vl3 = network.newVoltageLevel()
                .setId("vl3")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        b1 = vl1.getBusBreakerView()
                .newBus()
                .setId("b1")
                .add();

        Bus b2 = vl2.getBusBreakerView()
                .newBus()
                .setId("b2")
                .add();

        Bus b3 = vl3.getBusBreakerView()
                .newBus()
                .setId("b3")
                .add();

        Line l23 = network.newLine()
                .setId("l12")
                .setR(0.1)
                .setX(0.1)
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setBus2(b3.getId())
                .setConnectableBus2(b3.getId())
                .add();

        TwoWindingsTransformer t12 = s12.newTwoWindingsTransformer()
                .setId("t12")
                .setR(0.1)
                .setX(15)
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .add();

        g1 = vl1.newGenerator()
                .setId("g1")
                .setMaxP(500)
                .setMinP(0)
                .setTargetP(200)
                .setBus(b1.getId())
                .setConnectableBus(b1.getId())
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(t12.getTerminal2())
                .setTargetV(401)
                .add();

        Generator g3 = vl3.newGenerator()
                .setId("g3")
                .setMaxP(10)
                .setMinP(0)
                .setTargetP(0)
                .setBus(b3.getId())
                .setConnectableBus(b3.getId())
                .setVoltageRegulatorOn(true)
                .setTargetV(402)
                .add();

        Load l3 = vl3.newLoad()
                .setId("l3")
                .setBus(b3.getId())
                .setConnectableBus(b3.getId())
                .setP0(200)
                .setQ0(0)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(true);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltageRemoteControl(true)
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.FAIL);
    }

    @Test
    void testUnrealisticVoltageDetection() {

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(12.17, b1);
        assertReactivePowerEquals(2553.557, g1.getTerminal());

        // Now with realistic limits
        parametersExt.setMinRealisticVoltage(0.8);
        parametersExt.setMaxRealisticVoltage(1.2);
        result = loadFlowRunner.run(network, parameters);
        assertFalse(result.isFullyConverged());
        assertEquals("Unrealistic state", result.getComponentResults().get(0).getStatusText());

        // Now with reactive limits on g1
        parameters.setUseReactiveLimits(true);
        g1.newMinMaxReactiveLimits().setMinQ(-800).setMaxQ(800).add();
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(800, g1.getTerminal());

        // Now with very large reactive limits on g1
        g1.newMinMaxReactiveLimits().setMinQ(-3000).setMaxQ(6000).add();
        g1.setTargetQ(10);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(-10, g1.getTerminal());  // The group is set to initial targetQ

        // And with a high voltage
        g1.setTargetV(403);
        g1.setTargetQ(10);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(-10, g1.getTerminal());  // The group is set to initial targetQ

    }

}
