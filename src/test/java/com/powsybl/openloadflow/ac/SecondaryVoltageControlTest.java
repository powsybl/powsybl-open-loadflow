/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.ControlUnit;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.ControlZone;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.PilotPoint;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SecondaryVoltageControlTest {

    private Network network;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        // adjust reactive limit to avoid generators be to limit
        var g1 = network.getGenerator("B1-G");
        g1.newMinMaxReactiveLimits()
                .setMinQ(-30)
                .setMaxQ(30)
                .add();
        var g2 = network.getGenerator("B2-G");
        g2.newMinMaxReactiveLimits()
                .setMinQ(-40)
                .setMaxQ(30)
                .add();
        network.getGenerator("B3-G");
        var g6 = network.getGenerator("B6-G");
        g6.newMinMaxReactiveLimits()
                .setMinQ(-60)
                .setMaxQ(24)
                .add();
        var g8 = network.getGenerator("B8-G");
        g8.newMinMaxReactiveLimits()
                .setMinQ(-6)
                .setMaxQ(200)
                .add();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setUseReactiveLimits(false);
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void test() {
        PilotPoint pilotPoint = new PilotPoint(List.of("B10"), 14.4);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint, List.of(new ControlUnit("B1-G"),
                                                                          new ControlUnit("B2-G"),
                                                                          new ControlUnit("B3-G"),
                                                                          new ControlUnit("B6-G"))))
                .add();
        Bus b1 = network.getBusBreakerView().getBus("B1");
        Bus b2 = network.getBusBreakerView().getBus("B2");
        Bus b3 = network.getBusBreakerView().getBus("B3");
        Bus b6 = network.getBusBreakerView().getBus("B6");
        Bus b10 = network.getBusBreakerView().getBus("B10");
        var g1 = network.getGenerator("B1-G");
        var g2 = network.getGenerator("B2-G");
        var g3 = network.getGenerator("B3-G");
        var g6 = network.getGenerator("B6-G");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.262, b10);
        assertVoltageEquals(143.1, b1);
        assertVoltageEquals(141.075, b2);
        assertVoltageEquals(136.35, b3);
        assertVoltageEquals(12.84, b6);
        double q1 = g1.getTerminal().getQ();
        double q2 = g2.getTerminal().getQ();
        double q3 = g3.getTerminal().getQ();
        double q6 = g6.getTerminal().getQ();

        parametersExt.setSecondaryVoltageControl(true);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.4, b10);
        assertVoltageEquals(143.146, b1);
        assertVoltageEquals(141.18, b2);
        assertVoltageEquals(136.552, b3);
        assertVoltageEquals(13.053, b6);
        assertEquals(-2.655, q1 - g1.getTerminal().getQ(), DELTA_POWER);
        assertEquals(-4.007, q2 - g2.getTerminal().getQ(), DELTA_POWER);
        assertEquals(-1.29, q3 - g3.getTerminal().getQ(), DELTA_POWER);
        assertEquals(7.234, q6 - g6.getTerminal().getQ(), DELTA_POWER);

        pilotPoint.setTargetV(14);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.01, b10);
        assertVoltageEquals(143.014, b1);
        assertVoltageEquals(140.882, b2);
        assertVoltageEquals(135.977, b3);
        assertVoltageEquals(12.435, b6);
    }

    @Test
    void multiZonesTest() {
        PilotPoint pilotPoint1 = new PilotPoint(List.of("B4"), 142);
        PilotPoint pilotPoint2 = new PilotPoint(List.of("B10"), 14.5);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint1, List.of(new ControlUnit("B1-G"), new ControlUnit("B2-G"), new ControlUnit("B3-G"))))
                .addControlZone(new ControlZone("z2", pilotPoint2, List.of(new ControlUnit("B6-G"), new ControlUnit("B8-G"))))
                .add();

        Bus b4 = network.getBusBreakerView().getBus("B4");
        Bus b10 = network.getBusBreakerView().getBus("B10");

        parametersExt.setSecondaryVoltageControl(true);
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(9, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(141.901, b4); // < 0.1Kv
        assertVoltageEquals(14.5, b10);
    }
}
