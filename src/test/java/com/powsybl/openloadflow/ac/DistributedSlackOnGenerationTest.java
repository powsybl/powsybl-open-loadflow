/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class DistributedSlackOnGenerationTest {

    private Network network;
    private Generator g1;
    private Generator g2;
    private Generator g3;
    private Generator g4;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = DistributedSlackNetworkFactory.create();
        g1 = network.getGenerator("g1");
        g2 = network.getGenerator("g2");
        g3 = network.getGenerator("g3");
        g4 = network.getGenerator("g4");
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setThrowsExceptionInCaseOfSlackDistributionFailure(true);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(-115, g1.getTerminal());
        assertActivePowerEquals(-245, g2.getTerminal());
        assertActivePowerEquals(-105, g3.getTerminal());
        assertActivePowerEquals(-135, g4.getTerminal());
        assertReactivePowerEquals(159.746, g1.getTerminal());
        Line l14 = network.getLine("l14");
        Line l24 = network.getLine("l24");
        Line l34 = network.getLine("l34");
        assertActivePowerEquals(115, l14.getTerminal1());
        assertActivePowerEquals(-115, l14.getTerminal2());
        assertActivePowerEquals(245, l24.getTerminal1());
        assertActivePowerEquals(-245, l24.getTerminal2());
        assertActivePowerEquals(240, l34.getTerminal1());
        assertActivePowerEquals(-240, l34.getTerminal2());
    }

    @Test
    void testProportionalToGenerationPBalanceType() {
        // decrease g1 max limit power, so that distributed slack algo reach the g1 max
        g1.setMaxP(105);
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertActivePowerEquals(-105, g1.getTerminal());
        assertActivePowerEquals(-260.526, g2.getTerminal());
        assertActivePowerEquals(-117.236, g3.getTerminal());
        assertActivePowerEquals(-117.236, g4.getTerminal());
    }

    @Test
    void maxTest() {
        // decrease g1 max limit power, so that distributed slack algo reach the g1 max
        g1.setMaxP(105);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(-105, g1.getTerminal());
        assertActivePowerEquals(-249.285, g2.getTerminal());
        assertActivePowerEquals(-106.428, g3.getTerminal());
        assertActivePowerEquals(-139.285, g4.getTerminal());
    }

    @Test
    void minTest() {
        // increase g1 min limit power and global load so that distributed slack algo reach the g1 min
        g1.setMinP(90);
        network.getLoad("l1").setP0(400);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(-90, g1.getTerminal());
        assertActivePowerEquals(-170, g2.getTerminal());
        assertActivePowerEquals(-80, g3.getTerminal());
        assertActivePowerEquals(-60, g4.getTerminal());
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroParticipatingGeneratorsTest() {
        g1.getExtension(ActivePowerControl.class).setDroop(2);
        g2.getExtension(ActivePowerControl.class).setDroop(-3);
        g3.getExtension(ActivePowerControl.class).setDroop(0);
        g4.getExtension(ActivePowerControl.class).setDroop(0);
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters),
                "No more generator participating to slack distribution");
    }

    @Test
    void notEnoughActivePowerFailureTest() {
        network.getLoad("l1").setP0(1000);
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters),
                "Failed to distribute slack bus active power mismatch");
    }

    @Test
    void generatorWithNegativeTargetP() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").setMaxP(1000);
        network.getGenerator("GEN").setTargetP(-607);
        network.getLoad("LOAD").setP0(-600);
        network.getLoad("LOAD").setQ0(-200);
        LoadFlowResult result = LoadFlow.find("OpenLoadFlow").run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(595.328, network.getGenerator("GEN").getTerminal());
    }

    @Test
    void generatorWithMaxPEqualsToMinP() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").setMaxP(1000);
        network.getGenerator("GEN").setMinP(1000);
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters),
                "Failed to distribute slack bus active power mismatch, -1.4404045651214226 MW remains");
    }

    @Test
    void nonParticipatingBus() {

        //B1 and B2 are located in germany the rest is in france
        Substation b1s = network.getSubstation("b1_s");
        b1s.setCountry(Country.GE);
        Substation b2s = network.getSubstation("b2_s");
        b2s.setCountry(Country.GE);

        //Only substation located in france are used
        parameters.setCountriesToBalance(EnumSet.of(Country.FR));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertActivePowerEquals(-100, g1.getTerminal());
        assertActivePowerEquals(-200, g2.getTerminal());
        assertActivePowerEquals(-150, g3.getTerminal());
        assertActivePowerEquals(-150, g4.getTerminal());
    }
}
