/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
class AcLoadFlowGeneratorTest {
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void testWithCondenser() {
        Network network = FourBusNetworkFactory.createWithCondenser();
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(1.0, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-2.584977, b4);
    }

    @Test
    void testGeneratorDiscardedFromVoltageControl() {
        Network network = FourBusNetworkFactory.createWith2GeneratorsAtBus1();
        Generator g1Bis = network.getGenerator("g1Bis").setTargetP(0.0).setMinP(1.0).setTargetQ(Double.NaN); // must be discarded from voltage control
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        Generator g1 = network.getGenerator("g1");
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(1.0, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-1.452245, b4);
        assertReactivePowerEquals(0.0, g1Bis.getTerminal());
        assertReactivePowerEquals(-0.570, g1.getTerminal());
    }

    @Test
    void testGeneratorForceTargetQInDiagram() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g1 = network.getGenerator("g1");
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        g1.newMinMaxReactiveLimits().setMinQ(-1).setMaxQ(1).add();

        // targetQ > diagram
        g1.setTargetQ(2).setVoltageRegulatorOn(false);
        parametersExt.setForceTargetQInReactiveLimits(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(1.028108, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-0.337487, b4);
        assertReactivePowerEquals(-1, g1.getTerminal());

        // targetQ < diagram
        g1.setTargetQ(-2);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(0.884231, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-1.089083, b4);
        assertReactivePowerEquals(1, g1.getTerminal());
    }

    @Test
    void testGeneratorForceTargetQInCurveDiagram() {
        Network network = FourBusNetworkFactory.createBaseNetwork();
        Generator g1 = network.getGenerator("g1");
        assertEquals(2, g1.getTargetP());
        // disable slack generation on g4 so that only g1 moves
        network.getGenerator("g4").newExtension(ActivePowerControlAdder.class).withParticipate(false).add();
        Load d3 = network.getLoad("d3");
        g1.newReactiveCapabilityCurve()
            .beginPoint().setP(0).setMinQ(-2).setMaxQ(2).endPoint()
            .beginPoint().setP(4).setMinQ(-1).setMaxQ(1).endPoint()
            .add();

        g1.setTargetQ(1.5).setVoltageRegulatorOn(false);
        parametersExt.setForceTargetQInReactiveLimits(true).setSlackBusPMaxMismatch(0.001);

        d3.setP0(1.5);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(-1.5, g1.getTerminal()); // lowered from 2 to 1.5 because slack distribution
        assertReactivePowerEquals(-1.5, g1.getTerminal()); // at targetQ, inside curve

        d3.setP0(2.);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(-2.0, g1.getTerminal()); // at targetP, no slack needed
        assertReactivePowerEquals(-1.5, g1.getTerminal()); // at targetQ and at curve limit

        d3.setP0(3.);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertActivePowerEquals(-3.0, g1.getTerminal()); // increased from 2 to 3 because slack distribution
        assertReactivePowerEquals(-1.25, g1.getTerminal()); // not at targetQ because at curve limit
    }
}
