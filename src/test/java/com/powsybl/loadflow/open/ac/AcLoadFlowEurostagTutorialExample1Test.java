/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.loadflow.open.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.open.util.LoadFlowAssert;
import com.powsybl.loadflow.open.OpenLoadFlowParameters;
import com.powsybl.loadflow.open.OpenLoadFlowProvider;
import com.powsybl.loadflow.open.SlackBusSelectionMode;
import com.powsybl.loadflow.open.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.math.matrix.DenseMatrixFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Before
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
        parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setDistributedSlack(false);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void baseCaseTest() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals("3", result.getMetrics().get("iterations"));

        LoadFlowAssert.assertVoltageEquals(24.5, genBus);
        LoadFlowAssert.assertAngleEquals(0, genBus);
        LoadFlowAssert.assertVoltageEquals(402.143, bus1);
        LoadFlowAssert.assertAngleEquals(-2.325965, bus1);
        LoadFlowAssert.assertVoltageEquals(389.953, bus2);
        LoadFlowAssert.assertAngleEquals(-5.832329, bus2);
        LoadFlowAssert.assertVoltageEquals(147.578, loadBus);
        LoadFlowAssert.assertAngleEquals(-11.940451, loadBus);
        LoadFlowAssert.assertActivePowerEquals(302.444, line1.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(98.74, line1.getTerminal1());
        LoadFlowAssert.assertActivePowerEquals(-300.434, line1.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(-137.188, line1.getTerminal2());
        LoadFlowAssert.assertActivePowerEquals(302.444, line2.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(98.74, line2.getTerminal1());
        LoadFlowAssert.assertActivePowerEquals(-300.434, line2.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(-137.188, line2.getTerminal2());

        // check pv bus reactive power update
        LoadFlowAssert.assertReactivePowerEquals(225.279, gen.getTerminal());
    }

    @Test
    public void dcLfVoltageInitTest() {
        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        boolean[] stateVectorInitialized = new boolean[1];
        parametersExt.getAdditionalObservers().add(new DefaultAcLoadFlowObserver() {
            @Override
            public void stateVectorInitialized(double[] x) {
                Assert.assertEquals(0, x[1], LoadFlowAssert.DELTA_ANGLE);
                Assert.assertEquals(-0.043833, x[3], LoadFlowAssert.DELTA_ANGLE);
                Assert.assertEquals(-0.112393, x[5], LoadFlowAssert.DELTA_ANGLE);
                Assert.assertEquals(-0.220241, x[7], LoadFlowAssert.DELTA_ANGLE);
                stateVectorInitialized[0] = true;
            }
        });
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals("3", result.getMetrics().get("iterations"));
        assertTrue(stateVectorInitialized[0]);
    }

    @Test
    public void line1Side1DeconnectionTest() {
        line1.getTerminal1().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        LoadFlowAssert.assertVoltageEquals(24.5, genBus);
        LoadFlowAssert.assertAngleEquals(0, genBus);
        LoadFlowAssert.assertVoltageEquals(400.277, bus1);
        LoadFlowAssert.assertAngleEquals(-2.348788, bus1);
        LoadFlowAssert.assertVoltageEquals(374.537, bus2);
        LoadFlowAssert.assertAngleEquals(-9.719157, bus2);
        LoadFlowAssert.assertVoltageEquals(141.103, loadBus);
        LoadFlowAssert.assertAngleEquals(-16.372920, loadBus);
        LoadFlowAssert.assertUndefinedActivePower(line1.getTerminal1());
        LoadFlowAssert.assertUndefinedReactivePower(line1.getTerminal1());
        LoadFlowAssert.assertActivePowerEquals(0.016, line1.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(-54.321, line1.getTerminal2());
        LoadFlowAssert.assertActivePowerEquals(609.544, line2.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(263.412, line2.getTerminal1());
        LoadFlowAssert.assertActivePowerEquals(-600.965, line2.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(-227.04, line2.getTerminal2());
    }

    @Test
    public void line1Side2DeconnectionTest() {
        line1.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        LoadFlowAssert.assertVoltageEquals(24.5, genBus);
        LoadFlowAssert.assertAngleEquals(0, genBus);
        LoadFlowAssert.assertVoltageEquals(400.120, bus1);
        LoadFlowAssert.assertAngleEquals(-2.352669, bus1);
        LoadFlowAssert.assertVoltageEquals(368.797, bus2);
        LoadFlowAssert.assertAngleEquals(-9.773427, bus2);
        LoadFlowAssert.assertVoltageEquals(138.678, loadBus);
        LoadFlowAssert.assertAngleEquals(-16.649943, loadBus);
        LoadFlowAssert.assertActivePowerEquals(0.01812, line1.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(-61.995296, line1.getTerminal1());
        LoadFlowAssert.assertUndefinedActivePower(line1.getTerminal2());
        LoadFlowAssert.assertUndefinedReactivePower(line1.getTerminal2());
        LoadFlowAssert.assertActivePowerEquals(610.417, line2.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(330.862, line2.getTerminal1());
        LoadFlowAssert.assertActivePowerEquals(-600.983, line2.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(-284.230, line2.getTerminal2());
    }

    @Test
    public void line1DeconnectionTest() {
        line1.getTerminal1().disconnect();
        line1.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        LoadFlowAssert.assertVoltageEquals(24.5, genBus);
        LoadFlowAssert.assertAngleEquals(0, genBus);
        LoadFlowAssert.assertVoltageEquals(398.265, bus1);
        LoadFlowAssert.assertAngleEquals(-2.358007, bus1);
        LoadFlowAssert.assertVoltageEquals(366.585, bus2);
        LoadFlowAssert.assertAngleEquals(-9.857221, bus2);
        LoadFlowAssert.assertVoltageEquals(137.742, loadBus);
        LoadFlowAssert.assertAngleEquals(-16.822678, loadBus);
        LoadFlowAssert.assertUndefinedActivePower(line1.getTerminal1());
        LoadFlowAssert.assertUndefinedReactivePower(line1.getTerminal2());
        LoadFlowAssert.assertUndefinedActivePower(line1.getTerminal1());
        LoadFlowAssert.assertUndefinedReactivePower(line1.getTerminal2());
        LoadFlowAssert.assertActivePowerEquals(610.562, line2.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(334.056, line2.getTerminal1());
        LoadFlowAssert.assertActivePowerEquals(-600.996, line2.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(-285.379, line2.getTerminal2());
    }

    @Test
    public void shuntCompensatorTest() {
        loadBus.getVoltageLevel().newShuntCompensator()
                .setId("SC")
                .setBus(loadBus.getId())
                .setConnectableBus(loadBus.getId())
                .setbPerSection(3.25 * Math.pow(10, -3))
                .setMaximumSectionCount(1)
                .setCurrentSectionCount(1)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        LoadFlowAssert.assertVoltageEquals(152.327, loadBus);
        LoadFlowAssert.assertReactivePowerEquals(52.987, line1.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(-95.063, line1.getTerminal2());
        LoadFlowAssert.assertReactivePowerEquals(52.987, line2.getTerminal1());
        LoadFlowAssert.assertReactivePowerEquals(-95.063, line2.getTerminal2());
    }
}
