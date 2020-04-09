/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowEurostagTutorialExample1Test {

    private Network network;
    private Bus genBus;
    private Bus bus1;
    private Bus bus2;
    private Bus loadBus;
    private Line line1;
    private Line line2;
    private LoadFlow.Runner loadFlowRunner;
    private Generator gen;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    public void setUp() {
        network = EurostagTutorialExample1Factory.create();
        genBus = network.getBusBreakerView().getBus("NGEN");
        bus1 = network.getBusBreakerView().getBus("NHV1");
        bus2 = network.getBusBreakerView().getBus("NHV2");
        loadBus = network.getBusBreakerView().getBus("NLOAD");
        line1 = network.getLine("NHV1_NHV2_1");
        line2 = network.getLine("NHV1_NHV2_2");
        gen = network.getGenerator("GEN");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setNoGeneratorReactiveLimits(true);
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new FirstSlackBusSelector())
                .setDistributedSlack(false);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void baseCaseTest() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals("3", result.getMetrics().get("network_0_iterations"));

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(402.143, bus1);
        assertAngleEquals(-2.325965, bus1);
        assertVoltageEquals(389.953, bus2);
        assertAngleEquals(-5.832329, bus2);
        assertVoltageEquals(147.578, loadBus);
        assertAngleEquals(-11.940451, loadBus);
        assertActivePowerEquals(302.444, line1.getTerminal1());
        assertReactivePowerEquals(98.74, line1.getTerminal1());
        assertActivePowerEquals(-300.434, line1.getTerminal2());
        assertReactivePowerEquals(-137.188, line1.getTerminal2());
        assertActivePowerEquals(302.444, line2.getTerminal1());
        assertReactivePowerEquals(98.74, line2.getTerminal1());
        assertActivePowerEquals(-300.434, line2.getTerminal2());
        assertReactivePowerEquals(-137.188, line2.getTerminal2());

        // check pv bus reactive power update
        assertReactivePowerEquals(-225.279, gen.getTerminal());
    }

    @Test
    public void dcLfVoltageInitTest() {
        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        boolean[] stateVectorInitialized = new boolean[1];
        parametersExt.getAdditionalObservers().add(new DefaultAcLoadFlowObserver() {
            @Override
            public void stateVectorInitialized(double[] x) {
                assertEquals(0, x[1], LoadFlowAssert.DELTA_ANGLE);
                assertEquals(-0.043833, x[3], LoadFlowAssert.DELTA_ANGLE);
                assertEquals(-0.112393, x[5], LoadFlowAssert.DELTA_ANGLE);
                assertEquals(-0.220241, x[7], LoadFlowAssert.DELTA_ANGLE);
                stateVectorInitialized[0] = true;
            }
        });
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals("3", result.getMetrics().get("network_0_iterations"));
        assertTrue(stateVectorInitialized[0]);
    }

    @Test
    public void line1Side1DeconnectionTest() {
        line1.getTerminal1().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(400.277, bus1);
        assertAngleEquals(-2.348788, bus1);
        assertVoltageEquals(374.537, bus2);
        assertAngleEquals(-9.719157, bus2);
        assertVoltageEquals(141.103, loadBus);
        assertAngleEquals(-16.372920, loadBus);
        assertUndefinedActivePower(line1.getTerminal1());
        assertUndefinedReactivePower(line1.getTerminal1());
        assertActivePowerEquals(0.016, line1.getTerminal2());
        assertReactivePowerEquals(-54.321, line1.getTerminal2());
        assertActivePowerEquals(609.544, line2.getTerminal1());
        assertReactivePowerEquals(263.412, line2.getTerminal1());
        assertActivePowerEquals(-600.965, line2.getTerminal2());
        assertReactivePowerEquals(-227.04, line2.getTerminal2());
    }

    @Test
    public void line1Side2DeconnectionTest() {
        line1.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(400.120, bus1);
        assertAngleEquals(-2.352669, bus1);
        assertVoltageEquals(368.797, bus2);
        assertAngleEquals(-9.773427, bus2);
        assertVoltageEquals(138.678, loadBus);
        assertAngleEquals(-16.649943, loadBus);
        assertActivePowerEquals(0.01812, line1.getTerminal1());
        assertReactivePowerEquals(-61.995296, line1.getTerminal1());
        assertUndefinedActivePower(line1.getTerminal2());
        assertUndefinedReactivePower(line1.getTerminal2());
        assertActivePowerEquals(610.417, line2.getTerminal1());
        assertReactivePowerEquals(330.862, line2.getTerminal1());
        assertActivePowerEquals(-600.983, line2.getTerminal2());
        assertReactivePowerEquals(-284.230, line2.getTerminal2());
    }

    @Test
    public void line1DeconnectionTest() {
        line1.getTerminal1().disconnect();
        line1.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(398.265, bus1);
        assertAngleEquals(-2.358007, bus1);
        assertVoltageEquals(366.585, bus2);
        assertAngleEquals(-9.857221, bus2);
        assertVoltageEquals(137.742, loadBus);
        assertAngleEquals(-16.822678, loadBus);
        assertUndefinedActivePower(line1.getTerminal1());
        assertUndefinedReactivePower(line1.getTerminal2());
        assertUndefinedActivePower(line1.getTerminal1());
        assertUndefinedReactivePower(line1.getTerminal2());
        assertActivePowerEquals(610.562, line2.getTerminal1());
        assertReactivePowerEquals(334.056, line2.getTerminal1());
        assertActivePowerEquals(-600.996, line2.getTerminal2());
        assertReactivePowerEquals(-285.379, line2.getTerminal2());
    }

    @Test
    public void shuntCompensatorTest() {
        loadBus.getVoltageLevel().newShuntCompensator()
                .setId("SC")
                .setBus(loadBus.getId())
                .setConnectableBus(loadBus.getId())
                .setCurrentSectionCount(1)
                .newLinearModel()
                    .setbPerSection(3.25 * Math.pow(10, -3))
                    .setMaximumSectionCount(1)
                    .add()
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(152.327, loadBus);
        assertReactivePowerEquals(52.987, line1.getTerminal1());
        assertReactivePowerEquals(-95.063, line1.getTerminal2());
        assertReactivePowerEquals(52.987, line2.getTerminal1());
        assertReactivePowerEquals(-95.063, line2.getTerminal2());
    }
}
