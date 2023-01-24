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
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.CompletionException;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.*;

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
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setThrowsExceptionInCaseOfSlackDistributionFailure(true);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(1, result.getComponentResults().size());
        assertEquals(120, result.getComponentResults().get(0).getDistributedActivePower(), LoadFlowAssert.DELTA_POWER);
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
    void testProportionalToGenerationPMaxBalanceType() {
        // decrease g1 max limit power, so that distributed slack algo reach the g1 max
        g1.setMaxP(105);
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertActivePowerEquals(-105, g1.getTerminal());
        assertActivePowerEquals(-249.285, g2.getTerminal());
        assertActivePowerEquals(-106.429, g3.getTerminal());
        assertActivePowerEquals(-139.286, g4.getTerminal());
    }

    @Test
    void testProportionalToGenerationParticipationFactorBalanceType() {
        // decrease g1 max limit power, so that distributed slack algo reach the g1 max
        g1.setMaxP(100);

        // set participationFactor
        g2.getExtension(ActivePowerControl.class).setParticipationFactor(3.0);
        g3.getExtension(ActivePowerControl.class).setParticipationFactor(2.0);
        g4.getExtension(ActivePowerControl.class).setParticipationFactor(1.0);

        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_PARTICIPATION_FACTOR);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertActivePowerEquals(-100, g1.getTerminal());
        assertActivePowerEquals(-260, g2.getTerminal());
        assertActivePowerEquals(-130, g3.getTerminal());
        assertActivePowerEquals(-110, g4.getTerminal());
    }

    @Test
    void testProportionalToGenerationRemainingMarginBalanceType() {
        // decrease g1 max limit power, so that distributed slack algo reach the g1 max
        g1.setMaxP(105);

        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_REMAINING_MARGIN);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isOk());
        assertActivePowerEquals(-102.667, g1.getTerminal());
        assertActivePowerEquals(-253.333, g2.getTerminal());
        assertActivePowerEquals(-122.0, g3.getTerminal());
        assertActivePowerEquals(-122.0, g4.getTerminal());
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
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(595.328, network.getGenerator("GEN").getTerminal());
    }

    @Test
    void generatorWithMaxPEqualsToMinP() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").setMaxP(1000);
        network.getGenerator("GEN").setMinP(1000);
        network.getGenerator("GEN").setTargetP(1000);
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters),
                "Failed to distribute slack bus active power mismatch, -393.367476483181 MW remains");
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

    @Test
    void generatorWithTargetPLowerThanMinP() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").setMaxP(1000);
        network.getGenerator("GEN").setMinP(200);
        network.getGenerator("GEN").setTargetP(100);
        network.getVoltageLevel("VLGEN").newGenerator()
                .setId("g1")
                .setBus("NGEN")
                .setConnectableBus("NGEN")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(0)
                .setTargetP(0)
                .setTargetV(24.5)
                .setVoltageRegulatorOn(true)
                .add();
        assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters),
                "Failed to distribute slack bus active power mismatch, 504.9476825313616 MW remains");
    }
}
