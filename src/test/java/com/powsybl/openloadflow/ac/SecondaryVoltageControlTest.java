/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
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

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SecondaryVoltageControlTest {

    private Network network;

    private Bus b4;
    private Bus b6;
    private Bus b8;
    private Bus b10;
    private Generator g6;
    private Generator g8;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        b4 = network.getBusBreakerView().getBus("B4");
        b6 = network.getBusBreakerView().getBus("B6");
        b8 = network.getBusBreakerView().getBus("B8");
        b10 = network.getBusBreakerView().getBus("B10");
        g6 = network.getGenerator("B6-G");
        g8 = network.getGenerator("B8-G");

        // adjust reactive limit to avoid generators be to limit
        var g1 = network.getGenerator("B1-G");
        g1.newMinMaxReactiveLimits()
                .setMinQ(-30)
                .setMaxQ(30)
                .add();
        var g2 = network.getGenerator("B2-G");
        g2.newMinMaxReactiveLimits()
                .setMinQ(-40)
                .setMaxQ(35)
                .add();
        g6.newMinMaxReactiveLimits()
                .setMinQ(-61)
                .setMaxQ(24)
                .add();
        g8.newMinMaxReactiveLimits()
                .setMinQ(-6)
                .setMaxQ(200)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void testNoReactiveLimits() {
        parameters.setUseReactiveLimits(false);
        PilotPoint pilotPoint = new PilotPoint(List.of("B10"), 14.4);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint, List.of(new ControlUnit("B6-G"),
                                                                          new ControlUnit("B8-G"))))
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.262, b10);
        assertVoltageEquals(12.84, b6);
        assertVoltageEquals(21.8, b8);
        double q6 = g6.getTerminal().getQ();
        double q8 = g8.getTerminal().getQ();

        parametersExt.setSecondaryVoltageControl(true);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.4, b10);
        assertVoltageEquals(13.018, b6);
        assertVoltageEquals(22.001, b8);
        // not so bad... reactive power shift are closed
        assertEquals(5.047, q6 - g6.getTerminal().getQ(), DELTA_POWER);
        assertEquals(4.247, q8 - g8.getTerminal().getQ(), DELTA_POWER);

        pilotPoint.setTargetV(14);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14, b10);
        assertVoltageEquals(12.5, b6);
        assertVoltageEquals(21.418, b8);
        // not so bad... reactive power shift are closed
        assertEquals(-9.085, q6 - g6.getTerminal().getQ(), DELTA_POWER);
        assertEquals(-7.918, q8 - g8.getTerminal().getQ(), DELTA_POWER);
    }

    @Test
    void testReactiveLimits() {
        PilotPoint pilotPoint = new PilotPoint(List.of("B10"), 14);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint, List.of(new ControlUnit("B6-G"),
                                                                          new ControlUnit("B8-G"))))
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.261, b10);
        assertVoltageEquals(12.84, b6);
        assertVoltageEquals(21.8, b8);
        assertReactivePowerEquals(52.054, g6.getTerminal()); // [-61, 24]
        assertReactivePowerEquals(-188.795, g8.getTerminal()); // [-6, 200]

        parametersExt.setSecondaryVoltageControl(true);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());

        assertVoltageEquals(14, b10);
        assertVoltageEquals(12.708, b6);
        assertVoltageEquals(20.6, b8);
        assertReactivePowerEquals(48.699, g6.getTerminal()); // [-61, 24]
        assertReactivePowerEquals(-154.212, g8.getTerminal()); // [-6, 200]
    }

    @Test
    void multiNoReactiveLimitsZonesTest() {
        parameters.setUseReactiveLimits(false);
        PilotPoint pilotPoint1 = new PilotPoint(List.of("B4"), 142);
        PilotPoint pilotPoint2 = new PilotPoint(List.of("B10"), 14.5);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint1, List.of(new ControlUnit("B1-G"), new ControlUnit("B2-G"), new ControlUnit("B3-G"))))
                .addControlZone(new ControlZone("z2", pilotPoint2, List.of(new ControlUnit("B6-G"), new ControlUnit("B8-G"))))
                .add();

        parametersExt.setSecondaryVoltageControl(true);
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(6, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(142, b4);
        assertVoltageEquals(14.537, b10);
    }
}
