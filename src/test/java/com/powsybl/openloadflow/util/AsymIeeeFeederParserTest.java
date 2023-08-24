package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.util.AsymIeeeFeederParser;
import com.powsybl.openloadflow.network.util.AsymLvFeederParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsymIeeeFeederParserTest {

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);

        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(100)
                .setMaxActivePowerMismatch(0.0001)
                .setMaxReactivePowerMismatch(0.0001)
                .setNewtonRaphsonConvEpsPerEq(0.0001)
                .setMaxVoltageMismatch(0.0001)
                .setMaxSusceptanceMismatch(0.0001)
                .setMaxAngleMismatch(0.0001)
                .setMaxRatioMismatch(0.0001)
                .setAsymmetrical(true);
    }

    @Test
    void test13BussesTest() {

        Network network = AsymIeeeFeederParser.create("/ieeeFeeder13/");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(4.276427533655057, network.getBusBreakerView().getBus("Bus-632"));
    }

    @Test
    void test34BussesTest() {

        Network network = AsymIeeeFeederParser.create("/ieeeFeeder34/");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(26.145, network.getBusBreakerView().getBus("Bus-800"));
        assertVoltageEquals(25.86703194305272, network.getBusBreakerView().getBus("Bus-832"));
    }

    @Test
    void test123BussesTest() {

        Network network = AsymIeeeFeederParser.create("/ieeeFeeder123/");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(4.16, network.getBusBreakerView().getBus("Bus-150"));
        assertVoltageEquals(4.298876625269438, network.getBusBreakerView().getBus("Bus-72"));
    }

    @Disabled
    @Test
    void testLvFeedersTest() {

        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(100)
                .setMaxActivePowerMismatch(0.0000001)
                .setMaxReactivePowerMismatch(0.0000001)
                .setNewtonRaphsonConvEpsPerEq(0.0000001)
                .setMaxVoltageMismatch(0.00001)
                .setMaxSusceptanceMismatch(0.0001)
                .setMaxAngleMismatch(0.0001)
                .setMaxRatioMismatch(0.0001)
                .setAsymmetrical(true);

        Network network = AsymLvFeederParser.create("/lvFeeder/");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
    }

}
